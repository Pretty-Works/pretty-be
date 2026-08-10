package HK.PrettyWorks_BE.agent.conversation.api.response;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

// 대화 내역 목록의 한 줄. 패널 햄버거(☰)와 "최근 대화" 3건이 size만 달리 해서 같은 응답을 쓴다.
@Builder
public record AgentConversationListResponse(
        @Schema(example = "15")
        Long conversationId,

        // 첫 질문을 요약한 제목. 에이전트가 만들어 주면 그 값, 없으면 질문 앞부분으로 폴백한 값이다.
        @Schema(description = "첫 질문을 요약한 제목", example = "8월 휴가 일정 추천")
        String title,

        @Schema(description = "가장 최근 실행의 상태. WAITING_APPROVAL·WAITING_INPUT이면 답을 기다리는 중입니다. "
                + "실행이 한 번도 없었으면 null입니다.",
                nullable = true, example = "WAITING_APPROVAL")
        AgentRunStatus status,

        @Schema(description = "가장 최근 실행의 runId. 재연결·취소에 그대로 씁니다.",
                nullable = true, example = "run_c02b58")
        String runId,

        @Schema(description = "답을 기다리는 승인 카드의 id. 목록에 '확인 필요' 배지를 그릴 때 씁니다. 없으면 null입니다.",
                nullable = true, example = "44")
        Long pendingApprovalId,

        // 읽음 처리 API를 호출한 시점 이후로 새 에이전트 답변이 있는지. 내가 보낸 질문은 세지 않는다.
        @Schema(description = "안 읽은 에이전트 답변이 있으면 true. 목록에 '새 답장' 점을 찍을 때 씁니다.",
                example = "true")
        boolean unread,

        // 목록 정렬 기준이자 화면의 "오늘 / 어제 / 2일 전" 표시에 쓰는 값.
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss",
                timezone = "Asia/Seoul")
        @Schema(description = "목록 정렬 기준", example = "2026-08-02 14:31:02")
        LocalDateTime lastMessageAt,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss",
                timezone = "Asia/Seoul")
        @Schema(description = "대화가 처음 만들어진 시각", example = "2026-08-02 14:30:44")
        LocalDateTime createdAt
) {
}
