package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

// 대화 내역 목록 DTO. 패널 햄버거(☰)와 "최근 대화" 3건이 같은 응답을 크기만 달리 쓴다.
@Builder
public record AgentConversationsResponse(
        List<ConversationItem> conversations
) {

    @Builder
    public record ConversationItem(
            Long conversationId,

            // 첫 질문을 요약한 제목. 에이전트가 만들어 주면 그 값, 없으면 질문 앞부분으로 폴백한 값이다.
            String title,

            // 목록 정렬 기준이자 화면의 "오늘 / 어제 / 2일 전" 표시에 쓰는 값.
            LocalDateTime lastMessageAt,
            boolean autoApprove,
            String activeRunId,
            AgentRunStatus activeRunStatus
    ) {
    }
}
