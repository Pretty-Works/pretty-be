package HK.PrettyWorks_BE.agent.execution.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AgentRunStatus {
    RUNNING,
    WAITING_APPROVAL,
    WAITING_INPUT,
    COMPLETED,
    FAILED,
    EXPIRED;

    private static final Set<AgentRunStatus> ACTIVE =
            EnumSet.of(RUNNING, WAITING_APPROVAL, WAITING_INPUT);
    private static final Set<AgentRunStatus> TERMINAL =
            EnumSet.of(COMPLETED, FAILED, EXPIRED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public static Set<AgentRunStatus> activeStatuses() {
        return EnumSet.copyOf(ACTIVE);
    }

    public static Set<AgentRunStatus> terminalStatuses() {
        return EnumSet.copyOf(TERMINAL);
    }
}
