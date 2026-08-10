package HK.PrettyWorks_BE.agent.shared.limit;

public final class AgentPayloadLimits {
    public static final int MAX_EVENT_DATA_BYTES = 256 * 1024;
    public static final int MAX_STEP_DATA_BYTES = 4 * 1024;
    public static final int MAX_PARAMS_CANONICAL_BYTES = 64 * 1024;
    public static final int MAX_INTERACTION_RESPONSE_BYTES = 8 * 1024;
    public static final int MAX_STEPS_PER_RUN = 100;

    private AgentPayloadLimits() {
    }
}
