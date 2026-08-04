package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ApprovalPreviewRegistry {
    private final Map<String, ApprovalPreviewRenderer> renderers;

    public ApprovalPreviewRegistry(List<ApprovalPreviewRenderer> renderers) {
        this.renderers = renderers.stream().collect(Collectors.toUnmodifiableMap(
                ApprovalPreviewRenderer::tool, Function.identity()));
    }

    public String render(String tool, JsonNode params) {
        ApprovalPreviewRenderer renderer = renderers.get(tool);
        if (renderer == null) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return renderer.render(params);
    }
}
