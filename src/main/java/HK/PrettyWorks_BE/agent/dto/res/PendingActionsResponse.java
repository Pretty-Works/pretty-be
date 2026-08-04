package HK.PrettyWorks_BE.agent.dto.res;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

// 홈 화면 「확인이 필요한 요청」 DTO.
//
// 대기는 스레드 단위로 생기지만(스레드당 최대 1건), 이 목록은 사용자의 모든 스레드를 합친다.
// 그래서 count는 뱃지 숫자와 같고, 별도의 개수 조회 API를 두지 않았다.
@Builder
public record PendingActionsResponse(

        // 상단 뱃지에 그대로 쓰는 값. items.size()와 같지만, 프론트가 목록을 펼치지 않고도
        // 숫자만 읽을 수 있게 따로 내려준다.
        int count,

        List<PendingItem> items
) {

    @Builder
    public record PendingItem(
            // 승인·취소 API에 넘길 id.
            Long messageId,

            // 카드를 눌러 해당 대화로 이동할 때 쓴다.
            Long conversationId,

            // 어느 대화에서 나온 요청인지 보여주기 위한 스레드 제목.
            String conversationTitle,

            String actionType,

            // 카드에 표시할 문구와 선택지 버튼이 들어 있다. 프론트가 해석한다.
            JsonNode action,

            LocalDateTime createdAt
    ) {
    }
}
