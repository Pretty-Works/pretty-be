package HK.PrettyWorks_BE.calendar.schedule.service;

import HK.PrettyWorks_BE.calendar.leave.domain.ScheduleLeaveEntity;
import HK.PrettyWorks_BE.calendar.leave.repository.ScheduleLeaveRepository;
import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleParticipantEntity;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleUpdateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse.Owner;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse.Participant;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse.ScheduleItem;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleUpdateResponse;
import HK.PrettyWorks_BE.calendar.schedule.exception.ScheduleErrorCode;
import HK.PrettyWorks_BE.calendar.schedule.policy.SchedulePolicy;
import HK.PrettyWorks_BE.calendar.schedule.repository.ScheduleParticipantRepository;
import HK.PrettyWorks_BE.calendar.schedule.repository.ScheduleRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ScheduleLeaveRepository scheduleLeaveRepository;
    private final IdempotencyService idempotencyService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    // ================================= 공개 API =================================

    // 일정 생성. 멱등 키가 있으면 중복 요청을 방어한다(같은 키·같은 요청은 첫 응답 재생, 다른 내용은 409).
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않는다.
    public ScheduleCreateResponse create(Long writerId, String idempotencyKey, ScheduleCreateRequest request) {
        Supplier<Long> creator = () -> doCreate(writerId, request);

        String endpoint = "POST /api/v1/calendar/schedules";
        String fingerprint = "POST|/api/v1/calendar/schedules|" + canonical(request);

        return new ScheduleCreateResponse(
                idempotencyService.run(idempotencyKey, endpoint, writerId, fingerprint, creator));
    }

    @Transactional(readOnly = true)
    public ScheduleListResponse list(Long userId, LocalDate from, LocalDate to, List<Long> userIds) {
        // 1) 기간 검증(SCHEDULE_004): 조회 시작일이 종료일보다 늦으면 차단
        if (from.isAfter(to)) {
            throw BaseException.type(ScheduleErrorCode.INVALID_SEARCH_PERIOD);
        }

        // 2) 대상 사용자 = 본인 ∪ userIds. 본인은 항상 포함, null·중복 제거.
        //    존재하지 않는 id는 참가자 테이블에 없어 자연히 무시된다(명세: 존재하지 않는 userIds 무시).
        Set<Long> targetUserIds = new LinkedHashSet<>();
        targetUserIds.add(userId);
        if (userIds != null) {
            for (Long id : userIds) {
                if (id != null) {
                    targetUserIds.add(id);
                }
            }
        }

        // 3) 날짜를 일시 범위로 변환: from 00:00:00 ~ to 23:59:59
        LocalDateTime fromStart = from.atStartOfDay();
        LocalDateTime toEnd = to.atTime(23, 59, 59);

        // 4) [쿼리1] 기간과 겹치고 + 대상 사용자가 참가자인 일정 (startAt ASC)
        List<ScheduleEntity> schedules =
                scheduleRepository.findOverlappingByParticipants(fromStart, toEnd, new ArrayList<>(targetUserIds));
        if (schedules.isEmpty()) {
            return ScheduleListResponse.builder().schedules(List.of()).build();
        }

        // 5) [쿼리2] 결과 일정들의 참가자 전부 (IN 절 한 번)
        List<Long> scheduleIds = schedules.stream().map(ScheduleEntity::getId).toList();
        List<ScheduleParticipantEntity> participants = scheduleParticipantRepository.findByScheduleIdInAndLeftAtIsNull(scheduleIds);

        // 5-1) [쿼리2-1] 결과 일정 중 '휴가'인 것 조회 → scheduleId→휴가 엔티티 맵. 존재하면 그 일정은 휴가다.
        //      leaveId/leaveType/reason/days를 항목에 실어 편집 모달 연동(수정·취소 키 + 사유 프리필)에 쓴다.
        Map<Long, ScheduleLeaveEntity> leaveByScheduleId = scheduleLeaveRepository.findByScheduleIdIn(scheduleIds).stream()
                .collect(Collectors.toMap(ScheduleLeaveEntity::getScheduleId, leave -> leave));

        // 6) [쿼리3] 참가자들의 이름 (userId → name 맵). user 도메인 UserService가 N+1 없이 일괄 조회.
        Set<Long> participantUserIds = participants.stream()
                .map(ScheduleParticipantEntity::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nameById = userService.getNameMap(participantUserIds);

        // 7) 일정별 참가자 그룹핑 (scheduleId → 참가자 목록)
        Map<Long, List<ScheduleParticipantEntity>> participantsByScheduleId = participants.stream()
                .collect(Collectors.groupingBy(ScheduleParticipantEntity::getScheduleId));

        // 8) 조립 — 일정마다 owner(WRITER)와 참가자 목록(이름 포함)을 만든다.
        List<ScheduleItem> items = new ArrayList<>();
        for (ScheduleEntity schedule : schedules) {
            List<ScheduleParticipantEntity> scheduleParticipants =
                    participantsByScheduleId.getOrDefault(schedule.getId(), List.of());

            Owner owner = null;
            List<Participant> participantDtos = new ArrayList<>();
            for (ScheduleParticipantEntity p : scheduleParticipants) {
                String name = nameById.get(p.getUserId());
                participantDtos.add(Participant.builder()
                        .userId(p.getUserId())
                        .name(name)
                        .role(p.getRole().name())
                        .build());
                if (p.getRole() == ParticipantRole.WRITER) {
                    owner = Owner.builder().userId(p.getUserId()).name(name).build();
                }
            }

            // 휴가면 schedule_leaves 행이 존재. 완전공개 정책이라 leaveId/유형/사유/일수를 그대로 노출(마스킹 없음).
            ScheduleLeaveEntity leave = leaveByScheduleId.get(schedule.getId());
            boolean isLeave = leave != null;

            items.add(ScheduleItem.builder()
                    .id(schedule.getId())
                    .title(schedule.getTitle())
                    .startAt(schedule.getStartAt())
                    .endAt(schedule.getEndAt())
                    .allDay(schedule.isAllDay())
                    .type(schedule.getType().name())
                    .isLeave(isLeave)
                    .leaveId(isLeave ? leave.getId() : null)
                    .leaveType(isLeave ? leave.getLeaveType().name() : null)
                    .reason(isLeave ? leave.getReason() : null)
                    .days(isLeave ? leave.getDays() : null)
                    .owner(owner)
                    .participants(participantDtos)
                    .build());
        }

        return ScheduleListResponse.builder().schedules(items).build();
    }

    @Transactional
    public ScheduleUpdateResponse update(Long userId, Long scheduleId, ScheduleUpdateRequest request) {
        // 1) 일정 로드 + 소유권 검증(SCHEDULE_001/003). 영속 상태로 로드되어 더티 체킹 대상이 된다. 수정·삭제 공용 가드.
        ScheduleEntity schedule = loadOwnedSchedule(scheduleId, userId);

        // 2) 휴가 차단(SCHEDULE_007): schedule_leaves 행이 있는 '휴가 일정'은 범용 일정 API로 수정 불가.
        //    휴가는 전용 API(PATCH /calendar/leaves/{leaveId})로만 수정해 schedules+schedule_leaves 정합성을 유지한다.
        if (scheduleLeaveRepository.existsByScheduleId(scheduleId)) {
            throw BaseException.type(ScheduleErrorCode.LEAVE_NOT_EDITABLE_HERE);
        }

        // 3) 최종값 병합 — 전달된(non-null) 필드만 반영하고 나머지는 기존값을 유지한다.
        String title = request.title() != null ? request.title() : schedule.getTitle();
        boolean allDay = request.allDay() != null ? request.allDay() : schedule.isAllDay();
        ScheduleType type = request.type() != null ? request.type() : schedule.getType();
        LocalDateTime startAt = request.startAt() != null ? request.startAt() : schedule.getStartAt();
        LocalDateTime endAt = request.endAt() != null ? request.endAt() : schedule.getEndAt();

        // 3-1) allDay면 최종 시각 정규화(00:00:00 ~ 23:59:59)
        if (allDay) {
            startAt = startAt.toLocalDate().atStartOfDay();
            endAt = endAt.toLocalDate().atTime(23, 59, 59);
        }
        // 3-2) 기간 검증(SCHEDULE_002): '최종값' 기준. 같은 시각 허용.
        if (startAt.isAfter(endAt)) {
            throw BaseException.type(ScheduleErrorCode.INVALID_PERIOD);
        }

        // 4) 일정 필드 갱신 — 더티 체킹으로 커밋 시 UPDATE (save 불필요)
        schedule.update(title, startAt, endAt, allDay, type);

        // 5) 참가자 교체(diff) — participantUserIds가 '전달된 경우에만'. null이면 기존 참가자 그대로 유지.
        if (request.participantUserIds() != null) {
            syncParticipants(scheduleId, userId, request.participantUserIds());
        }

        // 6) 수정된 일정 id 반환
        return ScheduleUpdateResponse.builder()
                .scheduleId(schedule.getId())
                .build();
    }

    @Transactional
    public void delete(Long userId, Long scheduleId) {
        // 1) 일정 로드 + 소유권 검증(SCHEDULE_001/003). 수정·삭제 공용 가드.
        ScheduleEntity schedule = loadOwnedSchedule(scheduleId, userId);

        // 2) 하드 삭제. schedule_participants·schedule_leaves는 FK ON DELETE CASCADE로 DB가 함께 정리한다.
        scheduleRepository.delete(schedule);
    }

    @Transactional
    public void leave(Long userId, Long scheduleId) {
        // 1) 일정 조회 — 없으면 SCHEDULE_001(404)
        ScheduleEntity schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> BaseException.type(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        // 2) 내 '활성' 참가 행 — 없으면 참가자 아님(SCHEDULE_006, 404). 이미 나갔거나 애초에 미참여.
        ScheduleParticipantEntity myParticipation = scheduleParticipantRepository
                .findByScheduleIdAndUserIdAndLeftAtIsNull(scheduleId, userId)
                .orElseThrow(() -> BaseException.type(ScheduleErrorCode.NOT_A_PARTICIPANT));

        // 3) 작성자(오너)인 경우 — 나가기 대신 전체삭제 규칙 적용
        if (SchedulePolicy.canModify(schedule, userId)) {
            // 3-1) 다른 활성 참가자가 있으면 나갈 수 없음(SCHEDULE_005). 전체 삭제를 이용해야 한다.
            boolean othersActive = scheduleParticipantRepository
                    .existsByScheduleIdAndUserIdNotAndLeftAtIsNull(scheduleId, userId);
            if (othersActive) {
                throw BaseException.type(ScheduleErrorCode.OWNER_CANNOT_LEAVE);
            }
            // 3-2) 혼자면 나가기 = 전체삭제(hard). schedule_participants·schedule_leaves는 CASCADE 정리.
            scheduleRepository.delete(schedule);
            return;
        }

        // 4) 일반 참가자 — 본인 참여만 soft delete(left_at 세팅 → 더티 체킹으로 UPDATE)
        myParticipation.leave();
    }

    // ================================= 내부 헬퍼 =================================

    // --- 생성 ---

    // 검증(작성자·기간·참가자) + 저장(schedule → participants) 후 생성된 scheduleId 반환.
    // 트랜잭션은 IdempotencyService의 TransactionTemplate이 제공(자체 @Transactional 미부착 — self-invocation 회피).
    private Long doCreate(Long writerId, ScheduleCreateRequest request) {
        // 1) 작성자 조회 — 토큰 userId로 현재 유저 로드(없으면 UNAUTHORIZED). user 도메인 공용 진입점 재사용.
        currentUserService.getCurrentUser(writerId);

        // 2) allDay면 시간 정규화(00:00:00 ~ 23:59:59). 이후 검증·저장은 이 '최종값' 기준.
        //    allDay 생략(null)은 false로 처리. Boolean.TRUE.equals는 null/false→false, true만 true.
        boolean allDay = Boolean.TRUE.equals(request.allDay());
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();
        if (allDay) {
            startAt = startAt.toLocalDate().atStartOfDay();
            endAt = endAt.toLocalDate().atTime(23, 59, 59);
        }

        // 3) 기간 검증(SCHEDULE_002): 시작이 종료보다 늦으면 차단. 같은 시각은 허용. 과거 날짜는 허용.
        //    친절한 메시지를 위해 project 도메인처럼 전용 코드를 사용한다.
        if (startAt.isAfter(endAt)) {
            throw BaseException.type(ScheduleErrorCode.INVALID_PERIOD);
        }

        // 4) 참가자 정리·검증 — 작성자 제외·중복 제거 후 존재(USER_002)·재직(USER_003) 검증. create·update 공용 헬퍼.
        Set<Long> participantIds = resolveValidParticipantIds(writerId, request.participantUserIds());

        // 5) 저장 (schedule → participants, 모두 이 트랜잭션 안에서)
        // 5-1) schedule
        ScheduleEntity schedule = ScheduleEntity.builder()
                .userId(writerId)
                .title(request.title())
                .startAt(startAt)
                .endAt(endAt)
                .allDay(allDay)
                .type(request.type())
                .build();
        scheduleRepository.save(schedule);

        // 5-2) participants: 작성자(WRITER) + 참가자(PARTICIPANT)
        List<ScheduleParticipantEntity> participants = new ArrayList<>();
        participants.add(ScheduleParticipantEntity.builder()
                .scheduleId(schedule.getId())
                .userId(writerId)
                .role(ParticipantRole.WRITER)
                .build());
        participants.addAll(toParticipantEntities(schedule.getId(), participantIds));
        scheduleParticipantRepository.saveAll(participants);

        // 6) 생성된 일정 id 반환
        return schedule.getId();
    }

    // 본문 지문(정규화) — 클라이언트가 보낸 본문 필드를 고정 순서로 이어붙인다. 경로는 상위 fingerprint에서 포함.
    private String canonical(ScheduleCreateRequest r) {
        return r.title() + "|" + r.startAt() + "|" + r.endAt() + "|" + r.allDay()
                + "|" + r.type() + "|" + r.participantUserIds();
    }

    // --- 수정·삭제 공용 가드 ---

    // 수정·삭제 공용 가드: 일정 로드(없으면 SCHEDULE_001) + 작성자 소유권 검증(아니면 SCHEDULE_003).
    private ScheduleEntity loadOwnedSchedule(Long scheduleId, Long userId) {
        ScheduleEntity schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> BaseException.type(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!SchedulePolicy.canModify(schedule, userId)) {
            throw BaseException.type(ScheduleErrorCode.NO_SCHEDULE_PERMISSION);
        }
        return schedule;
    }

    // --- 참가자 ---

    // 참가자 목록 정리·검증(create·update 공용): 작성자 제외 + null·중복 제거(입력 순서 유지) 후,
    // 존재 검증(USER_002) + 퇴사자 차단(USER_003). 휴직(ON_LEAVE)은 복귀 예정·인수인계 등으로 일정에 필요하므로 허용.
    private Set<Long> resolveValidParticipantIds(Long ownerId, List<Long> requestedIds) {
        Set<Long> participantIds = new LinkedHashSet<>();
        if (requestedIds != null) {
            for (Long id : requestedIds) {
                if (id == null || id.equals(ownerId)) {
                    continue;
                }
                participantIds.add(id);
            }
        }
        if (!participantIds.isEmpty()) {
            List<UserEntity> found = userRepository.findAllById(participantIds);
            if (found.size() != participantIds.size()) {
                throw BaseException.type(UserErrorCode.USER_NOT_FOUND);
            }
            for (UserEntity participant : found) {
                // 재직 판정은 user 도메인 UserPolicy가 소유: isEmployed = 퇴사(RESIGNED)만 거부, 휴직(ON_LEAVE)은 허용.
                if (!UserPolicy.isEmployed(participant)) {
                    throw BaseException.type(UserErrorCode.RESIGNED_USER);
                }
            }
        }
        return participantIds;
    }

    // 주어진 userId들을 PARTICIPANT 역할 참가자 엔티티로 변환.
    private List<ScheduleParticipantEntity> toParticipantEntities(Long scheduleId, Set<Long> userIds) {
        List<ScheduleParticipantEntity> participants = new ArrayList<>();
        for (Long userId : userIds) {
            participants.add(ScheduleParticipantEntity.builder()
                    .scheduleId(scheduleId)
                    .userId(userId)
                    .role(ParticipantRole.PARTICIPANT)
                    .build());
        }
        return participants;
    }

    // 참가자 목록 동기화(diff) — 요청 목록을 최종 상태로 맞춘다. project 도메인 updateParticipants와 동일 패턴.
    // 신규 insert / 나갔던 사람 재활성화 / 빠진 활성 참가자 soft-delete. 안 바뀐 행은 손대지 않아 left_at 이력을 보존한다.
    private void syncParticipants(Long scheduleId, Long ownerId, List<Long> requestedRawIds) {
        // 1) 유효한 요청 참가자 정리·검증(작성자 제외·중복 제거 + 존재/재직)
        Set<Long> requestedIds = resolveValidParticipantIds(ownerId, requestedRawIds);

        // 2) 현재 PARTICIPANT 행(활성 + 나간 것 모두)을 userId→엔티티로 로드
        Map<Long, ScheduleParticipantEntity> current = new LinkedHashMap<>();
        for (ScheduleParticipantEntity p : scheduleParticipantRepository
                .findByScheduleIdAndRole(scheduleId, ParticipantRole.PARTICIPANT)) {
            current.put(p.getUserId(), p);
        }

        // 3) 요청에 있는 사람: 없으면 신규 insert / 나갔던(left_at) 사람이면 재활성화 / 활성이면 그대로 둔다.
        Set<Long> toInsert = new LinkedHashSet<>();
        for (Long reqId : requestedIds) {
            ScheduleParticipantEntity existing = current.get(reqId);
            if (existing == null) {
                toInsert.add(reqId);
            } else if (!existing.isActive()) {
                existing.reactivate();
            }
        }
        if (!toInsert.isEmpty()) {
            scheduleParticipantRepository.saveAll(toParticipantEntities(scheduleId, toInsert));
        }

        // 4) 요청에서 빠진 '활성' 참가자: soft-delete(left_at). 이미 나간 사람은 그대로 둔다.
        for (ScheduleParticipantEntity p : current.values()) {
            if (p.isActive() && !requestedIds.contains(p.getUserId())) {
                p.leave();
            }
        }
        // 기존 엔티티(reactivate/leave)는 영속 상태라 커밋 시 dirty checking으로 자동 UPDATE.
    }
}
