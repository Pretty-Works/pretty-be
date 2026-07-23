package HK.PrettyWorks_BE.calendar.schedule.service;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
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
import HK.PrettyWorks_BE.calendar.schedule.repository.ScheduleParticipantRepository;
import HK.PrettyWorks_BE.calendar.schedule.repository.ScheduleRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;

    @Transactional
    public ScheduleCreateResponse create(Long writerId, ScheduleCreateRequest request) {
        // 1) 작성자 조회 — 토큰의 userId로 조회한다. 토큰은 유효한데 유저가 없으면 인증 자체를 신뢰할 수 없으므로 UNAUTHORIZED.
        userRepository.findById(writerId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));

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

        // 4) 참가자 정리 — 작성자는 아래서 WRITER로 따로 등록하므로 제외, userId 중복 제거(입력 순서 유지)
        List<Long> requestedIds = request.participantUserIds() == null ? List.of() : request.participantUserIds();
        Set<Long> participantIds = new LinkedHashSet<>();
        for (Long userId : requestedIds) {
            if (userId == null || userId.equals(writerId)) {
                continue;
            }
            participantIds.add(userId);
        }
        // 4-1) 참가자 존재 검증(USER_002) + 퇴사자 차단(USER_003). 휴직(ON_LEAVE)은 복귀 예정·인수인계 등으로 일정에 필요하므로 허용.
        if (!participantIds.isEmpty()) {
            List<UserEntity> found = userRepository.findAllById(participantIds);
            if (found.size() != participantIds.size()) {
                throw BaseException.type(UserErrorCode.USER_NOT_FOUND);
            }
            for (UserEntity participant : found) {
                if (participant.getStatus() == StatusType.RESIGNED) {
                    throw BaseException.type(UserErrorCode.RESIGNED_USER);
                }
            }
        }

        // 5) 저장 (schedule → participants, 모두 이 트랜잭션 안에서)
        // 5-1) schedule
        ScheduleEntity schedule = ScheduleEntity.builder()
                .userId(writerId)
                .title(request.title())
                .startAt(startAt)
                .endAt(endAt)
                .allDay(allDay)
                .build();
        scheduleRepository.save(schedule);

        // 5-2) participants: 작성자(WRITER) + 참가자(PARTICIPANT)
        List<ScheduleParticipantEntity> participants = new ArrayList<>();
        participants.add(ScheduleParticipantEntity.builder()
                .scheduleId(schedule.getId())
                .userId(writerId)
                .role(ParticipantRole.WRITER)
                .build());
        for (Long userId : participantIds) {
            participants.add(ScheduleParticipantEntity.builder()
                    .scheduleId(schedule.getId())
                    .userId(userId)
                    .role(ParticipantRole.PARTICIPANT)
                    .build());
        }
        scheduleParticipantRepository.saveAll(participants);

        // 6) 생성된 일정 id 반환
        return ScheduleCreateResponse.builder()
                .scheduleId(schedule.getId())
                .build();
    }

    @Transactional
    public void delete(Long userId, Long scheduleId) {
        // 1) 일정 조회 — 없으면 SCHEDULE_001(404)
        ScheduleEntity schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> BaseException.type(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        // 2) 소유권 검증 — 작성자(user_id) 본인만 삭제 가능, 아니면 SCHEDULE_003(403)
        if (!schedule.getUserId().equals(userId)) {
            throw BaseException.type(ScheduleErrorCode.NO_SCHEDULE_PERMISSION);
        }

        // 3) 하드 삭제. schedule_participants·schedule_leaves는 FK ON DELETE CASCADE로 DB가 함께 정리한다.
        scheduleRepository.delete(schedule);
    }

    @Transactional
    public ScheduleUpdateResponse update(Long userId, Long scheduleId, ScheduleUpdateRequest request) {
        // 1) 일정 조회 — 없으면 SCHEDULE_001(404). 영속 상태로 로드되어 더티 체킹 대상이 된다.
        ScheduleEntity schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> BaseException.type(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        // 2) 소유권 검증 — 작성자 본인만 수정 가능, 아니면 SCHEDULE_003(403)
        if (!schedule.getUserId().equals(userId)) {
            throw BaseException.type(ScheduleErrorCode.NO_SCHEDULE_PERMISSION);
        }

        // 3) TODO(휴가): schedule_leaves가 있는 '휴가 일정'은 수정 대상 아님(취소 후 재신청) — 휴가 도메인 구현 시 체크 추가.

        // 4) 최종값 병합 — 전달된(non-null) 필드만 반영하고 나머지는 기존값을 유지한다.
        String title = request.title() != null ? request.title() : schedule.getTitle();
        boolean allDay = request.allDay() != null ? request.allDay() : schedule.isAllDay();
        LocalDateTime startAt = request.startAt() != null ? request.startAt() : schedule.getStartAt();
        LocalDateTime endAt = request.endAt() != null ? request.endAt() : schedule.getEndAt();

        // 4-1) allDay면 최종 시각 정규화(00:00:00 ~ 23:59:59)
        if (allDay) {
            startAt = startAt.toLocalDate().atStartOfDay();
            endAt = endAt.toLocalDate().atTime(23, 59, 59);
        }
        // 4-2) 기간 검증(SCHEDULE_002): '최종값' 기준. 같은 시각 허용.
        if (startAt.isAfter(endAt)) {
            throw BaseException.type(ScheduleErrorCode.INVALID_PERIOD);
        }

        // 5) 일정 필드 갱신 — 더티 체킹으로 커밋 시 UPDATE (save 불필요)
        schedule.update(title, startAt, endAt, allDay);

        // 6) 참가자 교체 — participantUserIds가 '전달된 경우에만'. null이면 기존 참가자 그대로 유지.
        if (request.participantUserIds() != null) {
            // 6-1) 기존 PARTICIPANT 행 삭제 후 즉시 flush.
            //      Hibernate는 flush 시 INSERT를 DELETE보다 먼저 실행하므로, 여기서 삭제를 확정하지 않으면
            //      교체 목록에 기존 참가자가 겹칠 때 UNIQUE(user_id, schedule_id) 충돌이 난다.
            scheduleParticipantRepository.deleteByScheduleIdAndRole(scheduleId, ParticipantRole.PARTICIPANT);
            scheduleParticipantRepository.flush();

            // 6-2) 새 참가자 정리 — 작성자 제외, null·중복 제거(입력 순서 유지)
            Set<Long> participantIds = new LinkedHashSet<>();
            for (Long participantId : request.participantUserIds()) {
                if (participantId == null || participantId.equals(userId)) {
                    continue;
                }
                participantIds.add(participantId);
            }
            // 6-3) 존재 검증(USER_002) + 퇴사자 차단(USER_003). 빈 배열이면 이 블록을 건너뛰어 작성자 혼자로 축소된다.
            if (!participantIds.isEmpty()) {
                List<UserEntity> found = userRepository.findAllById(participantIds);
                if (found.size() != participantIds.size()) {
                    throw BaseException.type(UserErrorCode.USER_NOT_FOUND);
                }
                for (UserEntity participant : found) {
                    if (participant.getStatus() == StatusType.RESIGNED) {
                        throw BaseException.type(UserErrorCode.RESIGNED_USER);
                    }
                }
                // 6-4) 새 PARTICIPANT 행 저장
                List<ScheduleParticipantEntity> newParticipants = new ArrayList<>();
                for (Long participantId : participantIds) {
                    newParticipants.add(ScheduleParticipantEntity.builder()
                            .scheduleId(scheduleId)
                            .userId(participantId)
                            .role(ParticipantRole.PARTICIPANT)
                            .build());
                }
                scheduleParticipantRepository.saveAll(newParticipants);
            }
        }

        // 7) 수정된 일정 id 반환
        return ScheduleUpdateResponse.builder()
                .scheduleId(schedule.getId())
                .build();
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
        List<ScheduleParticipantEntity> participants = scheduleParticipantRepository.findByScheduleIdIn(scheduleIds);

        // 6) [쿼리3] 참가자들의 이름 (userId → name 맵)
        Set<Long> participantUserIds = participants.stream()
                .map(ScheduleParticipantEntity::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nameById = userRepository.findAllById(participantUserIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));

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

            items.add(ScheduleItem.builder()
                    .id(schedule.getId())
                    .title(schedule.getTitle())
                    .startAt(schedule.getStartAt())
                    .endAt(schedule.getEndAt())
                    .allDay(schedule.isAllDay())
                    // A안: 휴가 도메인 전까지 항상 false. TODO(휴가): schedule_leaves 조인으로 isLeave·leaveType 계산.
                    .isLeave(false)
                    .owner(owner)
                    .participants(participantDtos)
                    .build());
        }

        return ScheduleListResponse.builder().schedules(items).build();
    }
}
