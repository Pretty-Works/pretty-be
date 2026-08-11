package HK.PrettyWorks_BE.agent.interaction.preview;

import tools.jackson.databind.JsonNode;

public interface ApprovalPreviewRenderer {
    String tool();
    String render(JsonNode params);
}
