package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.internal.dto.req.AgentReplanApplyRequest;
import HK.PrettyWorks_BE.agent.internal.dto.req.AgentReplanCreateRequest;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentWriteResults;
import HK.PrettyWorks_BE.replan.dto.res.ReplanApplyResponse;
import HK.PrettyWorks_BE.replan.dto.res.ReplanCreateResponse;
import HK.PrettyWorks_BE.replan.service.ReplanApplyService;
import HK.PrettyWorks_BE.replan.service.ReplanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// replan.create · replan.apply.
//
// 검증·권한 판정·실행 순서는 전부 replan 도메인이 갖는다. 여기서는 요청을 도메인 DTO로 옮기고
// 결과를 에이전트가 읽기 좋은 모양으로 좁힌다.
@Service
@RequiredArgsConstructor
public class AgentReplanToolService {

    private final ReplanService replanService;
    private final ReplanApplyService replanApplyService;

    public AgentWriteResults.ReplanCreated create(Long userId, AgentReplanCreateRequest request) {
        ReplanCreateResponse saved =
                replanService.create(userId, request.projectId(), request.toDomain());

        return AgentWriteResults.ReplanCreated.builder()
                .replanId(saved.replanId())
                .projectId(saved.projectId())
                .scenarios(saved.scenarios().stream()
                        .map(scenario -> AgentWriteResults.ReplanCreated.ReplanScenario.builder()
                                .scenarioType(scenario.scenarioType().name())
                                .scenarioLabel(scenario.scenarioType().getDescription())
                                .summary(scenario.summary())
                                .risk(scenario.risk().name())
                                .operationCount(scenario.operationCount())
                                .build())
                        .toList())
                .build();
    }

    public AgentWriteResults.ReplanApplied apply(Long userId, AgentReplanApplyRequest request) {
        ReplanApplyResponse applied = replanApplyService.apply(
                userId, request.projectId(), request.replanId(), request.scenarioType());

        return AgentWriteResults.ReplanApplied.builder()
                .replanId(applied.replanId())
                .projectId(applied.projectId())
                .scenarioType(applied.scenarioType().name())
                .scenarioLabel(applied.scenarioType().getDescription())
                .projectTargetDate(applied.projectTargetDate())
                .milestoneDateChangedCount(applied.milestoneDateChangedCount())
                .taskDueDateChangedCount(applied.taskDueDateChangedCount())
                .taskCreatedCount(applied.taskCreatedCount())
                .taskDeletedCount(applied.taskDeletedCount())
                .memberAddedCount(applied.memberAddedCount())
                .build();
    }
}
