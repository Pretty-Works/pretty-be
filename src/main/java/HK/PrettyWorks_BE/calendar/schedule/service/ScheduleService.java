package HK.PrettyWorks_BE.calendar.schedule.service;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleParticipantEntity;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
}
