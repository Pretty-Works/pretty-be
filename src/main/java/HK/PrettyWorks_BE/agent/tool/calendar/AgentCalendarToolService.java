package HK.PrettyWorks_BE.agent.tool.calendar;

import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveBalanceResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveListResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveUpdateRequest;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentScheduleListResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentScheduleUpdateRequest;
import HK.PrettyWorks_BE.agent.tool.common.AgentPage;
import HK.PrettyWorks_BE.agent.tool.common.AgentWriteResults;
import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveCreateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveBalanceResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveCreateResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveListResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveUpdateResponse;
import HK.PrettyWorks_BE.calendar.leave.service.LeaveService;
import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse.ScheduleItem;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleUpdateResponse;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.util.InclusiveDays;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// schedule.list · leave.list · leave.balance + 일정·휴가 쓰기.
//
// 조회는 기존 ScheduleService·LeaveService를 그대로 부른다. 이 서비스가 하는 일은
// ① 에이전트 전용 상한(기간·인원·건수)을 걸고 ② 응답을 좁히는 것뿐이다.
@Service
@RequiredArgsConstructor
public class AgentCalendarToolService {

    // 달력 두 달치를 넘는 통째 조회를 막는다. 휴가는 "올해 언제 썼더라"가 자연스러운 질문이라 1년까지 연다.
    private static final int MAX_SCHEDULE_RANGE_DAYS = 62;
    private static final int MAX_LEAVE_RANGE_DAYS = 366;
    private static final int MAX_TARGET_USERS = 20;

    private static final int MIN_BALANCE_YEAR = 2020;
    private static final int MAX_BALANCE_YEAR = 2100;

    // 휴가는 schedules에 type=PERSONAL로 저장되고 schedule_leaves 행의 존재로 구분된다.
    // 에이전트에게는 "휴가"가 하나의 일정 유형으로 보이는 편이 자연스러워 응답에서만 LEAVE로 바꿔 보여준다.
    private static final String LEAVE_TYPE_LABEL = "LEAVE";

    private final ScheduleService scheduleService;
    private final LeaveService leaveService;

    // ================================= 조회 =================================

    public AgentScheduleListResponse listSchedules(Long userId, LocalDate from, LocalDate to,
                                                   List<Long> userIds, String type) {
        // from > to 는 ScheduleService가 SCHEDULE_004로 판정한다(일정 명세 기준). 여기선 상한만 본다.
        validateMaxRange(from, to, MAX_SCHEDULE_RANGE_DAYS);
        List<Long> targets = resolveTargets(userId, userIds);

        // 상한보다 하나 더 받아 본다. 딱 상한만큼 받으면 "마침 그만큼"인지 "잘렸는지" 구분할 수 없다.
        ScheduleListResponse source = scheduleService.list(
                from, to, targets, queryType(type), PageRequest.of(0, AgentPage.MAX_SIZE + 1));
        List<ScheduleItem> fetched = source.schedules();
        boolean truncated = fetched.size() > AgentPage.MAX_SIZE;

        // PERSONAL과 LEAVE는 DB에서 갈리지 않는다(둘 다 type=PERSONAL, 구분은 schedule_leaves 존재).
        // 그래서 이 두 경우만 조회 뒤에 한 번 더 거른다 — 이미 상한이 걸린 집합이라 비용은 고정이지만,
        // 그 페이지 안에서 걸러진 만큼 결과가 상한보다 적게 나올 수 있다.
        List<AgentScheduleListResponse.AgentSchedule> schedules = fetched.stream()
                .limit(AgentPage.MAX_SIZE)
                .filter(item -> matchesLeaveness(item, type))
                .map(item -> AgentScheduleListResponse.AgentSchedule.builder()
                        .scheduleId(item.id())
                        .title(item.title())
                        .startAt(item.startAt())
                        .endAt(item.endAt())
                        .allDay(item.allDay())
                        .type(displayType(item))
                        .canEdit(isOwner(item, userId))
                        .participantNames(item.participants().stream()
                                .map(ScheduleListResponse.Participant::name)
                                .toList())
                        .isLeave(item.isLeave())
                        .build())
                .toList();

        return AgentScheduleListResponse.builder()
                .from(from)
                .to(to)
                .schedules(schedules)
                .totalCount(schedules.size())
                .truncated(truncated)
                .build();
    }

    public AgentLeaveListResponse listLeaves(Long userId, LocalDate from, LocalDate to,
                                             List<Long> userIds, String leaveType) {
        // 휴가 명세는 기간 오류도 REQUEST_001이다. 일정 서비스에 맡기면 SCHEDULE_004가 나가버린다.
        if (from == null || to == null || from.isAfter(to)) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        validateMaxRange(from, to, MAX_LEAVE_RANGE_DAYS);
        List<Long> targets = resolveTargets(userId, userIds);

        // 일정 전체를 훑고 isLeave로 거르지 않는다. 1년치 20명이면 전사 일정 수천 건을 읽고
        // 대부분 버리게 된다. schedule_leaves에서 시작하는 조회를 따로 둔 이유다.
        List<LeaveListResponse> fetched = leaveService.getLeaves(
                from, to, targets, parseLeaveType(leaveType), PageRequest.of(0, AgentPage.MAX_SIZE + 1));
        boolean truncated = fetched.size() > AgentPage.MAX_SIZE;

        List<AgentLeaveListResponse.AgentLeave> leaves = fetched.stream()
                .limit(AgentPage.MAX_SIZE)
                .map(leave -> {
                    boolean mine = userId.equals(leave.userId());
                    return AgentLeaveListResponse.AgentLeave.builder()
                            .leaveId(leave.leaveId())
                            .leaveType(leave.leaveType().name())
                            .startDate(leave.startDate())
                            .endDate(leave.endDate())
                            .days(leave.days())
                            // 남의 휴가 사유는 마스킹한다. 화면은 전원에게 보여주지만 에이전트는
                            // 그대로 답변에 옮겨 적을 수 있어 애초에 내려보내지 않는다.
                            .reason(mine ? leave.reason() : null)
                            .userId(leave.userId())
                            .userName(leave.userName())
                            .canEdit(mine)
                            .build();
                })
                .toList();

        return AgentLeaveListResponse.builder()
                .leaves(leaves)
                .totalCount(leaves.size())
                .truncated(truncated)
                .build();
    }

    public AgentLeaveBalanceResponse balance(Long userId, Integer year) {
        if (year != null && (year < MIN_BALANCE_YEAR || year > MAX_BALANCE_YEAR)) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        LeaveBalanceResponse balance = leaveService.getBalance(userId, year);

        // 근속연수(tenureYears)는 화면 카드용이라 뺀다.
        return AgentLeaveBalanceResponse.builder()
                .year(balance.year())
                .grantedDays(balance.grantedDays())
                .usedDays(balance.usedDays())
                .remainingDays(balance.remainingDays())
                .build();
    }

    // ================================= 쓰기 =================================

    public AgentWriteResults.ScheduleCreated createSchedule(Long userId, ScheduleCreateRequest request,
                                                            String idempotencyKey) {
        // 휴가는 schedules + schedule_leaves를 함께 만들어야 해서 이 경로로 들어오면 안 된다.
        // ScheduleType에 LEAVE 값 자체가 없어 역직렬화 단계에서 걸린다(REQUEST_012).
        ScheduleCreateResponse created = scheduleService.create(userId, idempotencyKey, request);

        return AgentWriteResults.ScheduleCreated.builder()
                .scheduleId(created.scheduleId())
                .title(request.title())
                .startAt(normalizedStart(request))
                .endAt(normalizedEnd(request))
                .participantCount(participantCount(userId, request.participantUserIds()))
                .build();
    }

    public AgentWriteResults.ScheduleUpdated updateSchedule(Long userId, AgentScheduleUpdateRequest request) {
        ScheduleUpdateResponse updated =
                scheduleService.update(userId, request.scheduleId(), request.toDomain());

        return AgentWriteResults.ScheduleUpdated.builder()
                .scheduleId(updated.scheduleId())
                .title(updated.title())
                .startAt(updated.startAt())
                .endAt(updated.endAt())
                .type(updated.type() == null ? null : updated.type().name())
                // 참가자를 교체했을 때만 센다. 요청 목록에서 세는 것은 생성과 같은 규칙이며,
                // 미전달이면 기존 유지라 알 수 없으므로 null로 둔다.
                .participantCount(request.participantUserIds() == null
                        ? null
                        : participantCount(userId, request.participantUserIds()))
                .build();
    }

    public AgentWriteResults.LeaveSaved createLeave(Long userId, LeaveCreateRequest request,
                                                    String idempotencyKey) {
        LeaveCreateResponse created = leaveService.create(userId, idempotencyKey, request);

        return AgentWriteResults.LeaveSaved.builder()
                .leaveId(created.leaveId())
                // 생성 응답에 연결 일정 id가 없다. 캘린더로 보내는 데(NAVIGATE) 필요하지 않아
                // 별도 조회를 걸지 않는다.
                .scheduleId(null)
                .leaveType(request.leaveType().name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .days(InclusiveDays.between(request.startDate(), request.endDate()))
                .remainingDaysAfter(remainingAfter(userId, request.startDate()))
                .build();
    }

    public AgentWriteResults.LeaveSaved updateLeave(Long userId, AgentLeaveUpdateRequest request) {
        LeaveUpdateResponse updated =
                leaveService.update(userId, request.leaveId(), request.toDomain());

        return AgentWriteResults.LeaveSaved.builder()
                .leaveId(updated.leaveId())
                .scheduleId(updated.scheduleId())
                .leaveType(updated.leaveType().name())
                .startDate(updated.startDate())
                .endDate(updated.endDate())
                .days(updated.days())
                .remainingDaysAfter(remainingAfter(userId, updated.startDate()))
                .build();
    }

    // ================================= 내부 헬퍼 =================================

    // --- 조회 대상·필터 ---

    // 도구는 userIds를 "조회 대상"으로 정의한다. 생략하면 본인, 명시하면 그 사람들만이다.
    // 화면처럼 본인을 자동으로 끼워 넣으면 "김서준님 목요일 비어?"에 내 일정이 섞인다.
    private List<Long> resolveTargets(Long userId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of(userId);
        }
        Set<Long> targets = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id != null) {
                targets.add(id);
            }
        }
        if (targets.isEmpty()) {
            return List.of(userId);
        }
        if (targets.size() > MAX_TARGET_USERS) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        return List.copyOf(targets);
    }

    // 에이전트가 말하는 LEAVE는 DB에서 PERSONAL이다. 그래서 유형 필터를 그대로 내려보낼 수 없다.
    private ScheduleType queryType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String normalized = type.trim().toUpperCase();
        if (LEAVE_TYPE_LABEL.equals(normalized)) {
            return ScheduleType.PERSONAL;
        }
        try {
            return ScheduleType.valueOf(normalized);
        } catch (IllegalArgumentException unknownType) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }

    // PERSONAL·LEAVE는 DB 필터로 갈리지 않아 여기서 마저 나눈다. 나머지 유형은 이미 정확하다.
    private boolean matchesLeaveness(ScheduleItem item, String type) {
        if (!StringUtils.hasText(type)) {
            return true;
        }
        String normalized = type.trim().toUpperCase();
        if (LEAVE_TYPE_LABEL.equals(normalized)) {
            return item.isLeave();
        }
        if (ScheduleType.PERSONAL.name().equals(normalized)) {
            return !item.isLeave();
        }
        return true;
    }

    private boolean isOwner(ScheduleItem item, Long userId) {
        return item.owner() != null && userId.equals(item.owner().userId());
    }

    private String displayType(ScheduleItem item) {
        return item.isLeave() ? LEAVE_TYPE_LABEL : item.type();
    }

    // from > to 는 도구마다 에러코드가 달라 여기서 보지 않는다. 기간 상한만 공통으로 막는다.
    private void validateMaxRange(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null || ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }

    private LeaveType parseLeaveType(String leaveType) {
        if (!StringUtils.hasText(leaveType)) {
            return null;
        }
        try {
            return LeaveType.valueOf(leaveType.trim().toUpperCase());
        } catch (IllegalArgumentException unknownType) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }

    // --- 쓰기 파생값 ---

    // 신청·수정이 반영된 뒤의 잔여 연차. 서버가 초과를 막지 않으므로 음수가 나올 수 있고,
    // 그 사실을 사용자에게 알리는 것이 이 값의 목적이다.
    // 귀속 연도는 시작일 기준 — LeaveService.getBalance의 집계 기준과 같아야 숫자가 맞는다.
    private int remainingAfter(Long userId, LocalDate startDate) {
        return leaveService.getBalance(userId, startDate.getYear()).remainingDays();
    }

    // 작성자(1명) + 정리된 참가자 수. 서버가 본인·중복을 걸러내므로 같은 기준으로 센다.
    private int participantCount(Long writerId, List<Long> requested) {
        if (requested == null) {
            return 1;
        }
        return 1 + (int) requested.stream()
                .filter(id -> id != null && !id.equals(writerId))
                .distinct()
                .count();
    }

    // allDay면 서버가 00:00:00~23:59:59로 정규화한다. 응답에 요청값을 그대로 실으면
    // 저장된 값과 다른 시각을 답변하게 된다.
    private LocalDateTime normalizedStart(ScheduleCreateRequest request) {
        return Boolean.TRUE.equals(request.allDay())
                ? request.startAt().toLocalDate().atStartOfDay()
                : request.startAt();
    }

    private LocalDateTime normalizedEnd(ScheduleCreateRequest request) {
        return Boolean.TRUE.equals(request.allDay())
                ? request.endAt().toLocalDate().atTime(23, 59, 59)
                : request.endAt();
    }
}
