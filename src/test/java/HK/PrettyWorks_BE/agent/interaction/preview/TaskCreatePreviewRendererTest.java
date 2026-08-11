package HK.PrettyWorks_BE.agent.interaction.preview;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.UserService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskCreatePreviewRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService = mock(UserService.class);
    private final TaskCreatePreviewRenderer renderer = new TaskCreatePreviewRenderer(userService);

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

    // 승인 카드는 "누구에게 배정되는지"를 사용자가 볼 마지막 기회다. id만 적히면 누구인지 모른 채 승인하게 된다.
    @Test
    void showsAssigneeNameSoTheApproverKnowsWhoGetsTheWork() {
        when(userService.getNameMap(any())).thenReturn(Map.of(15L, "이영희"));
        var params = objectMapper.readTree("""
                {"tasks": [
                  {"content":"API 명세 정리","projectId":7,"assigneeId":15,"dueDate":"2026-08-07"}
                ]}
                """);

        assertThat(renderer.render(params)).contains("담당 이영희");
    }

    // 담당자를 비우면 요청자 본인이 담당한다. 모든 줄에 자기 이름이 붙으면 정작 남에게 배정한 줄이 묻힌다.
    @Test
    void omitsAssigneeLabelWhenTheRequesterKeepsTheWork() {
        var params = objectMapper.readTree("""
                {"tasks": [
                  {"content":"회고 작성","projectId":null,"dueDate":"2026-08-08"}
                ]}
                """);

        assertThat(renderer.render(params)).doesNotContain("담당");
        verify(userService, never()).getNameMap(any());
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
