package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.client.dto.AgentProjectSummaryRequest;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentMeetingListResponse;
import HK.PrettyWorks_BE.agent.internal.service.AgentMeetingToolService;
import HK.PrettyWorks_BE.agent.internal.service.AgentPostToolService;
import HK.PrettyWorks_BE.agent.internal.service.AgentProjectToolService;
import HK.PrettyWorks_BE.agent.internal.service.AgentTaskToolService;
import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingFollowUpRow;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingRepository;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 요약 재료 수집. FastAPI에 넘길 바디 한 벌을 만드는 것이 전부입니다.
 *
 * <p>재료는 전부 기존 내부 도구 서비스에서 가져옵니다. 요약 전용 조회를 새로 짜면 같은 숫자를
 * 세는 코드가 두 벌이 되어, 에이전트 채팅과 배너가 서로 다른 완료율·집행률을 말하게 됩니다.</p>
 *
 * <p>{@code viewerId}는 "누구의 자격으로 읽는가"입니다. 도구 서비스가 권한 검증에 쓰는 값이며,
 * 돌아오는 데이터 자체는 프로젝트 전체라 누가 읽든 같습니다 — 그래서 요약 한 벌을 저장해
 * 참여자 전원에게 돌려줄 수 있습니다. 배치는 오너를 대신 세웁니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectSummaryMaterialService {

    // 지출은 "제일 큰 것 몇 건"이면 충분하다(규격). 전부 실으면 예산 섹션 한 장 만들자고
    // 수백 건이 프롬프트에 들어간다.
    private static final int TOP_EXPENSE_SIZE = 5;
    private static final int RECENT_POST_SIZE = 20;
    private static final int RECENT_MEETING_SIZE = 10;
    private static final int UPCOMING_MEETING_SIZE = 5;

    // 앞으로 2주. "다음 회의가 잡혀 있나"에 답하는 것이 목적이라 더 멀리 볼 이유가 없다.
    private static final int UPCOMING_MEETING_DAYS = 14;

    private static final String EXPENSE_SORT_AMOUNT_DESC = "AMOUNT_DESC";

    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final AgentProjectToolService projectToolService;
    private final AgentTaskToolService taskToolService;
    private final AgentPostToolService postToolService;
    private final AgentMeetingToolService meetingToolService;
    private final MeetingRepository meetingRepository;
    private final ScheduleService scheduleService;

    @Transactional(readOnly = true)
    public AgentProjectSummaryRequest collect(Long viewerId, Long projectId, LocalDate today) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        return AgentProjectSummaryRequest.builder()
                .projectId(projectId)
                .today(today)
                .project(new AgentProjectSummaryRequest.Project(
                        project.getName(), project.getStartDate(), project.getTargetDate()))
                .milestones(projectToolService.milestones(viewerId, projectId).milestones())
                // weekOffset=0 — 이번 주 할 일 + 지난 주에서 밀려온 건(isCarryOver).
                // 지연 판정의 재료가 바로 그 밀려온 건이라 onlyIncomplete로 거르지 않는다.
                .tasks(taskToolService.list(viewerId, projectId, 0, false).tasks())
                .budget(projectToolService.budget(viewerId, projectId))
                .expenses(projectToolService.expenses(viewerId, projectId, null, null, null,
                        false, EXPENSE_SORT_AMOUNT_DESC, TOP_EXPENSE_SIZE).expenses())
                .posts(postToolService.list(viewerId, projectId, null, null, RECENT_POST_SIZE).posts())
                .meetings(recentMeetings(viewerId, projectId))
                .upcomingMeetings(upcomingMeetings(projectId, today))
                .build();
    }

    // meeting.list 결과에 followUp을 얹는다. 목록 응답에 없는 필드지만
    // "후속 액션 미정리"가 meeting 섹션의 핵심이라 이 재료만은 따로 읽어 온다.
    private List<AgentProjectSummaryRequest.Meeting> recentMeetings(Long viewerId, Long projectId) {
        AgentMeetingListResponse source =
                meetingToolService.list(viewerId, projectId, null, null, null, RECENT_MEETING_SIZE);
        if (source.meetings().isEmpty()) {
            return List.of();
        }

        List<Long> meetingIds = source.meetings().stream()
                .map(AgentMeetingListResponse.AgentMeeting::meetingId)
                .toList();
        Map<Long, String> followUpById = new HashMap<>();
        for (MeetingFollowUpRow row : meetingRepository.findFollowUps(meetingIds)) {
            followUpById.put(row.meetingId(), row.followUp());
        }

        return source.meetings().stream()
                .map(meeting -> AgentProjectSummaryRequest.Meeting.builder()
                        .meetingId(meeting.meetingId())
                        .title(meeting.title())
                        .meetingDate(meeting.meetingDate())
                        .authorName(meeting.authorName())
                        // 미입력은 null이 아니라 빈 문자열로 통일한다. 규격의 예시가 ""이고,
                        // "정리 안 됨"을 판정하는 쪽이 null과 "" 두 가지를 다 처리하게 만들 이유가 없다.
                        .followUp(followUpById.getOrDefault(meeting.meetingId(), ""))
                        .build())
                .toList();
    }

    // 일정은 프로젝트가 아니라 사람에게 붙는다. 그래서 "이 프로젝트의 다음 회의"는
    // 참여중 멤버가 참가자인 MEETING 일정으로 근사한다.
    private List<AgentProjectSummaryRequest.UpcomingMeeting> upcomingMeetings(Long projectId,
                                                                              LocalDate today) {
        List<Long> memberIds = projectMemberService.getActiveMemberIds(projectId);
        if (memberIds.isEmpty()) {
            return List.of();
        }

        ScheduleListResponse schedules = scheduleService.list(
                today, today.plusDays(UPCOMING_MEETING_DAYS), memberIds,
                ScheduleType.MEETING, PageRequest.of(0, UPCOMING_MEETING_SIZE));

        return schedules.schedules().stream()
                .map(item -> new AgentProjectSummaryRequest.UpcomingMeeting(
                        item.title(), item.startAt()))
                .toList();
    }
}
