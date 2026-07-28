package HK.PrettyWorks_BE.project.meeting.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
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
import HK.PrettyWorks_BE.user.constant.StatusType;
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

    // 멱등 처리 진입점
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않음
    public MeetingCreateResponse createMeeting(
            Long projectId, Long authorId, String idempotencyKey, MeetingCreateRequest request) {

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

        // 존재하는 프로젝트인지 확인
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 작성자가 이 프로젝트의 참여중 멤버인지 확인
        projectMemberService.validateActiveMember(projectId, authorId);

        // 완료/보관된 프로젝트가 아닌지 확인
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(MeetingErrorCode.PROJECT_CLOSED);
        }

        // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지
        validateMeetingDate(project, request.meetingDate());

        // 참석자 공통 검증
        validateAttendees(projectId, authorId, request.attendeeIds());

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

        // 작성자 + 참석자 저장
        saveAttendees(savedMeeting.getId(), authorId, request.attendeeIds());

        return savedMeeting.getId();
    }

    // 회의록 목록 조회
    @Transactional(readOnly = true)
    public PageResponse<MeetingListResponse> getMeetingList
    (Long projectId, Long userId, String title, String attendeeName, Pageable pageable) {

        projectMemberService.validateActiveMember(projectId, userId);

        Page<MeetingEntity> meetings = meetingRepository.findMeetingSummaries(projectId, title, attendeeName, pageable);

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
                    .build();
        });

        return PageResponse.from(mapped);
    }

    // 회의록 상세 조회
    @Transactional(readOnly = true)
    public MeetingDetailResponse getMeetingDetail
    (Long projectId, Long meetingId, Long userId) {

        projectMemberService.validateActiveMember(projectId, userId);

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

        // 현재 사용자가 이 프로젝트의 활성 멤버인지 검증
        projectMemberService.validateActiveMember(projectId, userId);

        // 회의록 찾기
        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 이 회의록이 그 프로젝트 소속인지 검증
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND);
        }

        // 프로젝트 조회 및 완료/보관된 프로젝트가 아닌지 확인
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(MeetingErrorCode.PROJECT_CLOSED);
        }

        // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지
        validateMeetingDate(project, request.meetingDate());

        // 작성자 또는 참석자만 수정 가능
        boolean isParticipant = meetingAttendeeRepository.existsByMeetingIdAndUserId(meetingId, userId);
        if (!MeetingPolicy.canEdit(meeting, userId, isParticipant)) {
            throw BaseException.type(MeetingErrorCode.NO_PERMISSION);
        }

        // 본인(참석자)이 수정 시 자기 자신을 명단에서 뺄 수 없음
        // (작성자는 항상 WRITER로 유지되므로 예외)
        if (!meeting.getAuthorId().equals(userId)
                && !request.attendeeIds().contains(userId)) {
            throw BaseException.type(MeetingErrorCode.CANNOT_REMOVE_SELF);
        }

        // 참석자 공통 검증
        validateAttendees(projectId, meeting.getAuthorId(), request.attendeeIds());

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

        // 참석자 교체
        meetingAttendeeRepository.deleteByMeetingId(meetingId);
        meetingAttendeeRepository.flush(); // 삭제를 DB에 먼저 반영

        // 작성자/참석자 다시 저장
        saveAttendees(meetingId, meeting.getAuthorId(), request.attendeeIds());

        // 수정된 최신 상세를 반환
        return toDetailResponse(meeting);
    }

    // 회의록 삭제
    @Transactional
    public MeetingDeleteResponse deleteMeeting
    (Long projectId, Long meetingId, Long userId) {

        // 현재 사용자가 이 프로젝트의 활성 멤버인지
        projectMemberService.validateActiveMember(projectId, userId);

        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 프로젝트와 회의록이 일치하지 않을 경우
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_IN_PROJECT);
        }

        // 작성자만 삭제 가능
        if (!MeetingPolicy.canDelete(meeting, userId)) {
            throw BaseException.type(MeetingErrorCode.NO_PERMISSION);
        }

        // soft delete
        meetingRepository.delete(meeting);

        return MeetingDeleteResponse.builder()
                .meetingId(meetingId)
                .build();
    }

    // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지 검증
    private void validateMeetingDate(ProjectEntity project, LocalDate meetingDate) {
        if (meetingDate.isBefore(project.getStartDate())
                || meetingDate.isAfter(project.getTargetDate())) {
            throw BaseException.type(MeetingErrorCode.MEETING_DATE_OUT_OF_RANGE);
        }
    }

    // 참석자 공통 검증
    private void validateAttendees(Long projectId, Long authorId, List<Long> attendeeIds) {
        // 참석자 중복
        if (attendeeIds.size() != new HashSet<>(attendeeIds).size()) {
            throw BaseException.type(MeetingErrorCode.DUPLICATE_ATTENDEE);
        }

        // 작성자는 참석자에 포함 불가
        if (attendeeIds.contains(authorId)) {
            throw BaseException.type(MeetingErrorCode.AUTHOR_IN_ATTENDEES);
        }

        // 참석자 전원을 한 번에 조회
        List<UserEntity> attendees = userRepository.findAllById(attendeeIds);

        // 존재하는 ID인지 검증
        if (attendees.size() != attendeeIds.size()) {
            throw BaseException.type(MeetingErrorCode.ATTENDEE_NOT_FOUND);
        }

        // 재직중 확인
        for (UserEntity attendee : attendees) {
            if (!UserPolicy.isActive(attendee)) {
                throw BaseException.type(UserErrorCode.INACTIVE_USER);
            }
        }

        // 멤버십 확인
        for (Long attendeeId : attendeeIds) {
            projectMemberService.validateActiveMember(projectId, attendeeId);
        }
    }

    // 작성자 + 참석자 저장 공통 로직
    private void saveAttendees(Long meetingId, Long authorId, List<Long> attendeeIds) {
        UserEntity author = currentUserService.getCurrentUser(authorId);

        // 참석자들만 한 번에 조회
        Map<Long, UserEntity> attendeeMap = userRepository.findAllById(attendeeIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        List<MeetingAttendeeEntity> toSave = new ArrayList<>();

        // 작성자(WRITER)
        toSave.add(toAttendee(meetingId, author, MeetingRole.WRITER));

        // 참석자(ATTENDEE)
        for (Long attendeeId : attendeeIds) {
            UserEntity attendee = attendeeMap.get(attendeeId);
            if (attendee == null) throw BaseException.type(MeetingErrorCode.ATTENDEE_NOT_FOUND);
            toSave.add(toAttendee(meetingId, attendee, MeetingRole.ATTENDEE));
        }

        meetingAttendeeRepository.saveAll(toSave);
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