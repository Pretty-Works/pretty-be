package HK.PrettyWorks_BE.agent.repository;

import java.time.LocalDateTime;

// 홈 화면 「확인이 필요한 요청」 카드 JPQL 프로젝션 결과.
//
// 대기는 스레드 단위로 생기지만(스레드당 최대 1건), 이 목록은 사용자의 모든 스레드를 합쳐서
// 보여준다. 그래서 어느 대화에서 온 건지 알 수 있게 conversationTitle을 함께 내려준다.
// 카드에 표시할 문구와 선택지 버튼은 actionJson 안에 있으며 프론트가 해석한다.
public record PendingActionRow(
        Long messageId,
        Long conversationId,
        String conversationTitle,
        String actionType,
        String actionJson,
        LocalDateTime createdAt
) {
}
