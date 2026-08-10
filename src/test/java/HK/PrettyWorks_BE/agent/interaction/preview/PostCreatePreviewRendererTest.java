package HK.PrettyWorks_BE.agent.interaction.preview;

import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostCreatePreviewRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PostCreatePreviewRenderer renderer = new PostCreatePreviewRenderer();

    @Test
    void showsWhatWillBePostedNotJustTheTitle() {
        var params = objectMapper.readTree("""
                {"projectId":7,"title":"배포 일정 공지","priority":"HIGH",
                 "content":"8월 10일 배포 예정입니다."}
                """);

        String preview = renderer.render(params);

        assertThat(preview)
                .contains("제목: 배포 일정 공지")
                .contains("중요도: HIGH")
                .contains("프로젝트 #7")
                .contains("내용: 8월 10일 배포 예정입니다.");
    }

    @Test
    void keepsALongBodyFromSwallowingTheCard() {
        String body = "가".repeat(500);
        var params = objectMapper.readTree(objectMapper.writeValueAsString(
                java.util.Map.of("projectId", 7, "title", "공지", "priority", "LOW", "content", body)));

        String preview = renderer.render(params);

        assertThat(preview).contains("…");
        assertThat(preview.length()).isLessThan(400);
    }

    @Test
    void flattensLineBreaksSoTheFieldListStaysReadable() {
        var params = objectMapper.readTree("""
                {"projectId":7,"title":"공지","priority":"MID","content":"첫 줄\\n- 둘째 줄"}
                """);

        // 본문의 줄바꿈이 남으면 "- 둘째 줄"이 카드의 별도 항목처럼 보인다.
        assertThat(renderer.render(params)).endsWith("- 내용: 첫 줄 - 둘째 줄");
    }

    @Test
    void refusesToDrawACardWithoutABody() {
        // 본문 없이 승인 카드를 그리면 사용자가 무엇이 올라가는지 모른 채 누르게 된다.
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"projectId":7,"title":"공지","priority":"HIGH","content":"   "}
                """)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void refusesToDrawACardWithoutAProjectTarget() {
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"title":"공지","priority":"HIGH","content":"내용"}
                """)))
                .isInstanceOf(BaseException.class);
    }
}
