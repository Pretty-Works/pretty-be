package HK.PrettyWorks_BE.calendar.leave.service;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.calendar.leave.domain.ScheduleLeaveEntity;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveCreateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveUpdateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveCreateResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveUpdateResponse;
import HK.PrettyWorks_BE.calendar.leave.exception.LeaveErrorCode;
import HK.PrettyWorks_BE.calendar.leave.repository.ScheduleLeaveRepository;
import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleParticipantEntity;
import HK.PrettyWorks_BE.calendar.schedule.repository.ScheduleParticipantRepository;
import HK.PrettyWorks_BE.calendar.schedule.repository.ScheduleRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ScheduleLeaveRepository scheduleLeaveRepository;
    private final IdempotencyService idempotencyService;

    // 휴가 신청. 멱등 키가 있으면 중복 요청을 방어한다(같은 키·같은 요청은 첫 응답 재생, 다른 내용은 409).
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않는다.
    public LeaveCreateResponse create(Long userId, String idempotencyKey, LeaveCreateRequest request) {
        Supplier<Long> creator = () -> doCreate(userId, request);

        String endpoint = "POST /api/v1/calendar/leaves";
        String fingerprint = "POST|/api/v1/calendar/leaves|" + canonical(request);

        return new LeaveCreateResponse(
                idempotencyService.run(idempotencyKey, endpoint, userId, fingerprint, creator));
    }

    // 검증(기간) + 저장(schedules → 참가자 → schedule_leaves) 후 생성된 leaveId 반환.
    // 트랜잭션은 IdempotencyService의 TransactionTemplate이 제공(자체 @Transactional 미부착 — self-invocation 회피).
    private Long doCreate(Long userId, LeaveCreateRequest request) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();

        // 1) 기간 검증(REQUEST_001): 시작일이 종료일보다 늦으면 차단. 같은 날 허용, 과거·미래 허용.
        if (startDate.isAfter(endDate)) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        // 2) 휴가도 schedules 행으로 저장(종일). 제목은 유형명, 기간 00:00:00~23:59:59, type은 PERSONAL(휴가 구분은 schedule_leaves로).
        String title = titleOf(request.leaveType());
        ScheduleEntity schedule = ScheduleEntity.builder()
                .userId(userId)
                .title(title)
                .startAt(startDate.atStartOfDay())
                .endAt(endDate.atTime(23, 59, 59))
                .allDay(true)
                .type(ScheduleType.PERSONAL)
                .build();
        scheduleRepository.save(schedule);

        // 3) 신청자를 WRITER 참가자로 등록
        scheduleParticipantRepository.save(ScheduleParticipantEntity.builder()
                .scheduleId(schedule.getId())
                .userId(userId)
                .role(ParticipantRole.WRITER)
                .build());

        // 4) 휴가 상세(schedule_leaves) 저장. days = 시작~종료 포함 일수.
        int days = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        ScheduleLeaveEntity leave = ScheduleLeaveEntity.builder()
                .scheduleId(schedule.getId())
                .leaveType(request.leaveType())
                .reason(request.reason())
                .days(days)
                .build();
        scheduleLeaveRepository.save(leave);

        // 5) 생성된 휴가 id 반환
        return leave.getId();
    }

    // 본문 지문(정규화) — 필드를 고정 순서로 이어붙인다. 경로는 상위 fingerprint에서 포함.
    private String canonical(LeaveCreateRequest r) {
        return r.leaveType() + "|" + r.startDate() + "|" + r.endDate() + "|" + r.reason();
    }

    @Transactional
    public LeaveUpdateResponse update(Long userId, Long leaveId, LeaveUpdateRequest request) {
        // 1) 내 휴가 로드 + 소유권 검증(LEAVE_001/002). update·cancel 공용 가드.
        OwnedLeave owned = loadOwnedLeave(userId, leaveId);
        ScheduleLeaveEntity leave = owned.leave();
        ScheduleEntity schedule = owned.schedule();

        // 2) 부분 수정: 전달값 있으면 교체, 없으면(null) 기존값 유지.
        //    (일정은 startAt=날짜.atStartOfDay(), endAt=날짜.atTime(23:59:59)로 저장하므로 toLocalDate()로 원래 날짜 복원.)
        LeaveType newType = request.leaveType() != null ? request.leaveType() : leave.getLeaveType();
        LocalDate newStart = request.startDate() != null ? request.startDate() : schedule.getStartAt().toLocalDate();
        LocalDate newEnd = request.endDate() != null ? request.endDate() : schedule.getEndAt().toLocalDate();
        String newReason = request.reason() != null ? request.reason() : leave.getReason();

        // 3) 최종 기간 검증(REQUEST_001). 같은 날 허용, 과거·미래 허용.
        if (newStart.isAfter(newEnd)) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        // 4) 일정 행 갱신(제목=유형명, 기간 정규화, 종일·PERSONAL 고정). 영속 엔티티라 더티체킹으로 UPDATE.
        schedule.update(
                titleOf(newType),
                newStart.atStartOfDay(),
                newEnd.atTime(23, 59, 59),
                true,
                ScheduleType.PERSONAL
        );

        // 5) 휴가 상세 갱신. days = 시작~종료 포함 일수로 재계산.
        int days = (int) ChronoUnit.DAYS.between(newStart, newEnd) + 1;
        leave.update(newType, newReason, days);

        // 6) 재계산된 최종값을 응답에 담아 편집 모달이 바로 반영하게 함.
        return LeaveUpdateResponse.builder()
                .leaveId(leave.getId())
                .scheduleId(schedule.getId())
                .leaveType(newType)
                .startDate(newStart)
                .endDate(newEnd)
                .days(days)
                .reason(newReason)
                .build();
    }

    @Transactional
    public void cancel(Long userId, Long leaveId) {
        // 1) 내 휴가 로드 + 소유권 검증(LEAVE_001/002). cancel은 일정만 사용.
        ScheduleEntity schedule = loadOwnedLeave(userId, leaveId).schedule();

        // 2) 연결된 일정을 하드 삭제. schedule_leaves·schedule_participants는 FK ON DELETE CASCADE로 DB가 함께 정리한다.
        scheduleRepository.delete(schedule);
    }

    // 휴가 + 연결 일정을 로드하고 소유권을 검증한다(LEAVE_001/002). update·cancel 공용 가드.
    private OwnedLeave loadOwnedLeave(Long userId, Long leaveId) {
        ScheduleLeaveEntity leave = scheduleLeaveRepository.findById(leaveId)
                .orElseThrow(() -> BaseException.type(LeaveErrorCode.LEAVE_NOT_FOUND));
        // 일정이 없으면 데이터 무결성 문제 — 클라이언트는 leaveId만 아므로 동일하게 404 처리.
        ScheduleEntity schedule = scheduleRepository.findById(leave.getScheduleId())
                .orElseThrow(() -> BaseException.type(LeaveErrorCode.LEAVE_NOT_FOUND));
        if (!schedule.getUserId().equals(userId)) {
            throw BaseException.type(LeaveErrorCode.NO_LEAVE_PERMISSION);
        }
        return new OwnedLeave(leave, schedule);
    }

    // 두 엔티티를 함께 돌려주는 작은 묶음. update는 둘 다, cancel은 일정만 사용.
    private record OwnedLeave(ScheduleLeaveEntity leave, ScheduleEntity schedule) {
    }

    // 휴가 유형 → 일정 제목 매핑(연차/병가). create·update 공용.
    private String titleOf(LeaveType leaveType) {
        return leaveType == LeaveType.ANNUAL ? "연차" : "병가";
    }
}
