package HK.PrettyWorks_BE.project.meeting.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.meeting.domain.MeetingAttendeeEntity;
import HK.PrettyWorks_BE.project.meeting.domain.MeetingEntity;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingCreateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingUpdateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingCreateResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDeleteResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDetailResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingListResponse;
import HK.PrettyWorks_BE.project.meeting.exception.MeetingErrorCode;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingAttendeeRepository;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingRepository;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;

    // 회의록 작성
    @Transactional
    public MeetingCreateResponse createMeeting
    (Long projectId, Long authorId, MeetingCreateRequest request) {

        // 존재하는 프로젝트인지 확인
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.PROJECT_NOT_FOUND));

        // 회의 일자가 프로젝트 기간(startDate ~ targetDate) 안인지
        if (request.meetingDate().isBefore(project.getStartDate())
                || request.meetingDate().isAfter(project.getTargetDate())) {
            throw BaseException.type(MeetingErrorCode.MEETING_DATE_OUT_OF_RANGE);
        }

        // 작성자가 이 프로젝트의 참여중 멤버인지 확인
        projectMemberService.validateActiveMember(projectId, authorId);

        // 참석자 중복 검사
        if (request.attendeeIds().size() != new HashSet<>(request.attendeeIds()).size()) {
            throw BaseException.type(MeetingErrorCode.DUPLICATE_ATTENDEE);
        }

        // 작성자가 참석자 목록에 포함되면 잘못된 요청
        if (request.attendeeIds().contains(authorId)) {
            throw BaseException.type(MeetingErrorCode.AUTHOR_IN_ATTENDEES);
        }

        // 문서 번호 자동 생성
        String documentNo = "MTG-" + request.meetingDate() + "-" + System.currentTimeMillis();

        // 회의록 만들어서 저장
        MeetingEntity meeting = MeetingEntity.builder()
                .projectId(projectId)
                .authorId(authorId)
                .documentNo(documentNo)
                .title(request.title())
                .meetingDate(request.meetingDate())
                .location(request.location())
                .purpose(request.purpose())
                .content(request.content())
                .followUp(request.followUp())
                .recording(request.recording())
                .build();

        MeetingEntity savedMeeting = meetingRepository.save(meeting);

        // 작성자를 users에서 찾아서 WRITER로 저장
        UserEntity author = userRepository.findById(authorId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.AUTHOR_NOT_FOUND));

        MeetingAttendeeEntity writer = MeetingAttendeeEntity.builder()
                .meetingId(savedMeeting.getId())
                .userId(author.getId())
                .attendeeName(author.getName())
                .attendeeDepartment(author.getDepartment().name())
                .role("WRITER")
                .build();

        meetingAttendeeRepository.save(writer);

        // 참석자들 저장
        for (Long attendeeId : request.attendeeIds()) {
            UserEntity attendee = userRepository.findById(attendeeId)
                    .orElseThrow(() -> BaseException.type(MeetingErrorCode.ATTENDEE_NOT_FOUND));

            // 재직 중인 참석자인지 확인
            if (attendee.getStatus() != StatusType.ACTIVE) {
                throw BaseException.type(UserErrorCode.INACTIVE_USER);
            }

            projectMemberService.validateActiveMember(projectId, attendeeId);

            MeetingAttendeeEntity meetingAttendee = MeetingAttendeeEntity.builder()
                    .meetingId(savedMeeting.getId())
                    .userId(attendee.getId())
                    .attendeeName(attendee.getName())
                    .attendeeDepartment(attendee.getDepartment().name())
                    .role("ATTENDEE")
                    .build();

            meetingAttendeeRepository.save(meetingAttendee);

        }

        // 저장된 회의록 id를 응답으로 반환
        return MeetingCreateResponse.builder()
                .meetingId(savedMeeting.getId())
                .build();
    }

    // 회의록 목록 조회
    @Transactional(readOnly = true)
    public PageResponse<MeetingListResponse> getMeetingList(Long projectId, Long userId, String title, String attendeeName, Pageable pageable) {
        projectMemberService.validateActiveMember(projectId, userId);

        Page<MeetingEntity> meetings = meetingRepository.findMeetingSummaries(projectId, title, attendeeName, pageable);

        // 이 회의록의 참여자들을 불러와서
        Page<MeetingListResponse> mapped = meetings.map(meeting -> {
            // 작성자/참석자 나누기
            List<MeetingAttendeeEntity> attendees = meetingAttendeeRepository.findByMeetingId(meeting.getId());

            String authorName = null;
            List<String> attendeeNames = new ArrayList<>();
            for (MeetingAttendeeEntity a : attendees) {
                if (a.getRole().equals("WRITER")) {
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

        // 이 회의록의 참석자들 전부 가져오기
        List<MeetingAttendeeEntity> all = meetingAttendeeRepository.findByMeetingId(meetingId);

        // 작성자(WRITER)와 참석자(ATTENDEE)로 나누기
        MeetingDetailResponse.PersonInfo author = null;
        List<MeetingDetailResponse.PersonInfo> attendees = new ArrayList<>();

        for (MeetingAttendeeEntity a : all) {
            MeetingDetailResponse.PersonInfo person = MeetingDetailResponse.PersonInfo.builder()
                    .userId(a.getUserId())
                    .name(a.getAttendeeName())
                    .department(a.getAttendeeDepartment())
                    .build();

            if (a.getRole().equals("WRITER")) {
                author = person;
            } else {
                attendees.add(person);
            }
        }

        // DTO로 조립해서 반환
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

    // 회의록 수정
    @Transactional
    public MeetingDetailResponse updateMeeting
    (Long projectId, Long meetingId, Long userId, MeetingUpdateRequest request) {

        // 회의록 찾기
        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 이 회의록이 그 프로젝트 소속인지 검증
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND);
        }

        // 작성자 또는 참석자만 수정 가능
        boolean canEdit = meetingAttendeeRepository.existsByMeetingIdAndUserId(meetingId, userId);
        if (!canEdit) {
            throw BaseException.type(MeetingErrorCode.NO_PERMISSION);
        }

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

        // 작성자를 WRITER로 다시 저장
        UserEntity author = userRepository.findById(meeting.getAuthorId())
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.ATTENDEE_NOT_FOUND));

        meetingAttendeeRepository.save(
                MeetingAttendeeEntity.builder()
                        .meetingId(meeting.getId())
                        .userId(author.getId())
                        .attendeeName(author.getName())
                        .attendeeDepartment(author.getDepartment().name())
                        .role("WRITER")
                        .build()
        );

        // 새 참석자들을 ATTENDEE로 저장
        for (Long attendeeId : request.attendeeIds()) {
            UserEntity attendee = userRepository.findById(attendeeId)
                    .orElseThrow(() -> BaseException.type(MeetingErrorCode.ATTENDEE_NOT_FOUND));

            meetingAttendeeRepository.save(
                    MeetingAttendeeEntity.builder()
                            .meetingId(meeting.getId())
                            .userId(attendee.getId())
                            .attendeeName(attendee.getName())
                            .attendeeDepartment(attendee.getDepartment().name())
                            .role("ATTENDEE")
                            .build()
            );
        }

        // 수정된 최신 상세를 반환
        return getMeetingDetail(projectId, meetingId, userId);
    }

    // 회의록 삭제
    @Transactional
    public MeetingDeleteResponse deleteMeeting
    (Long projectId, Long meetingId, Long userId) {

        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BaseException.type(MeetingErrorCode.MEETING_NOT_FOUND));

        // 프로젝트와 회의록이 일치하지 않을 경우
        if (!projectId.equals(meeting.getProjectId())) {
            throw BaseException.type(MeetingErrorCode.MEETING_NOT_IN_PROJECT);
        }

        // 작성자만 삭제 가능
        if (!meeting.getAuthorId().equals(userId)) {
            throw BaseException.type(MeetingErrorCode.NO_PERMISSION);
        }

        // soft delete
        meetingAttendeeRepository.deleteByMeetingId(meetingId);
        meetingRepository.delete(meeting);

        return MeetingDeleteResponse.builder()
                .meetingId(meetingId)
                .build();
    }

}
