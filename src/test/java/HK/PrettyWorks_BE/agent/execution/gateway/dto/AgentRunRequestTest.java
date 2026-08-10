package HK.PrettyWorks_BE.agent.execution.gateway.dto;

import HK.PrettyWorks_BE.agent.shared.attachment.AgentFileEncoding;
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
                1L,
                "이번 주 할 일을 등록해줘",
                List.of(new AgentRunRequest.ContextMessage("USER", "이전 요청")),
                objectMapper.readTree("{\"screen\":\"TASK_LIST\",\"formState\":{}}"),
                "WEB",
                "ko-KR",
                List.of()
        );

        assertThat(request.runId()).isEqualTo("run_public_1");
        assertThat(request.messages()).hasSize(1);
        assertThat(request.screenContext().get("screen").textValue()).isEqualTo("TASK_LIST");
    }

    @Test
    void requiresScreenContextWithNonBlankScreen() {
        assertThatThrownBy(() -> new AgentRunRequest(
                "run_public_1", 1L, "질문", List.of(), objectMapper.readTree("{}"),
                "WEB", "ko-KR", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("screenContext.screen");
    }

    // 첨부는 없는 것이 정상이라 null을 빈 배열로 받아 준다.
    // 첨부가 있느냐 없느냐로 FastAPI 쪽 분기가 갈리면 안 되므로 항상 배열이 나가야 한다.
    @Test
    void treatsMissingFilesAsEmptyList() {
        AgentRunRequest request = new AgentRunRequest(
                "run_public_1", 1L, "질문", List.of(),
                objectMapper.readTree("{\"screen\":\"HOME\"}"), "WEB", "ko-KR", null);

        assertThat(request.files()).isEmpty();
    }

    @Test
    void carriesAttachedFileAsIs() {
        AgentRunRequest.AttachedFile file = new AgentRunRequest.AttachedFile(
                "회의록.txt", "text/plain", 12L, AgentFileEncoding.TEXT, "회의 내용입니다");

        AgentRunRequest request = new AgentRunRequest(
                "run_public_1", 1L, "요약해줘", List.of(),
                objectMapper.readTree("{\"screen\":\"HOME\"}"), "WEB", "ko-KR", List.of(file));

        assertThat(request.files()).containsExactly(file);
        assertThat(request.files().getFirst().content()).isEqualTo("회의 내용입니다");
    }

    @Test
    void rejectsAttachedFileWithoutName() {
        assertThatThrownBy(() -> new AgentRunRequest.AttachedFile(
                " ", "text/plain", 12L, AgentFileEncoding.TEXT, "내용"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("files.filename");
    }

    @Test
    void snapshotsMutableInputs() {
        var screenContext = objectMapper.readTree("{\"screen\":\"HOME\"}");
        AgentRunRequest request = new AgentRunRequest(
                "run_public_1", 1L, "질문", List.of(), screenContext, "WEB", "ko-KR", List.of());

        ((tools.jackson.databind.node.ObjectNode) screenContext).put("screen", "PROJECT_LIST");
        ((tools.jackson.databind.node.ObjectNode) request.screenContext()).put("screen", "TASK_LIST");

        assertThat(request.screenContext().get("screen").textValue()).isEqualTo("HOME");
        assertThatThrownBy(() -> request.messages().add(
                new AgentRunRequest.ContextMessage("USER", "변경")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
