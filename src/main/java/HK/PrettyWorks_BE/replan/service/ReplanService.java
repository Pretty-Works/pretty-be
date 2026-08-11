package HK.PrettyWorks_BE.replan.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import HK.PrettyWorks_BE.replan.constant.ReplanOperationType;
import HK.PrettyWorks_BE.replan.constant.ReplanPolicy;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.domain.ReplanEntity;
import HK.PrettyWorks_BE.replan.domain.ReplanScenarioEntity;
import HK.PrettyWorks_BE.replan.dto.ReplanOperation;
import HK.PrettyWorks_BE.replan.dto.req.ReplanCreateRequest;
import HK.PrettyWorks_BE.replan.dto.req.ReplanCreateRequest.ScenarioRequest;
import HK.PrettyWorks_BE.replan.dto.res.ReplanCreateResponse;
import HK.PrettyWorks_BE.replan.exception.ReplanErrorCode;
import HK.PrettyWorks_BE.replan.policy.ReplanOperationPolicy;
import HK.PrettyWorks_BE.replan.repository.ReplanRepository;
import HK.PrettyWorks_BE.replan.repository.ReplanScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 재계획 저장. 이 단계에서 프로젝트 데이터는 하나도 바뀌지 않는다 — 선택지를 만들어 둘 뿐이다.
//
// 저장된 시나리오는 이후 절대 수정하지 않는다(ReplanScenarioEntity 주석 참고).
// 계획이 달라져야 하면 새 재계획을 만든다. 그래서 이 서비스에는 update가 없다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanService {

    private final ReplanRepository replanRepository;
    private final ReplanScenarioRepository scenarioRepository;
    private final ReplanAccessGuard accessGuard;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReplanCreateResponse create(Long actorId, Long projectId, ReplanCreateRequest request) {
        accessGuard.validateManager(projectId, actorId);

        // 완료·보관된 프로젝트에는 계획을 세워 둘 수 없다 (PROJECT_020).
        // 적용 단계에서도 어차피 막히지만, 그때는 사용자가 승인까지 마친 뒤라 헛수고가 된다.
        if (!ProjectPolicy.isOpenForContent(projectService.getDetail(actorId, projectId).status())) {
            throw BaseException.type(ProjectErrorCode.PROJECT_CLOSED);
        }

        List<ScenarioRequest> scenarios = request.scenarios();
        validateDistinctTypes(scenarios);
        validateOperations(scenarios);

        ReplanEntity replan = replanRepository.save(ReplanEntity.builder()
                .projectId(projectId)
                .createdBy(actorId)
                .reason(request.reason())
                .build());

        List<ReplanScenarioEntity> entities = scenarios.stream()
                .map(scenario -> ReplanScenarioEntity.builder()
                        .replanId(replan.getId())
                        .scenarioType(scenario.scenarioType())
                        .summary(scenario.summary())
                        .risk(scenario.risk())
                        // 변경 목록은 통째로 JSON 한 덩어리로 보관한다. 적용 때 되읽어 그대로 실행하므로
                        // 저장한 순간의 계획이 글자 단위로 남는다.
                        .operations(objectMapper.writeValueAsString(scenario.operations()))
                        .build())
                .toList();
        scenarioRepository.saveAll(entities);

        List<ReplanCreateResponse.Scenario> saved = new ArrayList<>();
        for (ScenarioRequest scenario : scenarios) {
            saved.add(ReplanCreateResponse.Scenario.builder()
                    .scenarioType(scenario.scenarioType())
                    .summary(scenario.summary())
                    .risk(scenario.risk())
                    .operationCount(scenario.operations().size())
                    .build());
        }

        return ReplanCreateResponse.builder()
                .replanId(replan.getId())
                .projectId(projectId)
                .scenarios(saved)
                .build();
    }

    /**
     * 저장된 시나리오의 변경 목록을 종류별로 묶어 돌려준다. 적용 실행과 승인 카드 렌더링이 함께 쓴다.
     *
     * <p>요청이 가리킨 프로젝트의 재계획이 맞는지 확인한다. 승인 카드는 실행 '전에' 그려지므로,
     * 여기서 걸러내지 않으면 실행이 막히더라도 남의 프로젝트 계획 내용은 이미 화면에 나간 뒤다.
     *
     * <p>보관된 JSON을 읽지 못하거나 값이 빠져 있으면 실패시킨다 — 무엇이 바뀌는지 못 보여 주는 승인은
     * 승인이 아니고, 낡은 계획을 추측으로 실행해서도 안 된다.
     */
    @Transactional(readOnly = true)
    public Map<ReplanOperationType, List<ReplanOperation>> loadOperations(
            Long projectId, Long replanId, ReplanScenarioType scenarioType) {
        ReplanEntity replan = replanRepository.findById(replanId)
                .orElseThrow(() -> BaseException.type(ReplanErrorCode.REPLAN_NOT_FOUND));
        if (!projectId.equals(replan.getProjectId())) {
            log.warn("[재계획 대상 오류] 이 프로젝트의 재계획이 아님 replanId={} projectId={}", replanId, projectId);
            throw BaseException.type(ReplanErrorCode.REPLAN_NOT_FOUND);
        }

        ReplanScenarioEntity scenario = scenarioRepository
                .findByReplanIdAndScenarioType(replanId, scenarioType)
                .orElseThrow(() -> BaseException.type(ReplanErrorCode.SCENARIO_NOT_FOUND));

        return groupByType(parseOperations(scenario));
    }

    // ================================= 내부 헬퍼 =================================

    private List<ReplanOperation> parseOperations(ReplanScenarioEntity scenario) {
        final List<ReplanOperation> operations;
        try {
            operations = objectMapper.readValue(scenario.getOperations(),
                    new TypeReference<List<ReplanOperation>>() {
                    });
        } catch (RuntimeException unreadable) {
            // 보관된 JSON을 지금 코드로 읽을 수 없다 — 스키마가 바뀌었거나 손상됐다.
            throw invalidOperation("보관된 operations JSON을 읽을 수 없음 scenarioId=" + scenario.getId());
        }

        if (operations.isEmpty() || operations.size() > ReplanPolicy.MAX_OPERATIONS) {
            throw invalidOperation("변경 항목 수가 범위를 벗어남: " + operations.size());
        }
        for (ReplanOperation operation : operations) {
            // 저장할 때도 확인했지만 다시 본다. 그때 통과한 규칙이 이후 코드 변경으로 달라졌을 수 있다.
            if (!ReplanOperationPolicy.isComplete(operation)) {
                throw invalidOperation("필수 값이 빠진 항목: " + operation.operation());
            }
        }
        return operations;
    }

    private Map<ReplanOperationType, List<ReplanOperation>> groupByType(List<ReplanOperation> operations) {
        return operations.stream().collect(Collectors.groupingBy(
                ReplanOperation::operation,
                () -> new EnumMap<>(ReplanOperationType.class),
                Collectors.toList()));
    }

    // REPLAN_005는 여러 상황을 함께 덮는다. 응답 메시지 하나로는 알 수 없으므로 사유를 로그로 남긴다.
    private BaseException invalidOperation(String reason) {
        log.warn("[재계획 항목 오류] {}", reason);
        return BaseException.type(ReplanErrorCode.INVALID_OPERATION);
    }

    // 같은 종류를 두 번 제시할 수 없다. 적용은 (replanId, scenarioType)으로 한 건을 찾는데,
    // 둘이면 사용자가 고른 것과 다른 쪽이 실행될 수 있다. DB의 UNIQUE 제약과 같은 규칙을 여기서 먼저 알린다.
    private void validateDistinctTypes(List<ScenarioRequest> scenarios) {
        Set<ReplanScenarioType> seen = EnumSet.noneOf(ReplanScenarioType.class);
        for (ScenarioRequest scenario : scenarios) {
            if (!seen.add(scenario.scenarioType())) {
                log.warn("[재계획 저장 오류] 같은 시나리오 종류가 두 번 제시됨: {}", scenario.scenarioType());
                throw BaseException.type(ReplanErrorCode.INVALID_OPERATION);
            }
        }
    }

    // 종류별 필수 값을 저장 시점에 확인한다. 값이 빠진 계획을 저장해 두면 사용자가 승인까지 마친 뒤
    // 적용 단계에서 실패하는데, 그때는 이미 다른 시나리오를 고를 기회가 지나 있다.
    //
    // 대상 id가 이 프로젝트 소속인지는 여기서 보지 않는다 — 저장 후 적용까지 사이에 바뀔 수 있어
    // 적용 직전 확인이 어차피 필요하고, 그쪽이 진짜 방어선이다.
    private void validateOperations(List<ScenarioRequest> scenarios) {
        for (ScenarioRequest scenario : scenarios) {
            for (ReplanOperation operation : scenario.operations()) {
                if (!ReplanOperationPolicy.isComplete(operation)) {
                    log.warn("[재계획 저장 오류] 필수 값이 빠진 항목: scenario={} operation={}",
                            scenario.scenarioType(), operation.operation());
                    throw BaseException.type(ReplanErrorCode.INVALID_OPERATION);
                }
            }
        }
    }
}
