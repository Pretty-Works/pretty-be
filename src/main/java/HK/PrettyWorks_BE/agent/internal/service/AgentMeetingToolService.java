package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.agent.internal.dto.req.AgentMeetingCreateRequest;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentMeetingDetailResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentMeetingListResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentWriteResults;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingCreateResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDetailResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingListResponse;
import HK.PrettyWorks_BE.project.meeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// meeting.list · meeting.detail. 목록은 훑기용, 본문은 상세 한 건 — 역할을 갈라 둔다.
@Service
@RequiredArgsConstructor
public class AgentMeetingToolService {

    // 목록 상한을 다른 조회(50)보다 낮게 잡는다. 회의록은 항목 하나가 길어(제목·목적·참석자 명단)
    // 50건이면 본문 없이도 컨텍스트를 상당히 먹는다.
    private static final int MAX_MEETING_SIZE = 20;

    private final MeetingService meetingService;

    public AgentMeetingListResponse list(Long userId, Long projectId, String title,
                                         LocalDate from, LocalDate to, int size) {
        int validatedSize = AgentPage.validateSize(size, MAX_MEETING_SIZE);

        // 최근 회의부터. 보조 정렬(id)이 없으면 같은 날짜 회의의 순서가 매번 달라져
        // "그 회의"를 특정하려는 후속 호출이 다른 건을 집는다.
        PageResponse<MeetingListResponse> page = meetingService.getMeetingList(
                projectId, userId, title, null, from, to,
                PageRequest.of(0, validatedSize,
                        Sort.by(Sort.Order.desc("meetingDate"), Sort.Order.desc("id"))));

        List<AgentMeetingListResponse.AgentMeeting> meetings = page.content().stream()
                .map(meeting -> AgentMeetingListResponse.AgentMeeting.builder()
                        .meetingId(meeting.meetingId())
                        .title(meeting.title())
                        .meetingDate(meeting.meetingDate())
                        .location(meeting.location())
                        .purpose(meeting.purpose())
                        .authorName(meeting.authorName())
                        .attendeeNames(meeting.attendeeNames())
                        .build())
                .toList();

        return AgentMeetingListResponse.builder()
                .projectId(projectId)
                .meetings(meetings)
                .totalCount(meetings.size())
                .truncated(page.totalElements() > meetings.size())
                .build();
    }

    // 승인된 회의록을 실제로 저장한다. 검증·멱등은 공개 API와 같은 MeetingService.createMeeting이 담당한다.
    public AgentWriteResults.MeetingCreated create(Long userId, AgentMeetingCreateRequest request,
                                                   String idempotencyKey) {
        Long projectId = request.projectId();
        MeetingCreateResponse created =
                meetingService.createMeeting(projectId, userId, idempotencyKey, request.toDomain());

        // 문서번호는 저장 시점에 서버가 붙인다. 형식("MTG-날짜-id")을 여기서 다시 조립하면
        // 규칙이 두 벌이 되어 서버가 형식을 바꿀 때 조용히 갈라진다. 저장된 값을 읽어 온다.
        MeetingDetailResponse saved =
                meetingService.getMeetingDetail(projectId, created.meetingId(), userId);

        return AgentWriteResults.MeetingCreated.builder()
                .meetingId(saved.meetingId())
                .documentNo(saved.documentNumber())
                .projectId(projectId)
                .title(saved.title())
                .meetingDate(saved.meetingDate())
                .build();
    }

    public AgentMeetingDetailResponse detail(Long userId, Long projectId, Long meetingId) {
        MeetingDetailResponse source = meetingService.getMeetingDetail(projectId, meetingId, userId);

        boolean mine = source.author() != null && userId.equals(source.author().userId());
        boolean attended = source.attendees().stream()
                .anyMatch(attendee -> userId.equals(attendee.userId()));

        return AgentMeetingDetailResponse.builder()
                .meetingId(source.meetingId())
                .projectId(projectId)
                .documentNo(source.documentNumber())
                .title(source.title())
                .meetingDate(source.meetingDate())
                .location(source.location())
                .purpose(source.purpose())
                .content(source.content())
                .followUp(source.followUp())
                .authorId(source.author() == null ? null : source.author().userId())
                .authorName(source.author() == null ? null : source.author().name())
                .isMine(mine)
                // 작성자 또는 참석자면 수정 가능(MEETING_010). 참석자가 고칠 때는 자기 자신을
                // 명단에서 뺄 수 없다는 제약이 따로 있다(MEETING_012).
                .canEdit(mine || attended)
                .attendees(source.attendees().stream()
                        .map(attendee -> AgentMeetingDetailResponse.AgentAttendee.builder()
                                .userId(attendee.userId())
                                .name(attendee.name())
                                .department(attendee.department())
                                .build())
                        .toList())
                .build();
    }
}
