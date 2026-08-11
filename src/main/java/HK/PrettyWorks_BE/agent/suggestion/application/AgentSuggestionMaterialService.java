package HK.PrettyWorks_BE.agent.suggestion.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionRequest;
import HK.PrettyWorks_BE.agent.tool.calendar.AgentCalendarToolService;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveBalanceResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentScheduleListResponse;
import HK.PrettyWorks_BE.agent.tool.meeting.AgentMeetingToolService;
import HK.PrettyWorks_BE.agent.tool.meeting.dto.AgentMeetingListResponse;
import HK.PrettyWorks_BE.agent.tool.project.AgentProjectToolService;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentProjectSearchResponse;
import HK.PrettyWorks_BE.agent.tool.task.AgentTaskToolService;
import HK.PrettyWorks_BE.agent.tool.task.dto.AgentTaskListResponse.AgentTask;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingFollowUpRow;
import HK.PrettyWorks_BE.project.meeting.repository.MeetingRepository;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 추천 재료 수집. FastAPI에 넘길 바디 한 벌을 만드는 것이 전부입니다.
 *
 * <p>재료는 전부 기존 내부 도구 서비스에서 가져옵니다. 추천 전용 조회를 새로 짜면 같은 것을
 * 세는 코드가 두 벌이 되어, 에이전트 채팅과 추천 칩이 서로 다른 할 일 건수를 말하게 됩니다
 * ({@code ProjectSummaryMaterialService}와 같은 이유).</p>
 *
 * <p><b>재료 하나가 실패해도 나머지로 계속 갑니다.</b> 추천은 화면의 부가 기능이라, 연차 정보를
 * 못 읽었다고 밀린 할 일 추천까지 사라지면 안 됩니다. 그래서 각 재료를 따로 감싸 실패 시 빈 값으로
 * 넘어갑니다 — 후보 하나가 줄 뿐 패널은 그대로 뜹니다.</p>
 *
 * <p>클래스에 {@code @Transactional}을 걸지 않은 것은 의도입니다. 재료끼리 한 시점으로 맞출 이유가
 * 없고(추천 문구는 몇 초 차이에 달라지지 않습니다), 무엇보다 바깥 트랜잭션 안에서 안쪽
 * {@code @Transactional} 호출의 예외를 잡으면 그 트랜잭션이 rollback-only로 표시돼 커밋 시점에
 * {@code UnexpectedRollbackException}이 납니다 — 위의 "하나 실패해도 계속"과 양립할 수 없습니다.
 * 도구·도메인 서비스가 각자 짧은 읽기 트랜잭션을 엽니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSuggestionMaterialService {

    // 진행중 프로젝트 상위 몇 개까지 볼지. project_check 후보는 최대 1개만 나가므로
    // 많이 실어 봐야 프롬프트만 길어진다.
    private static final int MAX_PROJECTS = 5;

    // 회의는 프로젝트마다 한 번씩 조회해야 한다(meeting.list가 projectId를 필수로 받는다).
    // 프로젝트 수 × 이 값이 곧 쿼리 수라, 패널 한 번 여는 비용이 여기서 정해진다.
    private static final int MEETINGS_PER_PROJECT = 5;

    private static final int UPCOMING_MEETING_SIZE = 5;

    // 앞으로 2주. "다음 회의가 잡혀 있나"에 답하는 것이 목적이라 더 멀리 볼 이유가 없다.
    private static final int UPCOMING_MEETING_DAYS = 14;

    // 최근 대화 제목 개수(규격 3~5개). 방금 물어본 것을 또 추천하지 않기 위한 재료다.
    private static final int RECENT_QUESTION_SIZE = 5;

    // 일정 도구는 유형을 문자열로 받는다(ScheduleType.valueOf로 해석한다).
    private static final String SCHEDULE_TYPE_MEETING = "MEETING";

    private final AgentProjectToolService projectToolService;
    private final AgentTaskToolService taskToolService;
    private final AgentMeetingToolService meetingToolService;
    private final AgentCalendarToolService calendarToolService;
    private final MeetingRepository meetingRepository;
    private final AgentConversationRepository conversationRepository;

    public AgentSuggestionRequest collect(Long userId, String screen, LocalDate today) {
        List<AgentProjectSearchResponse.AgentProject> projects =
                quietly(userId, "프로젝트", List::of, () -> projects(userId));

        return AgentSuggestionRequest.builder()
                .today(today)
                .screen(screen)
                .projects(projects)
                .tasks(quietly(userId, "할 일", List::of, () -> tasks(userId)))
                .meetings(quietly(userId, "회의", List::of, () -> meetings(userId, projects)))
                .upcomingMeetings(quietly(userId, "다가오는 회의", List::of,
                        () -> upcomingMeetings(userId, today)))
                .leaveBalance(quietly(userId, "연차", () -> null,
                        () -> calendarToolService.balance(userId, null)))
                .recentQuestions(quietly(userId, "최근 대화", List::of,
                        () -> recentQuestions(userId)))
                .build();
    }

    // ================================= 재료별 수집 =================================

    // 진행중만 본다. 완료·중단된 프로젝트를 "점검해드릴까요?"라고 권할 이유가 없다.
    private List<AgentProjectSearchResponse.AgentProject> projects(Long userId) {
        return projectToolService
                .search(userId, ProjectStatus.ONGOING.name(), null, MAX_PROJECTS)
                .projects();
    }

    /**
     * 이번 주와 다음 주의 미완료 할 일.
     *
     * <p>두 주를 보는 이유는 마감 임박(due_soon) 때문입니다. 이번 주만 보내면 오늘이 목요일일 때
     * "다음 주 월요일 마감"이 재료에 실리지 않아 임박 후보가 구조적으로 나올 수 없습니다.</p>
     *
     * <p>지난 마감(overdue_task)은 따로 조회하지 않습니다 — 지난 주 이전의 미완료 건은
     * task.list가 이번 주 목록에 carry-over로 얹어 주기 때문입니다.</p>
     */
    private List<AgentTask> tasks(Long userId) {
        // taskId로 합친다. 주 범위가 겹치지 않아 실제로 겹칠 일은 없지만,
        // 같은 할 일이 두 번 실리면 LLM이 같은 추천을 두 개 만든다.
        Map<Long, AgentTask> byId = new LinkedHashMap<>();
        for (int weekOffset = 0; weekOffset <= 1; weekOffset++) {
            // projectId=null → 개인 스코프(내게 배정된 할 일). onlyIncomplete=true —
            // 추천 후보는 전부 미완료 건이라 완료된 것을 실어 봐야 프롬프트만 길어진다.
            for (AgentTask task : taskToolService.list(userId, null, weekOffset, true).tasks()) {
                byId.putIfAbsent(task.taskId(), task);
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * 최근 회의에 {@code followUp}을 얹어 돌려줍니다. 후속 액션 미정리 판정의 유일한 재료입니다.
     *
     * <p>{@code meeting.list}가 {@code projectId}를 필수로 받아(내부에서 참여 여부를 검증합니다)
     * 프로젝트마다 한 번씩 부릅니다. 그래서 프로젝트 수에 상한이 필요합니다 — 패널 한 번 여는 데
     * 조회가 수십 번 나가면 안 됩니다.</p>
     */
    private List<AgentSuggestionRequest.Meeting> meetings(
            Long userId, List<AgentProjectSearchResponse.AgentProject> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        List<AgentMeetingListResponse.AgentMeeting> collected = new ArrayList<>();
        for (AgentProjectSearchResponse.AgentProject project : projects) {
            // 한 프로젝트 조회가 실패해도 나머지 프로젝트의 회의는 살린다.
            collected.addAll(quietly(userId, "회의(projectId=" + project.projectId() + ")", List::of,
                    () -> meetingToolService
                            .list(userId, project.projectId(), null, null, null, MEETINGS_PER_PROJECT)
                            .meetings()));
        }
        if (collected.isEmpty()) {
            return List.of();
        }

        // followUp은 목록 응답에 없는 필드라 따로 읽는다. 회의별로 부르지 않고 한 번에 모아 읽는다.
        Map<Long, String> followUpById = new HashMap<>();
        List<Long> meetingIds = collected.stream()
                .map(AgentMeetingListResponse.AgentMeeting::meetingId)
                .toList();
        for (MeetingFollowUpRow row : meetingRepository.findFollowUps(meetingIds)) {
            followUpById.put(row.meetingId(), row.followUp());
        }

        return collected.stream()
                .map(meeting -> AgentSuggestionRequest.Meeting.builder()
                        .meetingId(meeting.meetingId())
                        .title(meeting.title())
                        .meetingDate(meeting.meetingDate())
                        .authorName(meeting.authorName())
                        // 미입력은 null이 아니라 빈 문자열로 통일한다. 규격의 예시가 ""이고,
                        // "정리 안 됨"을 판정하는 쪽이 null과 "" 두 가지를 다 처리할 이유가 없다.
                        .followUp(followUpById.getOrDefault(meeting.meetingId(), ""))
                        .build())
                .toList();
    }

    // 내 일정 중 앞으로 2주간의 회의. userIds=null이면 도구가 본인으로 잡는다.
    private List<AgentSuggestionRequest.UpcomingMeeting> upcomingMeetings(Long userId,
                                                                          LocalDate today) {
        AgentScheduleListResponse schedules = calendarToolService.listSchedules(
                userId, today, today.plusDays(UPCOMING_MEETING_DAYS), null,
                SCHEDULE_TYPE_MEETING);

        return schedules.schedules().stream()
                .limit(UPCOMING_MEETING_SIZE)
                .map(schedule -> new AgentSuggestionRequest.UpcomingMeeting(
                        schedule.title(), schedule.startAt()))
                .toList();
    }

    // 대화 내역 목록과 같은 쿼리를 크기만 바꿔 쓴다(커서 없이 첫 페이지).
    private List<String> recentQuestions(Long userId) {
        return conversationRepository
                .findScroll(userId, null, null, PageRequest.of(0, RECENT_QUESTION_SIZE))
                .stream()
                .map(AgentConversationEntity::getTitle)
                .toList();
    }

    // ================================= 내부 =================================

    /**
     * 재료 하나를 읽되, 실패하면 기본값으로 넘어갑니다.
     *
     * <p>추천은 부가 기능입니다. 재료 하나가 빠지면 후보가 하나 줄 뿐이고, 그것 때문에 패널 전체가
     * 비는 편이 더 나쁩니다. 삼킨 실패는 반드시 로그로 남겨 조용히 사라지지 않게 합니다.</p>
     */
    private <T> T quietly(Long userId, String label, Supplier<T> fallback, Supplier<T> source) {
        try {
            return source.get();
        } catch (RuntimeException failure) {
            log.warn("[에이전트 추천] {} 재료를 읽지 못해 건너뜁니다. userId={}", label, userId, failure);
            return fallback.get();
        }
    }
}
