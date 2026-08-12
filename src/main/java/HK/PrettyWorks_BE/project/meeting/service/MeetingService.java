package HK.PrettyWorks_BE.project.meeting.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import HK.PrettyWorks_BE.notification.constant.NotificationTarget;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.notification.event.NotificationPublisher;
import HK.PrettyWorks_BE.project.meeting.constant.MeetingRole;
import HK.PrettyWorks_BE.project.meeting.domain.MeetingAttendeeEntity;
import HK.PrettyWorks_BE.project.meeting.domain.MeetingEntity;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingCreateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingUpdateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingCreateResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDeleteResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDetailResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingListResponse;
import HK.PrettyWorks_BE.project.meeting.exception.MeetingErrorCode;
import HK.PrettyWorks_BE.project.meeting.policy.MeetingPolicy;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingAttendeeRepository;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingRepository;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserService currentUserService;
    private final NotificationPublisher notificationPublisher;

    // 멱등 처리 진입점
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않음
    public MeetingCreateResponse createMeeting(
            Long projectId, Long authorId, String idempotencyKey, MeetingCreateRequest request) {

        // 작성자 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단.
        // 멱등 처리 바깥에 둬서 기존 응답을 재생하는 요청도 현재의 재직 상태를 반드시 확인한다.
        currentUserService.getEmployedUser(authorId);

        String path = "/api/v1/projects/" + projectId + "/meetings";

        // 무엇을 저장할지(creator) + 무엇으로 중복 판정할지(fingerprint).
        Supplier<Long> creator = () -> doCreate(projectId, authorId, request);
        String fingerprint = idempotencyService.fingerprint("POST", path, request);

        // run()이 생성된 회의록 id를 돌려줌 (재요청이면 첫 응답 id 재생)
        Long meetingId = idempotencyService.run(idempotencyKey, "POST " + path, authorId, fingerprint, creator);

        return MeetingCreateResponse.builder()
                .meetingId(meetingId)
                .build();
    }

    // 회의록 작성
    private Long doCreate(Long projectId, Long authorId, MeetingCreateRequest request) {

        // 작성자가 이 프로젝트의 참여중 멤버인지 확인
        projectMemberService.validateActiveMember(projectId, authorId);

        // 존재하는 프로젝트인지 확인
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 완료/보관된 프로젝트가 아닌지 확인
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(MeetingErrorCode.PROJECT_CLOSED);
        }

        // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지
        validateMeetingDate(project, request.meetingDate());

        // 참석자 공통 검증 — 검증하며 읽은 사용자를 그대로 넘겨받아 저장 때 다시 조회하지 않는다
        List<UserEntity> attendees = validateAttendees(projectId, authorId, request.attendeeIds());

        // 회의록을 먼저 저장해서 임시 id를 발급
        String tempDocumentNo = "TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);

        // 회의록 만들어서 저장
        MeetingEntity meeting = MeetingEntity.builder()
                .projectId(projectId)
                .authorId(authorId)
                .documentNo(tempDocumentNo)
                .title(request.title())
                .meetingDate(request.meetingDate())
                .location(request.location())
                .purpose(request.purpose())
                .content(request.content())
                .followUp(request.followUp())
                .recording(request.recording())
                .build();

        MeetingEntity savedMeeting = meetingRepository.save(meeting);

        // 발급받은 id로 최종 문서번호 확정
        savedMeeting.assignDocumentNo("MTG-" + request.meetingDate() + "-" + savedMeeting.getId());

        // 작성자(WRITER) + 참석자(ATTENDEE) 저장
        UserEntity author = userRepository.findById(authorId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.AUTHOR_NOT_FOUND));
        saveAttendees(savedMeeting.getId(), author, attendees);

        // 참석자 전원 + 이 프로젝트를 관리하는 사람(오너·PM)에게 알림.
        // 관리자는 자기가 참석하지 않은 회의도 알아야 한다는 팀 결정이다.
        // 작성자 본인·중복은 NotificationPublisher가 걸러낸다.
        List<Long> recipients = new ArrayList<>(request.attendeeIds());
        recipients.addAll(projectMemberService.getManagerIds(projectId));

        // 회의록 목록이 아니라 그 회의록 상세로 보낸다(게시글과 같은 이유).
        notificationPublisher.publish(NotificationType.MEETING_CREATED,
                recipients, authorId, NotificationTarget.meeting(projectId, savedMeeting.getId()),
                project.getName(), savedMeeting.getTitle());

        return savedMeeting.getId();
    }

    // 회의록 목록 조회
    @Transactional(readOnly = true)
    public PageResponse<MeetingListResponse> getMeetingList
    (Long projectId, Long userId, String title, String attendeeName, Pageable pageable) {
        return getMeetingList(projectId, userId, title, attendeeName, null, null, pageable);
    }

    // 회의 일자 구간까지 좁히는 일반형 조회. "지난 달 회의록"처럼 기간으로 묻는 에이전트 도구(meeting.list)가 쓴다.
    @Transactional(readOnly = true)
    public PageResponse<MeetingListResponse> getMeetingList
    (Long projectId, Long userId, String title, String attendeeName,
     LocalDate from, LocalDate to, Pageable pageable) {

        projectMemberService.validateAccess(projectId, userId);

        Page<MeetingEntity> meetings =
                meetingRepository.findMeetingSummaries(projectId, title, attendeeName, from, to, pageable);

        // 이 페이지의 회의 id들을 모아서 참석자를 한 번에 조회 후 meetingId로 그룹핑
        List<Long> meetingIds = meetings.getContent().stream()
                .map(MeetingEntity::getId)
                .toList();

        Map<Long, List<MeetingAttendeeEntity>> attendeesByMeeting =
                meetingAttendeeRepository.findByMeetingIdIn(meetingIds).stream()
                        .collect(Collectors.groupingBy(MeetingAttendeeEntity::getMeetingId));

        Page<MeetingListResponse> mapped = meetings.map(meeting -> {
            List<MeetingAttendeeEntity> attendees =
                    attendeesByMeeting.getOrDefault(meeting.getId(), List.of());

            String authorName = null;
            List<String> attendeeNames = new ArrayList<>();
            for (MeetingAttendeeEntity a : attendees) {
                if (a.getRole() == MeetingRole.WRITER) {
                    authorName = a.getAttendeeName();
                } else {
                    attendeeNames.add(a.getAttendeeName());
                }
            }

            return MeetingListResponse.builder()
                    .meetingId(meeting.getId())
                    .title(meeting.getTitle())
                    .authorName(authorName)
                    .attendeeNames(attendeeNames)
                    .meetingDate(meeting.getMeetingDate())
                    .location(meeting.getLocation())
                    .purpose(meeting.getPurpose())
                    .build();
        });

        return PageResponse.from(mapped);
    }

    // 회의록 상세 조회
    @Transactional(readOnly = true)
    public MeetingDetailResponse getMeetingDetail
    (Long projectId, Long meetingId, Long userId) {

        projectMemberService.validateAccess(projectId, userId);

        // 회의록 찾기 (존재하지 않는 회의록은 에러)
        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 이 회의록이 정말 그 프로젝트 소속인지 확인
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND);
        }

        return toDetailResponse(meeting);
    }

    // 회의록 수정
    @Transactional
    public MeetingDetailResponse updateMeeting
    (Long projectId, Long meetingId, Long userId, MeetingUpdateRequest request) {

        // 프로젝트 존재를 멤버십보다 먼저 판정해 없는 프로젝트가 403으로 가려지지 않게 한다.
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));
        projectMemberService.validateActiveMember(projectId, userId);

        // 회의록 찾기
        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 이 회의록이 그 프로젝트 소속인지 검증
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND);
        }

        // 작성자 또는 참석자만 수정 가능
        boolean isParticipant = meetingAttendeeRepository.existsByMeetingIdAndUserId(meetingId, userId);
        if (!MeetingPolicy.canEdit(meeting, userId, isParticipant)) {
            throw BaseException.type(MeetingErrorCode.NO_PERMISSION);
        }

        // 권한 판정 뒤에 둬서 남의 회의록 요청에는 권한 에러가 먼저 나가게 한다.
        currentUserService.getEmployedUser(userId);

        // 완료/보관된 프로젝트가 아닌지 확인
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(MeetingErrorCode.PROJECT_CLOSED);
        }

        // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지
        validateMeetingDate(project, request.meetingDate());

        // 본인(참석자)이 수정 시 자기 자신을 명단에서 뺄 수 없음
        // (작성자는 명단 관리 권한이 있고 항상 WRITER로 유지되므로 예외)
        if (!MeetingPolicy.canManageAttendees(meeting, userId)
                && !request.attendeeIds().contains(userId)) {
            throw BaseException.type(MeetingErrorCode.CANNOT_REMOVE_SELF);
        }

        // 참석자 공통 검증 — 검증하며 읽은 사용자를 그대로 넘겨받아 저장 때 다시 조회하지 않는다
        List<UserEntity> attendees = validateAttendees(projectId, meeting.getAuthorId(), request.attendeeIds());

        // 회의록 내용 수정
        meeting.update(
                request.title(),
                request.meetingDate(),
                request.location(),
                request.purpose(),
                request.content(),
                request.followUp(),
                request.recording()
        );

        // 참석자 명단 동기화 (바뀐 사람만 반영)
        syncAttendees(meetingId, attendees);

        // 수정 알림은 발행하지 않는다 — 오타 하나만 고쳐도 참석자 전원에게 알림이 가 팀 결정으로 폐기했다.
        // NotificationType에서 상수도 함께 지웠으니 되살리지 말 것(사유는 그쪽 주석 참고).

        // 수정된 최신 상세를 반환
        return toDetailResponse(meeting);
    }

    // 회의록 삭제
    @Transactional
    public MeetingDeleteResponse deleteMeeting
    (Long projectId, Long meetingId, Long userId) {

        // 현재 사용자가 이 프로젝트의 활성 멤버인지
        projectMemberService.validateAccess(projectId, userId);

        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 프로젝트와 회의록이 일치하지 않을 경우
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND);
        }

        // 작성자만 삭제 가능
        if (!MeetingPolicy.canDelete(meeting, userId)) {
            throw BaseException.type(MeetingErrorCode.NO_PERMISSION);
        }

        // 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단
        currentUserService.getEmployedUser(userId);

        // soft delete
        meetingRepository.delete(meeting);

        return MeetingDeleteResponse.builder()
                .meetingId(meetingId)
                .build();
    }

    // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지 검증.
    // 기간 판정은 ProjectPolicy가 소유한다 — 할 일·지출·마일스톤과 같은 규칙(양끝 포함)을 써야
    // 경계 규칙이 바뀔 때 회의록만 남는 일이 없다.
    private void validateMeetingDate(ProjectEntity project, LocalDate meetingDate) {
        if (!ProjectPolicy.isWithinPeriod(project, meetingDate)) {
            throw BaseException.type(MeetingErrorCode.MEETING_DATE_OUT_OF_RANGE);
        }
    }

    // 참석자 공통 검증. 검증하며 읽은 UserEntity를 반환해 호출자가 저장에 그대로 쓴다
    // (여기서 조회하고 저장 쪽에서 또 조회하면 같은 사용자를 두 번 읽게 된다)
    private List<UserEntity> validateAttendees(Long projectId, Long authorId, List<Long> attendeeIds) {
        // 참석자 중복
        if (attendeeIds.size() != new HashSet<>(attendeeIds).size()) {
            throw BaseException.type(MeetingErrorCode.DUPLICATE_ATTENDEE);
        }

        // 작성자는 참석자에 포함 불가
        if (attendeeIds.contains(authorId)) {
            throw BaseException.type(MeetingErrorCode.AUTHOR_IN_ATTENDEES);
        }

        // 참석자 전원을 한 번에 조회
        Map<Long, UserEntity> userMap = userRepository.findAllById(attendeeIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        // 존재하는 ID인지 검증
        if (userMap.size() != attendeeIds.size()) {
            throw BaseException.type(MeetingErrorCode.ATTENDEE_NOT_FOUND);
        }

        // 요청 순서대로 되돌린다 — 조회 결과 순서(대개 id 순)를 그대로 쓰면 명단 순서가 요청과 달라진다
        List<UserEntity> attendees = attendeeIds.stream()
                .map(userMap::get)
                .toList();

        // 재직 확인 — 퇴사자(RESIGNED)만 거부하고 휴직(ON_LEAVE)은 허용한다.
        // 회의록은 "그 자리에 누가 있었나"의 기록이라 휴직 전 회의를 뒤늦게 적을 수 있고,
        // 복귀 예정자·인수인계 참석도 실제로 일어난다.
        // 프로젝트 멤버·일정 참가자와 같은 기준(UserPolicy.isEmployed)으로 맞춘다 —
        // 기준이 갈리면 참여자 목록에는 보이는데 참석자로는 거부되는 일이 생긴다.
        for (UserEntity attendee : attendees) {
            if (!UserPolicy.isEmployed(attendee)) {
                throw BaseException.type(UserErrorCode.RESIGNED_USER);
            }
        }

        // 멤버십 확인 — 인원수만큼 조회가 나가지 않게 한 번에 센다
        projectMemberService.validateActiveMembers(projectId, attendeeIds);

        return attendees;
    }

    // 작성자 + 참석자 저장 (생성 시)
    private void saveAttendees(Long meetingId, UserEntity author, List<UserEntity> attendees) {
        List<MeetingAttendeeEntity> toSave = new ArrayList<>();

        // 작성자(WRITER)
        toSave.add(toAttendee(meetingId, author, MeetingRole.WRITER));

        // 참석자(ATTENDEE)
        for (UserEntity attendee : attendees) {
            toSave.add(toAttendee(meetingId, attendee, MeetingRole.ATTENDEE));
        }

        meetingAttendeeRepository.saveAll(toSave);
    }

    // 참석자 명단 동기화(diff) — 요청 명단을 최종 상태로 맞춘다. 일정 도메인 syncParticipants와 같은 패턴.
    // 빠진 사람만 삭제하고 새로 들어온 사람만 추가하며, 그대로인 행은 손대지 않는다.
    //
    // 전원 삭제 후 재삽입하지 않는 이유가 둘 있다.
    //  1) (user_id, meeting_id) UNIQUE와 부딪혀 삭제를 먼저 DB에 밀어넣는 flush()가 필요했다.
    //  2) 참석자 이름·부서는 '작성 시점 스냅샷'인데, 재삽입하면 그 뒤 부서를 옮긴 사람의 기록이
    //     현재 부서로 덮여 쓴다. 오타 하나 고쳤을 뿐인데 과거 회의 기록이 바뀌는 셈이다.
    private void syncAttendees(Long meetingId, List<UserEntity> attendees) {
        Set<Long> requestedIds = attendees.stream()
                .map(UserEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 현재 ATTENDEE 행만 대상 — WRITER 행은 작성자 고정이라 명단 변경과 무관하다
        List<MeetingAttendeeEntity> current =
                meetingAttendeeRepository.findByMeetingIdAndRole(meetingId, MeetingRole.ATTENDEE);

        // 요청에서 빠진 참석자만 삭제
        List<MeetingAttendeeEntity> toDelete = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();
        for (MeetingAttendeeEntity attendee : current) {
            if (requestedIds.contains(attendee.getUserId())) {
                keptIds.add(attendee.getUserId());
            } else {
                toDelete.add(attendee);
            }
        }
        if (!toDelete.isEmpty()) {
            meetingAttendeeRepository.deleteAll(toDelete);
        }

        // 새로 들어온 참석자만 추가
        List<MeetingAttendeeEntity> toInsert = new ArrayList<>();
        for (UserEntity attendee : attendees) {
            if (!keptIds.contains(attendee.getId())) {
                toInsert.add(toAttendee(meetingId, attendee, MeetingRole.ATTENDEE));
            }
        }
        if (!toInsert.isEmpty()) {
            meetingAttendeeRepository.saveAll(toInsert);
        }
    }

    // MeetingAttendeeEntity 조립 공통
    private MeetingAttendeeEntity toAttendee(Long meetingId, UserEntity user, MeetingRole role) {
        return MeetingAttendeeEntity.builder()
                .meetingId(meetingId)
                .userId(user.getId())
                .attendeeName(user.getName())
                .attendeeDepartment(user.getDepartment().name())
                .role(role)
                .build();
    }

    // 회의록 + 참석자를 상세 응답 DTO로 조립
    private MeetingDetailResponse toDetailResponse(MeetingEntity meeting) {
        List<MeetingAttendeeEntity> all = meetingAttendeeRepository.findByMeetingId(meeting.getId());

        MeetingDetailResponse.PersonInfo author = null;
        List<MeetingDetailResponse.PersonInfo> attendees = new ArrayList<>();

        for (MeetingAttendeeEntity a : all) {
            MeetingDetailResponse.PersonInfo person = MeetingDetailResponse.PersonInfo.builder()
                    .userId(a.getUserId())
                    .name(a.getAttendeeName())
                    .department(a.getAttendeeDepartment())
                    .build();

            if (a.getRole() == MeetingRole.WRITER) {
                author = person;
            } else {
                attendees.add(person);
            }
        }

        return MeetingDetailResponse.builder()
                .meetingId(meeting.getId())
                .documentNumber(meeting.getDocumentNo())
                .title(meeting.getTitle())
                .meetingDate(meeting.getMeetingDate())
                .location(meeting.getLocation())
                .author(author)
                .attendees(attendees)
                .recording(meeting.getRecording())
                .purpose(meeting.getPurpose())
                .content(meeting.getContent())
                .followUp(meeting.getFollowUp())
                .build();
    }
}
