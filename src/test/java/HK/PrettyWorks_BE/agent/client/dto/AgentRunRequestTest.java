package HK.PrettyWorks_BE.agent.client.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsRunScopedRequestWithoutUserOrConversationIdentity() {
        AgentRunRequest request = new AgentRunRequest(
                "run_public_1",
                "이번 주 할 일을 등록해줘",
                List.of(new AgentRunRequest.ContextMessage("USER", "이전 요청")),
                objectMapper.readTree("{\"screen\":\"TASK_LIST\",\"formState\":{}}"),
                "WEB",
                "ko-KR"
        );

        assertThat(request.runId()).isEqualTo("run_public_1");
        assertThat(request.messages()).hasSize(1);
        assertThat(request.screenContext().get("screen").textValue()).isEqualTo("TASK_LIST");
    }

    @Test
    void requiresScreenContextWithNonBlankScreen() {
        assertThatThrownBy(() -> new AgentRunRequest(
                "run_public_1", "질문", List.of(), objectMapper.readTree("{}"), "WEB", "ko-KR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("screenContext.screen");
    }

    @Test
    void snapshotsMutableInputs() {
        var screenContext = objectMapper.readTree("{\"screen\":\"HOME\"}");
        AgentRunRequest request = new AgentRunRequest(
                "run_public_1", "질문", List.of(), screenContext, "WEB", "ko-KR");

        ((tools.jackson.databind.node.ObjectNode) screenContext).put("screen", "PROJECT_LIST");
        ((tools.jackson.databind.node.ObjectNode) request.screenContext()).put("screen", "TASK_LIST");

        assertThat(request.screenContext().get("screen").textValue()).isEqualTo("HOME");
        assertThatThrownBy(() -> request.messages().add(
                new AgentRunRequest.ContextMessage("USER", "변경")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
