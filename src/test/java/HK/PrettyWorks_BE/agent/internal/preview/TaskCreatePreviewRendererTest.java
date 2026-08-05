package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskCreatePreviewRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskCreatePreviewRenderer renderer = new TaskCreatePreviewRenderer();

    @Test
    void rendersEveryTaskInOneApprovalPreview() {
        var params = objectMapper.readTree("""
                {"tasks": [
                  {"content":"API 명세 정리","projectId":7,"dueDate":"2026-08-07"},
                  {"content":"회고 작성","projectId":null,"dueDate":"2026-08-08"}
                ]}
                """);

        String preview = renderer.render(params);

        assertThat(preview)
                .contains("할 일 2건")
                .contains("API 명세 정리 · 2026-08-07 · 프로젝트 #7")
                .contains("회고 작성 · 2026-08-08 · 개인");
    }

    @Test
    void rejectsAnEmptyBatchBeforeShowingApproval() {
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("{\"tasks\":[]}")))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void rejectsABareArrayLeftOverFromTheOldSchema() {
        // 예전 스키마(최상위 배열)로 승인 카드를 그리려 하면 실패해야 한다.
        // 조용히 통과하면 사용자가 빈 카드를 승인하게 된다.
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                [{"content":"API 명세 정리","projectId":7,"dueDate":"2026-08-07"}]
                """)))
                .isInstanceOf(BaseException.class);
    }
}
