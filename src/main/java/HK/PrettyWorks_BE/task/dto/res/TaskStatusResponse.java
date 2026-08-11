package HK.PrettyWorks_BE.task.dto.res;

import lombok.Builder;

import java.time.LocalDateTime;

// 완료 토글 결과. 화면은 204로 끝나지만, 호출자가 "무엇이 어떻게 바뀌었는지"를 그대로 말해야 하는
// 경우(에이전트 답변)가 있어 서비스는 결과를 돌려준다.
@Builder
public record TaskStatusResponse(
        Long taskId,
        String content,
        boolean completed,
        LocalDateTime completedAt,
        // 실제로 바뀌었는지. false면 이미 그 상태였다는 뜻이며 에러가 아니다.
        boolean changed
) {
}
