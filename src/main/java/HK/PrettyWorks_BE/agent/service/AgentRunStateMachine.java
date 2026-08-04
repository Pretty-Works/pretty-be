package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class AgentRunStateMachine {
    public static final int MAX_TOOL_CALLS = 20;
    public static final int MAX_QUESTIONS = 5;

    private static final Map<AgentRunStatus, Set<AgentRunStatus>> ALLOWED = Map.of(
            AgentRunStatus.RUNNING, EnumSet.of(AgentRunStatus.WAITING_APPROVAL,
                    AgentRunStatus.WAITING_INPUT, AgentRunStatus.COMPLETED,
                    AgentRunStatus.FAILED, AgentRunStatus.EXPIRED),
            AgentRunStatus.WAITING_APPROVAL, EnumSet.of(AgentRunStatus.RUNNING,
                    AgentRunStatus.FAILED, AgentRunStatus.EXPIRED),
            AgentRunStatus.WAITING_INPUT, EnumSet.of(AgentRunStatus.RUNNING,
                    AgentRunStatus.FAILED, AgentRunStatus.EXPIRED)
    );

    private final AgentRunRepository runRepository;
    private final TransactionTemplate transactionTemplate;

    public AgentRunStateMachine(AgentRunRepository runRepository,
                                PlatformTransactionManager transactionManager) {
        this.runRepository = runRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 이미 잠근 RUNNING Run에서 질문 예산을 원자적으로 소비합니다. 한도면 Run을 FAILED로
     * 바꾸되 예외를 던지지 않아, 호출자가 같은 트랜잭션에서 error 이벤트까지 저장할 수 있게 합니다.
     */
    public QuestionRegistration registerQuestionOrFail(AgentRunEntity lockedRun) {
        requireRunning(lockedRun);
        LocalDateTime now = LocalDateTime.now();
        if (lockedRun.getQuestionCount() >= MAX_QUESTIONS) {
            lockedRun.transition(AgentRunStatus.FAILED,
                    AgentErrorCode.TOO_MANY_QUESTIONS.getErrorCode(), now);
            return QuestionRegistration.LIMIT_EXCEEDED;
        }
        lockedRun.registerQuestion(now);
        return QuestionRegistration.REGISTERED;
    }

    /** 잠긴 엔티티의 상태를 현재 트랜잭션 안에서만 변경합니다. */
    public boolean transitionLocked(AgentRunEntity lockedRun,
                                    Collection<AgentRunStatus> expected,
                                    AgentRunStatus next, String errorCode) {
        if (expected.stream().anyMatch(current -> !isAllowed(current, next))) {
            throw new IllegalArgumentException("허용되지 않은 에이전트 실행 상태 전이입니다.");
        }
        if (!expected.contains(lockedRun.getStatus())) {
            return false;
        }
        lockedRun.transition(next, errorCode, LocalDateTime.now());
        return true;
    }

    public enum QuestionRegistration {
        REGISTERED,
        LIMIT_EXCEEDED
    }

    public boolean transition(Long internalRunId, Collection<AgentRunStatus> expected,
                              AgentRunStatus next, String errorCode) {
        if (expected.stream().anyMatch(current -> !isAllowed(current, next))) {
            throw new IllegalArgumentException("허용되지 않은 에이전트 실행 상태 전이입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime finishedAt = next.isTerminal() ? now : null;
        Integer updated = transactionTemplate.execute(status -> runRepository.transition(
                internalRunId, expected, next, errorCode, now, finishedAt));
        return updated != null && updated == 1;
    }

    private AgentRunEntity lockedRun(Long internalRunId) {
        return runRepository.findByIdForUpdate(internalRunId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
    }

    private void requireRunning(AgentRunEntity run) {
        if (run.getStatus() != AgentRunStatus.RUNNING) {
            throw BaseException.type(AgentErrorCode.RUN_NOT_FOUND);
        }
    }

    private boolean isAllowed(AgentRunStatus current, AgentRunStatus next) {
        return ALLOWED.getOrDefault(current, Set.of()).contains(next);
    }
}
