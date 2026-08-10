package HK.PrettyWorks_BE.agent.meetingdraft.application;

import HK.PrettyWorks_BE.agent.meetingdraft.dto.AgentMeetingDraftRequest;
import HK.PrettyWorks_BE.agent.meetingdraft.dto.AgentMeetingDraftResult;
import HK.PrettyWorks_BE.agent.meetingdraft.dto.MeetingDraftResponse;
import HK.PrettyWorks_BE.agent.meetingdraft.gateway.AgentMeetingDraftClient;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.meeting.exception.MeetingErrorCode;
import HK.PrettyWorks_BE.project.member.dto.res.ProjectMemberResponse;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회의 기록(txt)으로 회의록 초안을 만듭니다. 저장은 하지 않습니다.
 *
 * <p>BE의 역할은 게이트입니다. 문장은 BE가 만들지 않습니다 — 권한을 확인하고, 재료(녹취록·참여자
 * 명단·오늘 날짜)를 모아 FastAPI에 넘기고, 돌아온 값이 <b>회의록 작성 화면에 그대로 채워 넣어도
 * 되는 값인지</b>만 검사해서 내려보냅니다.</p>
 *
 * <p>검사가 이 클래스의 핵심입니다. 이 응답은 사용자가 손대지 않고 그대로 저장 API로 보낼 수
 * 있어야 하므로, 저장 단계에서 거부될 값(명단에 없는 참석자, 프로젝트 기간 밖의 날짜, 컬럼 길이를
 * 넘는 문자열)이 폼에 들어가서는 안 됩니다. 사용자가 저장 버튼을 누른 뒤에야 400을 보게 되면
 * 무엇을 고쳐야 하는지 알 수 없습니다.</p>
 *
 * <p>클래스에 {@code @Transactional}을 걸지 않은 것은 의도입니다. FastAPI 호출은 수십 초가
 * 걸릴 수 있어 트랜잭션 밖에서 해야 합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingDraftService {

    // 서버 기준 오늘. 사용자·데이터가 전부 국내라 고정한다(AgentUserToolService와 같은 기준).
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    // 참여자 명단 상한. PageRequests의 상한과 같은 값이라 이보다 크게 요청하면 400이 난다.
    private static final int MAX_PROJECT_MEMBERS = 100;

    // 아래 상한은 전부 회의록 저장 규격에서 그대로 가져온 값이다. 한쪽을 고치면 다른 쪽도 고쳐야 한다.
    // MeetingCreateRequest.attendeeIds @Size(max = 100)
    private static final int MAX_ATTENDEES = 100;
    // meetings.title VARCHAR(200) · location VARCHAR(100) · purpose VARCHAR(500)
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_LOCATION_LENGTH = 100;
    private static final int MAX_PURPOSE_LENGTH = 500;
    // MeetingCreateRequest.content @Size(max = 20_000) · followUp @Size(max = 10_000)
    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final int MAX_FOLLOW_UP_LENGTH = 10_000;

    private final CurrentUserService currentUserService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final AgentMeetingDraftClient draftClient;

    // 같은 사용자가 초안 생성을 동시에 여러 건 돌리는 것을 막는다. 파일을 두 번 고르거나 버튼을
    // 연타하면 그대로 LLM 호출이 두 번 나가는데, 사용자가 쓰는 것은 어차피 한 벌이다.
    //
    // 인스턴스 메모리라 다중 인스턴스에서는 인스턴스당 한 건까지 허용된다. Redis 락까지 끌어오지
    // 않는 이유는 최악의 경우가 "초안을 몇 번 더 만든다"이지 데이터 손상이 아니기 때문이다.
    private final Set<Long> drafting = ConcurrentHashMap.newKeySet();

    /**
     * 업로드한 회의 기록으로 초안을 만들어 돌려줍니다.
     *
     * @param userId    로그인 사용자. 회의록 작성자가 될 사람이라 참석자 후보에서 제외됩니다.
     * @param projectId 회의록을 쓸 프로젝트
     * @param file      회의 기록 txt
     */
    public MeetingDraftResponse draft(Long userId, Long projectId, MultipartFile file) {
        // 재직 검증(USER_003) — 휴직자는 통과, 퇴사자만 차단. 회의록 작성과 같은 기준이다.
        currentUserService.getEmployedUser(userId);

        // 프로젝트 존재를 멤버십보다 먼저 판정해 없는 프로젝트가 403으로 가려지지 않게 한다.
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));
        projectMemberService.validateActiveMember(projectId, userId);

        // 완료·보관 프로젝트에는 회의록을 쓸 수 없다(MEETING_008). 저장하지 못할 초안을 만드느라
        // LLM을 부를 이유가 없으므로 파일을 읽기 전에 끊는다.
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(MeetingErrorCode.PROJECT_CLOSED);
        }

        String transcript = MeetingTranscriptReader.read(file);
        List<ProjectMemberResponse> members = loadMembers(projectId, userId);
        LocalDate today = LocalDate.now(SERVICE_ZONE);

        AgentMeetingDraftResult result = generate(userId, AgentMeetingDraftRequest.builder()
                .transcript(transcript)
                .today(today)
                .projectMembers(members.stream()
                        .map(member -> AgentMeetingDraftRequest.ProjectMember.builder()
                                .userId(member.userId())
                                .name(member.name())
                                // 부서·직책은 코드가 아니라 한글명만 — 녹취록에 적히는 말과 맞춰야
                                // LLM이 발화자를 사람에 붙일 수 있다(공동 규격 §4-2).
                                .department(member.department().getDescription())
                                .position(member.position().getDescription())
                                .build())
                        .toList())
                .build());

        return toResponse(result, project, members, userId, today);
    }

    // ================================= 내부 =================================

    private List<ProjectMemberResponse> loadMembers(Long projectId, Long userId) {
        // 요청자 본인도 명단에 넣는다(excludeSelf=false). 작성자는 회의에서 가장 많이 말하는
        // 사람이라, 명단에서 빼면 LLM이 그 발화를 모르는 사람의 것으로 보고 참석자를 잘못 짚는다.
        // 본인을 참석자에서 빼는 것은 응답을 만들 때 한다.
        List<ProjectMemberResponse> members =
                projectMemberService.getMembers(projectId, userId, null, false, MAX_PROJECT_MEMBERS);

        // 조회 상한에 걸리면 명단 뒷부분이 LLM에게 보이지 않는다. 조용히 넘어가면
        // "그 사람은 왜 참석자로 안 잡히지"를 추적할 수가 없다.
        if (members.size() >= MAX_PROJECT_MEMBERS) {
            log.warn("[회의록 초안] 참여자가 상한만큼 조회돼 일부가 빠졌을 수 있습니다. projectId={} max={}",
                    projectId, MAX_PROJECT_MEMBERS);
        }
        return members;
    }

    // 사용자당 한 건만 통과시킨다. 실패해도 반드시 풀어 준다 —
    // 안 그러면 한 번 실패한 사용자가 재시도할 때마다 429를 받는다.
    private AgentMeetingDraftResult generate(Long userId, AgentMeetingDraftRequest request) {
        if (!drafting.add(userId)) {
            log.debug("[회의록 초안] 이미 생성 중입니다. userId={}", userId);
            throw BaseException.type(AgentErrorCode.DRAFT_IN_PROGRESS);
        }
        try {
            AgentMeetingDraftResult result = draftClient.generate(request);
            log.info("[회의록 초안] 생성 완료. userId={} transcriptLength={}",
                    userId, request.transcript().length());
            return result;
        } finally {
            drafting.remove(userId);
        }
    }

    private MeetingDraftResponse toResponse(AgentMeetingDraftResult result,
                                            ProjectEntity project,
                                            List<ProjectMemberResponse> members,
                                            Long authorId,
                                            LocalDate today) {
        return MeetingDraftResponse.builder()
                .title(clip(result.title(), MAX_TITLE_LENGTH, "title"))
                .meetingDate(resolveMeetingDate(result.meetingDate(), project, today))
                .location(clip(result.location(), MAX_LOCATION_LENGTH, "location"))
                .purpose(clip(result.purpose(), MAX_PURPOSE_LENGTH, "purpose"))
                .content(clip(result.content(), MAX_CONTENT_LENGTH, "content"))
                .followUp(clip(result.followUp(), MAX_FOLLOW_UP_LENGTH, "followUp"))
                .attendeeUserIds(resolveAttendees(result.attendeeUserIds(), members, authorId))
                .build();
    }

    /**
     * 참석자를 프로젝트 참여자 명단과 대조해 걸러냅니다.
     *
     * <p>명단에 없는 id 제거 · 중복 제거는 규격이 정한 것이고, 여기에 <b>작성자 본인 제거</b>가
     * 더해집니다. 회의록은 작성자를 참석자에 넣을 수 없어(MEETING_005), 본인이 낀 채로 폼에
     * 채워지면 사용자가 저장을 눌렀을 때 400이 납니다.</p>
     *
     * <p>LLM이 준 순서를 지킵니다. 명단 순서로 다시 정렬하면 "회의에서 언급된 순서"라는
     * 정보가 사라지는데, 그 순서가 화면에서 더 자연스럽습니다.</p>
     */
    private List<Long> resolveAttendees(List<Long> received,
                                        List<ProjectMemberResponse> members,
                                        Long authorId) {
        if (received.isEmpty()) {
            return List.of();
        }
        Set<Long> candidates = new HashSet<>(members.size());
        for (ProjectMemberResponse member : members) {
            candidates.add(member.userId());
        }

        List<Long> resolved = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long userId : received) {
            if (userId == null || userId.equals(authorId)
                    || !candidates.contains(userId) || !seen.add(userId)) {
                continue;
            }
            resolved.add(userId);
            if (resolved.size() == MAX_ATTENDEES) {
                log.warn("[회의록 초안] 참석자가 상한을 넘어 앞의 {}명만 씁니다.", MAX_ATTENDEES);
                break;
            }
        }
        if (resolved.size() != received.size()) {
            log.debug("[회의록 초안] 참석자를 걸렀습니다. received={} resolved={}",
                    received.size(), resolved.size());
        }
        return List.copyOf(resolved);
    }

    /**
     * 회의 날짜를 화면에 채울 수 있는 값으로 확정합니다. 아니면 null입니다.
     *
     * <p>규격은 {@code yyyy-MM-dd} 형식 검사만 요구하지만, 형식이 맞아도 저장되지 않는 날짜가
     * 있습니다 — 회의 일자는 오늘이거나 과거여야 하고(@PastOrPresent), 프로젝트 기간
     * 안이어야 합니다(MEETING_007). 그런 날짜를 폼에 채워 주면 사용자는 저장 버튼을 누른 뒤에야
     * 거절당합니다. 비워서 직접 고르게 하는 편이 낫습니다.</p>
     */
    private LocalDate resolveMeetingDate(String received, ProjectEntity project, LocalDate today) {
        if (received == null) {
            return null;
        }
        LocalDate meetingDate;
        try {
            // LocalDate.parse는 ISO_LOCAL_DATE(yyyy-MM-dd)만 받는다. "2026-8-5"나 "2026/08/05"는
            // 여기서 걸린다 — 규격이 말하는 형식 검증이 곧 이것이다.
            meetingDate = LocalDate.parse(received.trim());
        } catch (DateTimeParseException malformed) {
            log.warn("[회의록 초안] 회의 날짜 형식이 올바르지 않아 비웁니다.");
            return null;
        }

        if (meetingDate.isAfter(today)) {
            log.info("[회의록 초안] 회의 날짜가 미래라 비웁니다. meetingDate={}", meetingDate);
            return null;
        }
        if (!ProjectPolicy.isWithinPeriod(project, meetingDate)) {
            log.info("[회의록 초안] 회의 날짜가 프로젝트 기간 밖이라 비웁니다. meetingDate={}", meetingDate);
            return null;
        }
        return meetingDate;
    }

    /**
     * 저장 가능한 길이로 자릅니다. 공백뿐이면 null입니다.
     *
     * <p>여기서는 잘라서 돌려줍니다 — 녹취록과 달리(거기서는 거절합니다) 사용자가 화면에서 잘린
     * 결과를 눈으로 보고 고칠 수 있고, 제목이 좀 길다고 초안 전체를 버릴 이유가 없습니다.</p>
     */
    private static String clip(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        log.warn("[회의록 초안] {}이(가) 상한보다 길어 잘랐습니다. length={} max={}",
                field, trimmed.length(), maxLength);
        // 이모지처럼 char 두 개로 된 글자의 가운데를 자르면 깨진 문자 하나가 남는다.
        int end = Character.isHighSurrogate(trimmed.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return trimmed.substring(0, end);
    }
}
