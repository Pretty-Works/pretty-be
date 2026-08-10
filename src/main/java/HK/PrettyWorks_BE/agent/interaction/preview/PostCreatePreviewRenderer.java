package HK.PrettyWorks_BE.agent.interaction.preview;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 게시글은 프로젝트 전원이 보는 글이다. 잘못 올려도 저장 자체는 성공하고, 내용만 사실과 다른 채로
// 팀에 공지된다. 그래서 제목·중요도에 더해 본문 앞부분까지 카드에 싣는다 —
// 제목만으로는 "무엇을 공지하는지"를 승인 시점에 확인할 수 없다.
@Component
public class PostCreatePreviewRenderer implements ApprovalPreviewRenderer {

    // 본문 상한은 10,000자다. 카드는 한눈에 훑고 누르는 자리라 앞부분만 보여준다.
    private static final int MAX_PREVIEW_CONTENT = 200;

    @Override
    public String tool() {
        return "post.create";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);

        return "게시글을 작성합니다."
                + "\n- 제목: " + PreviewFields.requiredText(body, "title")
                + "\n- 중요도: " + PreviewFields.requiredText(body, "priority")
                + "\n- 프로젝트 #" + PreviewFields.requiredNumber(body, "projectId")
                + "\n- 내용: " + shorten(PreviewFields.requiredText(body, "content"));
    }

    // 본문의 줄바꿈을 그대로 두면 카드의 "- 항목" 구조가 무너져 어디까지가 본문인지 알 수 없다.
    private String shorten(String content) {
        String singleLine = content.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= MAX_PREVIEW_CONTENT) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_PREVIEW_CONTENT - 1) + "…";
    }
}
