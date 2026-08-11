package HK.PrettyWorks_BE.agent.tool.security;

public final class InternalAgentAttributes {
    public static final String USER_ID = InternalAgentAttributes.class.getName() + ".userId";
    public static final String INTERNAL_RUN_ID = InternalAgentAttributes.class.getName() + ".internalRunId";
    public static final String PUBLIC_RUN_ID = InternalAgentAttributes.class.getName() + ".publicRunId";
    public static final String TOKEN_INSPECTION = InternalAgentAttributes.class.getName() + ".tokenInspection";
    public static final String CACHED_REQUEST = InternalAgentAttributes.class.getName() + ".cachedRequest";

    private InternalAgentAttributes() {
    }
}
