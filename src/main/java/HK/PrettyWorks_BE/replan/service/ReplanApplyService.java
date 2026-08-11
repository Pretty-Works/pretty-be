package HK.PrettyWorks_BE.replan.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneSummary;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import HK.PrettyWorks_BE.replan.constant.ReplanOperationType;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.domain.ReplanEntity;
import HK.PrettyWorks_BE.replan.dto.ReplanOperation;
import HK.PrettyWorks_BE.replan.dto.res.ReplanApplyResponse;
import HK.PrettyWorks_BE.replan.exception.ReplanErrorCode;
import HK.PrettyWorks_BE.replan.repository.ReplanRepository;
import HK.PrettyWorks_BE.task.domain.TaskEntity;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.exception.TaskErrorCode;
import HK.PrettyWorks_BE.task.policy.TaskPolicy;
import HK.PrettyWorks_BE.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 저장해 둔 시나리오를 실제 데이터에 반영한다.
 *
 * <p><b>요청은 replanId와 scenarioType만 받는다.</b> 무엇을 바꿀지는 저장된 계획에서 읽는다 —
 * 호출자가 변경 내용을 다시 실어 보내면, 승인 화면에 보여 준 계획과 다른 것을 실행할 길이 열린다.
 *
 * <p><b>변경은 전부 공개 서비스 메서드로 한다.</b> 프로젝트 기간·마일스톤·참여자는
 * {@code ProjectService.update} 한 번으로, 할 일은 {@code TaskService}의 update·delete·createBatch로 처리한다.
 * 재계획 전용 변경 로직은 하나도 두지 않는다 — 화면이 못 하는 일을 재계획이 할 수 있으면
 * 같은 조작의 결과가 경로에 따라 달라지고, 규칙이 두 벌이 되어 한쪽만 고쳐지는 순간 갈라진다.
 * 담당자를 넘기는 재배치도 화면과 똑같이 지우고 새로 만드는 방식으로 표현한다.
 *
 * <p>전체가 한 트랜잭션이다. 이 서비스를 여러 번 나눠 부르는 방식은 쓰지 않는다 —
 * 내부 도구는 호출 한 번이 트랜잭션 한 개라(AgentWriteExecutor), 나눠 부르면 절반만 반영된 상태가 남는다.
 *
 * <p>실행 순서는 요청이 아니라 {@link ReplanOperationType} 선언 순서가 정한다. 앞 단계의 변경이
 * 뒤 단계의 검증 근거가 되기 때문이다 — 프로젝트 기간을 먼저 넓혀야 할 일 마감일이 통과하고,
 * 참여자를 먼저 넣어야 그 사람에게 배정할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanApplyService {

    private final ReplanRepository replanRepository;
    private final ReplanService replanService;
    private final ReplanAccessGuard accessGuard;
    private final ProjectService projectService;
    private final TaskService taskService;

    @Transactional
    public ReplanApplyResponse apply(Long actorId, Long projectId, Long replanId,
                                     ReplanScenarioType scenarioType) {
        // 1) 적용 권한 (REPLAN_003). 재계획을 찾기 '전에' 본다 —
        //    나중에 보면 권한 없는 호출자에게도 "그 replanId가 있는지"가 응답 코드로 새어나간다.
        //    계획을 세운 시점이 아니라 지금 기준으로 판정한다. 그 사이 프로젝트를 나갔거나 퇴사했을 수 있다.
        accessGuard.validateManager(projectId, actorId);

        // 2) 재계획 조회 + 요청한 프로젝트의 것인지 (REPLAN_001)
        ReplanEntity replan = replanRepository.findById(replanId)
                .orElseThrow(() -> BaseException.type(ReplanErrorCode.REPLAN_NOT_FOUND));
        if (!projectId.equals(replan.getProjectId())) {
            throw BaseException.type(ReplanErrorCode.REPLAN_NOT_FOUND);
        }
        if (replan.isApplied()) {
            throw BaseException.type(ReplanErrorCode.ALREADY_APPLIED);
        }

        // 3) 선택한 시나리오의 변경 목록 (REPLAN_002). 읽기·파싱·소속 확인은 ReplanService가 소유한다 —
        //    승인 카드 렌더러도 같은 메서드를 쓰므로, 보여준 것과 실행되는 것이 같은 경로에서 나온다.
        Map<ReplanOperationType, List<ReplanOperation>> byType =
                replanService.loadOperations(projectId, replanId, scenarioType);

        // 4) 현재 상태를 읽는다. 뒤이은 변경 호출은 같은 트랜잭션이라 여기서 적재된 엔티티를 그대로 쓴다.
        //    프로젝트 상세는 프로젝트·마일스톤·참여자를 건드릴 때만 필요하다.
        Map<Long, TaskEntity> tasks = loadTargetTasks(actorId, projectId, byType);
        ProjectDetailResponse detail = touchesProject(byType)
                ? projectService.getDetail(actorId, projectId)
                : null;

        // 5) 계획 당시 값과 현재 값을 먼저 전부 대조한다 (REPLAN_004).
        //    한 건이라도 어긋나면 아무것도 바꾸지 않고 멈춘다 — 절반만 반영된 상태는
        //    사용자가 승인한 어느 시나리오와도 다른 결과다.
        //    실행 단계에서만 걸리는 조건(예: 삭제는 작성자만)도 여기서 미리 본다.
        verifyApplicable(actorId, byType, detail, tasks);

        // 6) 실행
        ReplanApplyResponse result =
                execute(actorId, projectId, replanId, scenarioType, byType, detail, tasks);

        // 7) 적용 사실만 기록한다. 계획 내용은 건드리지 않는다 —
        //    무엇을 승인했고 무엇이 실행됐는지 나중에도 대조할 수 있어야 한다.
        //    특히 할 일은 하드 삭제라, 사라진 이유가 남는 곳이 여기뿐이다.
        replan.markApplied(scenarioType, actorId, LocalDateTime.now());

        // 하드 삭제를 포함한 대량 변경이다. DB에는 '누가 언제 어느 시나리오를' 까지만 남으므로
        // 무엇이 몇 건 바뀌었는지는 여기서 남긴다. 요청 식별자는 MdcFilter가 붙인다.
        log.info("[재계획 적용] replanId={} scenario={} projectId={} 마일스톤={} 마감일={} 생성={} 삭제={} 참여자={}",
                replanId, scenarioType, projectId,
                result.milestoneDateChangedCount(), result.taskDueDateChangedCount(),
                result.taskCreatedCount(), result.taskDeletedCount(), result.memberAddedCount());

        return result;
    }

    // 프로젝트 수정 API를 타야 하는 변경이 있는지. 없으면 상세 조회 자체를 생략한다.
    private boolean touchesProject(Map<ReplanOperationType, List<ReplanOperation>> byType) {
        return !operationsOf(byType, ReplanOperationType.PROJECT_TARGET_DATE_CHANGE).isEmpty()
                || !operationsOf(byType, ReplanOperationType.MILESTONE_TARGET_DATE_CHANGE).isEmpty()
                || !operationsOf(byType, ReplanOperationType.PROJECT_MEMBER_ADD).isEmpty();
    }

    // 할 일 세 종류가 함께 오므로 id를 모아 한 번에 읽는다.
    // 소속(다른 프로젝트의 taskId)·수정 권한 확인은 loadEditableTasks가 담당한다.
    private Map<Long, TaskEntity> loadTargetTasks(Long actorId, Long projectId,
                                                  Map<ReplanOperationType, List<ReplanOperation>> byType) {
        Set<Long> taskIds = Stream.of(
                        operationsOf(byType, ReplanOperationType.TASK_DUE_DATE_CHANGE),
                        operationsOf(byType, ReplanOperationType.TASK_DELETE))
                .flatMap(List::stream)
                .map(ReplanOperation::taskId)
                .collect(Collectors.toSet());

        return taskService.loadEditableTasks(actorId, projectId, taskIds).stream()
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity()));
    }

    // ================================= 적용 가능 여부 검증 =================================

    private void verifyApplicable(Long actorId, Map<ReplanOperationType, List<ReplanOperation>> byType,
                                  ProjectDetailResponse detail, Map<Long, TaskEntity> tasks) {
        if (detail != null) {
            for (ReplanOperation operation : operationsOf(byType, ReplanOperationType.PROJECT_TARGET_DATE_CHANGE)) {
                requireUnchanged("프로젝트 목표일", operation.from(), detail.endDate());
            }

            Map<Long, LocalDate> current = detail.milestones().stream()
                    .collect(Collectors.toMap(MilestoneSummary::milestoneId, MilestoneSummary::targetDate));
            for (ReplanOperation operation : operationsOf(byType, ReplanOperationType.MILESTONE_TARGET_DATE_CHANGE)) {
                // 이 프로젝트에 없는 마일스톤은 '충돌'이 아니라 '잘못된 대상'이다.
                // 충돌로 응답하면 "재계획을 다시 만들라"고 안내하는데, 다시 만들어도 같은 id를 넣으면 또 실패한다.
                if (!current.containsKey(operation.milestoneId())) {
                    log.warn("[재계획 대상 오류] 이 프로젝트의 마일스톤이 아님 milestoneId={}", operation.milestoneId());
                    throw BaseException.type(ProjectErrorCode.MILESTONE_NOT_FOUND);
                }
                requireUnchanged("마일스톤 #" + operation.milestoneId() + " 목표일",
                        operation.from(), current.get(operation.milestoneId()));
            }
        }

        for (ReplanOperation operation : operationsOf(byType, ReplanOperationType.TASK_DUE_DATE_CHANGE)) {
            requireUnchanged("할 일 #" + operation.taskId() + " 마감일",
                    operation.from(), tasks.get(operation.taskId()).getDueDate());
        }

        for (ReplanOperation operation : operationsOf(byType, ReplanOperationType.TASK_DELETE)) {
            TaskEntity task = tasks.get(operation.taskId());

            // 삭제는 작성자만 가능하다(TaskPolicy.canDelete). 적재 기준인 canModify(담당자·작성자)보다 좁으므로
            // 여기서 미리 본다 — 실행까지 가면 앞의 변경을 전부 돌린 뒤 롤백된다.
            if (!TaskPolicy.canDelete(task, actorId)) {
                log.warn("[재계획 대상 오류] 작성자가 아니라 삭제할 수 없음 taskId={}", operation.taskId());
                throw BaseException.type(TaskErrorCode.NO_DELETE_PERMISSION);
            }

            // 되돌릴 수 없는 변경이다. 내용까지 대조해 "그때 그 할 일이 맞는지"를 확인한다.
            // 값은 로그에 남기지 않는다 — 할 일 내용에는 사람 이름이 섞이는 경우가 흔하다.
            requireUnchanged("할 일 #" + operation.taskId() + " 내용",
                    operation.expectedContent(), task.getContent());
        }
    }

    // 계획 당시 값과 현재 값이 같아야 한다. 다르면 그 사이 누가 먼저 바꾼 것이므로 덮어쓰지 않는다.
    // 할 일·마일스톤에는 낙관적 락(@Version)이 없어 이 대조가 유일한 방어다.
    //
    // 무엇이 어긋났는지는 응답에 담기지 않는다(REPLAN_004 한 줄). 그래서 로그에 남긴다 —
    // 이게 없으면 "재계획이 자꾸 실패한다"는 문의에 답할 근거가 없다.
    private void requireUnchanged(String what, Object expected, Object current) {
        if (!Objects.equals(expected, current)) {
            log.warn("[재계획 충돌] {}", what);
            throw BaseException.type(ReplanErrorCode.SCENARIO_CONFLICT);
        }
    }

    // ================================= 실행 =================================

    private ReplanApplyResponse execute(Long actorId, Long projectId, Long replanId,
                                        ReplanScenarioType scenarioType,
                                        Map<ReplanOperationType, List<ReplanOperation>> byType,
                                        ProjectDetailResponse detail, Map<Long, TaskEntity> tasks) {
        // 무엇을 바꿀지 먼저 전부 모은다. 같은 대상이 두 번 지정된 것 같은 구조 오류가
        // 실행 도중이 아니라 시작 전에 걸리게 하려는 것이다.
        LocalDate projectTargetDate = singleProjectTargetDate(byType);
        Map<Long, LocalDate> milestoneDates = dateMap(byType,
                ReplanOperationType.MILESTONE_TARGET_DATE_CHANGE, ReplanOperation::milestoneId);
        Map<Long, LocalDate> taskDueDates = dateMap(byType,
                ReplanOperationType.TASK_DUE_DATE_CHANGE, ReplanOperation::taskId);
        List<TaskRequest> newTasks = newTaskRequests(projectId, byType);
        List<Long> deleteTargets = deleteTargets(byType);

        // 지울 할 일의 마감일을 함께 옮기라는 계획은 받지 않는다. 실행은 되지만 담당자에게
        // "마감일이 변경되었습니다" 다음에 "삭제되었습니다"가 연달아 간다.
        if (deleteTargets.stream().anyMatch(taskDueDates::containsKey)) {
            throw invalidOperation("같은 할 일에 마감일 변경과 삭제가 함께 지정됨");
        }

        // ①②③ 프로젝트 기간 · 참여자 · 마일스톤 — 공개 수정 API 한 번으로 끝난다.
        //     기간 검증(PROJECT_003/021), 마일스톤 검증(PROJECT_015/016), 참여자 diff와 알림이 모두 그 안에 있다.
        //
        //     version에 방금 읽은 값을 넣는 것은 의도한 것이다. 수정 API의 사전 검사(VersionGuard)는
        //     "폼을 열어둔 사이 남이 저장했나"를 보는 화면용 장치인데, 재계획은 생성부터 적용까지 간격이 길어
        //     그 사이 누가 프로젝트를 한 번만 건드려도 막힌다. 그 역할은 위의 from 값 대조가 대신하고,
        //     커밋 시점 낙관적 락(findByIdWithOptimisticLock)은 그대로 살아 있어 동시 수정은 여전히 걸린다.
        int milestoneChanged = 0;
        int memberAdded = 0;
        if (detail != null) {
            milestoneChanged = countChangedMilestones(detail, milestoneDates);
            memberAdded = countNewMembers(detail, byType);
            projectService.update(actorId, projectId, detail.version(),
                    buildProjectRequest(detail, projectTargetDate, milestoneDates, byType));
        }

        // ④ 할 일 마감일. ①에서 넓힌 기간을 기준으로 판정된다(같은 영속성 컨텍스트).
        //    수정 API는 내용·소속까지 함께 받으므로 방금 읽은 현재 값을 그대로 되보낸다.
        int dueDateChanged = 0;
        for (Map.Entry<Long, LocalDate> entry : taskDueDates.entrySet()) {
            TaskEntity task = tasks.get(entry.getKey());
            if (entry.getValue().equals(task.getDueDate())) {
                continue;   // 이미 그 날짜면 건드리지 않는다. 알림도 나가지 않는다.
            }
            taskService.update(actorId, entry.getKey(), TaskRequest.builder()
                    .content(task.getContent())
                    .projectId(projectId)
                    .dueDate(entry.getValue())
                    .build());
            dueDateChanged++;
        }

        // ⑤ 생성. 건수 상한과 전부-또는-전무 저장은 createBatch가 담당한다.
        //    담당자를 넘기는 재배치는 여기 생성 + 아래 삭제 조합으로 들어온다(화면과 같은 방식).
        int created = newTasks.isEmpty() ? 0 : taskService.createBatch(actorId, newTasks).size();

        // ⑥ 삭제 — 마지막이다. 지운 뒤에 그 할 일을 건드리면 없는 대상이라 전체가 실패한다.
        for (Long taskId : deleteTargets) {
            taskService.delete(actorId, taskId);
        }

        return ReplanApplyResponse.builder()
                .replanId(replanId)
                .projectId(projectId)
                .scenarioType(scenarioType)
                .projectTargetDate(projectTargetDate)
                .milestoneDateChangedCount(milestoneChanged)
                .taskDueDateChangedCount(dueDateChanged)
                .taskCreatedCount(created)
                .taskDeletedCount(deleteTargets.size())
                .memberAddedCount(memberAdded)
                .build();
    }

    /**
     * 현재 값을 그대로 옮긴 뒤 바꿀 것만 덮어쓴다. 프로젝트 수정 API가 전체 교체이기 때문이다.
     *
     * <p>전체를 옮기는 일은 {@code ProjectRequest.from}이 소유한다 — 그 record에 필드가 늘어날 때
     * 같은 파일에서 보이도록 거기에 뒀다. 여기서는 재계획이 실제로 바꾸는 셋만 덮어쓴다.
     */
    private ProjectRequest buildProjectRequest(ProjectDetailResponse detail, LocalDate newTargetDate,
                                               Map<Long, LocalDate> milestoneTargetDates,
                                               Map<ReplanOperationType, List<ReplanOperation>> byType) {
        return ProjectRequest.from(detail)
                .endDate(newTargetDate == null ? detail.endDate() : newTargetDate)
                .members(membersWithAdditions(detail, byType))
                .milestones(milestonesWithNewDates(detail, milestoneTargetDates))
                .build();
    }

    // 지금 참여중인 사람을 그대로 유지하고 추가분만 얹는다.
    // 목록에서 빠뜨리면 수정 API의 diff가 그 사람을 내보낸다(soft delete + 제외 알림).
    private List<ProjectRequest.MemberRequest> membersWithAdditions(
            ProjectDetailResponse detail, Map<ReplanOperationType, List<ReplanOperation>> byType) {
        List<ProjectRequest.MemberRequest> members = new ArrayList<>();
        Set<Long> present = new LinkedHashSet<>();
        for (ProjectDetailResponse.Member member : detail.members()) {
            members.add(ProjectRequest.MemberRequest.builder()
                    .userId(member.userId())
                    .role(member.role())
                    .build());
            present.add(member.userId());
        }

        for (ReplanOperation operation : operationsOf(byType, ReplanOperationType.PROJECT_MEMBER_ADD)) {
            // 이미 참여중이면 넣지 않는다. 넣어도 diff가 걸러내지만 역할이 덮어쓰인다.
            // 오너를 지정한 경우는 수정 API가 참여자 목록에서 제외한다(collectParticipants).
            if (present.add(operation.memberId())) {
                members.add(ProjectRequest.MemberRequest.builder()
                        .userId(operation.memberId())
                        .role(operation.role())
                        .build());
            }
        }
        return members;
    }

    // 목표일만 갈아끼운다. milestoneId는 그대로 실어야 완료 상태가 보존되고,
    // 요청에서 빠진 마일스톤은 삭제되므로 바꾸지 않는 것까지 전부 담는다.
    private List<ProjectRequest.MilestoneRequest> milestonesWithNewDates(
            ProjectDetailResponse detail, Map<Long, LocalDate> targetDates) {
        return detail.milestones().stream()
                .map(milestone -> ProjectRequest.MilestoneRequest.builder()
                        .milestoneId(milestone.milestoneId())
                        .targetDate(targetDates.getOrDefault(
                                milestone.milestoneId(), milestone.targetDate()))
                        .goal(milestone.goal())
                        .build())
                .toList();
    }

    // ================================= 내부 헬퍼 =================================

    // 요청한 마일스톤 중 실제로 날짜가 달라지는 건수. 이미 그 날짜면 세지 않는다 —
    // 호출자가 요청 건수로 답하면 "2건 조정했습니다"가 사실과 어긋난다.
    private int countChangedMilestones(ProjectDetailResponse detail, Map<Long, LocalDate> targetDates) {
        Map<Long, LocalDate> current = detail.milestones().stream()
                .collect(Collectors.toMap(MilestoneSummary::milestoneId, MilestoneSummary::targetDate));
        return (int) targetDates.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(current.get(entry.getKey())))
                .count();
    }

    // 이미 참여중인 사람은 세지 않는다. 나갔던(LEFT) 사람은 상세 조회에 없으므로 추가로 잡힌다 —
    // 실제로 수정 API가 재활성화하므로 그게 맞다.
    private int countNewMembers(ProjectDetailResponse detail,
                                Map<ReplanOperationType, List<ReplanOperation>> byType) {
        Set<Long> present = detail.members().stream()
                .map(ProjectDetailResponse.Member::userId)
                .collect(Collectors.toSet());
        return (int) operationsOf(byType, ReplanOperationType.PROJECT_MEMBER_ADD).stream()
                .map(ReplanOperation::memberId)
                .distinct()
                .filter(memberId -> !present.contains(memberId)
                        && !memberId.equals(detail.owner().userId()))
                .count();
    }

    private List<ReplanOperation> operationsOf(Map<ReplanOperationType, List<ReplanOperation>> byType,
                                               ReplanOperationType type) {
        return byType.getOrDefault(type, List.of());
    }

    // 프로젝트 목표일은 한 시나리오에서 한 번만 바꿀 수 있다. 두 건이 오면 어느 쪽이 최종인지 알 수 없고,
    // 사용자가 승인 화면에서 본 날짜와 다른 값이 남을 수 있다.
    private LocalDate singleProjectTargetDate(Map<ReplanOperationType, List<ReplanOperation>> byType) {
        List<ReplanOperation> operations = operationsOf(byType, ReplanOperationType.PROJECT_TARGET_DATE_CHANGE);
        if (operations.isEmpty()) {
            return null;
        }
        if (operations.size() > 1) {
            throw invalidOperation("프로젝트 목표일 변경이 " + operations.size() + "건 지정됨");
        }
        return operations.get(0).to();
    }

    // 같은 대상이 두 번 오면 거부한다. 뒤엣것으로 덮으면 승인 화면에서 본 것과 다른 값이 적용될 수 있다.
    private Map<Long, LocalDate> dateMap(Map<ReplanOperationType, List<ReplanOperation>> byType,
                                         ReplanOperationType type,
                                         Function<ReplanOperation, Long> keyOf) {
        Map<Long, LocalDate> result = new LinkedHashMap<>();
        for (ReplanOperation operation : operationsOf(byType, type)) {
            if (result.put(keyOf.apply(operation), operation.to()) != null) {
                throw invalidOperation(type + " 에 같은 대상이 두 번 지정됨: " + keyOf.apply(operation));
            }
        }
        return result;
    }

    // 담당자를 비우면 재계획을 적용한 사람이 담당한다(TaskService.resolveAssignee).
    private List<TaskRequest> newTaskRequests(Long projectId,
                                              Map<ReplanOperationType, List<ReplanOperation>> byType) {
        return operationsOf(byType, ReplanOperationType.TASK_CREATE).stream()
                .map(operation -> TaskRequest.builder()
                        .projectId(projectId)
                        .content(operation.content())
                        .assigneeId(operation.toAssigneeId())
                        .dueDate(operation.to())
                        .build())
                .toList();
    }

    private List<Long> deleteTargets(Map<ReplanOperationType, List<ReplanOperation>> byType) {
        List<Long> taskIds = operationsOf(byType, ReplanOperationType.TASK_DELETE).stream()
                .map(ReplanOperation::taskId)
                .toList();
        if (Set.copyOf(taskIds).size() != taskIds.size()) {
            throw invalidOperation("TASK_DELETE 에 같은 할 일이 두 번 지정됨");
        }
        return taskIds;
    }

    // REPLAN_005는 여러 상황을 함께 덮는다(필수값 누락·중복 지정·JSON 손상 등).
    // 응답 메시지 하나로는 무엇이 잘못됐는지 알 수 없으므로 사유를 로그로 남긴다.
    private BaseException invalidOperation(String reason) {
        log.warn("[재계획 항목 오류] {}", reason);
        return BaseException.type(ReplanErrorCode.INVALID_OPERATION);
    }
}
