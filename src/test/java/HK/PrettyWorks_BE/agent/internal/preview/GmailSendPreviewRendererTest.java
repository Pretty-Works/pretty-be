package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailSendPreviewRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GmailSendPreviewRenderer renderer = new GmailSendPreviewRenderer();

    @Test
    void showsEveryRecipientBecauseASentMailCannotBeTakenBack() {
        var params = objectMapper.readTree("""
                {"to":["hong@pretty.works","kim@pretty.works","lee@pretty.works"],
                 "subject":"8월 배포 일정","body":"8월 10일 배포 예정입니다."}
                """);

        String preview = renderer.render(params);

        // 개수로 줄이면("3명에게") 엉뚱한 주소가 섞여도 사용자가 알아챌 수 없다.
        assertThat(preview)
                .contains("받는 사람: hong@pretty.works, kim@pretty.works, lee@pretty.works")
                .contains("제목: 8월 배포 일정")
                .contains("취소할 수 없습니다");
    }

    @Test
    void omitsTheCcLineWhenThereIsNone() {
        var params = objectMapper.readTree("""
                {"to":["hong@pretty.works"],"subject":"확인 요청","body":"확인 부탁드립니다."}
                """);

        assertThat(renderer.render(params)).doesNotContain("참조");
    }

    @Test
    void showsCcSeparatelySoItIsNotMistakenForTheMainRecipient() {
        var params = objectMapper.readTree("""
                {"to":["hong@pretty.works"],"cc":["team@pretty.works"],
                 "subject":"확인 요청","body":"확인 부탁드립니다."}
                """);

        assertThat(renderer.render(params))
                .contains("받는 사람: hong@pretty.works")
                .contains("참조: team@pretty.works");
    }

    @Test
    void keepsALongBodyFromSwallowingTheCard() {
        String body = "가".repeat(500);
        var params = objectMapper.readTree(objectMapper.writeValueAsString(
                java.util.Map.of("to", java.util.List.of("hong@pretty.works"),
                        "subject", "안내", "body", body)));

        String preview = renderer.render(params);

        assertThat(preview).contains("…");
        assertThat(preview.length()).isLessThan(400);
    }

    @Test
    void refusesToDrawACardWithoutARecipient() {
        // 받는 사람이 비면 사용자가 "누구에게 가는지" 모른 채 승인하게 된다.
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"to":[],"subject":"안내","body":"내용"}
                """)))
                .isInstanceOf(BaseException.class);

        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"subject":"안내","body":"내용"}
                """)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void refusesToDrawACardWhenARecipientIsMalformed() {
        // 한 명이라도 모양이 어긋나면 "받는 사람: hong@pretty.works, " 같은 카드가 그려진다.
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"to":["hong@pretty.works","  "],"subject":"안내","body":"내용"}
                """)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void refusesToDrawACardWithoutASubjectOrBody() {
        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"to":["hong@pretty.works"],"body":"내용"}
                """)))
                .isInstanceOf(BaseException.class);

        assertThatThrownBy(() -> renderer.render(objectMapper.readTree("""
                {"to":["hong@pretty.works"],"subject":"안내"}
                """)))
                .isInstanceOf(BaseException.class);
    }
}
