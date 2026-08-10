package HK.PrettyWorks_BE.agent.tool.task.dto;

import HK.PrettyWorks_BE.task.dto.req.TaskStatusRequest;
import jakarta.validation.constraints.NotNull;

// task.toggleStatus 요청.
//
// "누를 때마다 반전"이 아니라 목표 상태를 명시한다. 반전 방식은 네트워크 오류로 재시도가 일어나면
// 상태가 되돌아가고, 그 사고는 눈에 잘 띄지 않는다. 이미 그 상태면 아무것도 바꾸지 않고 성공한다.
public record AgentTaskStatusRequest(
        @NotNull Long taskId,
        @NotNull Boolean completed
) {
    public TaskStatusRequest toDomain() {
        return TaskStatusRequest.builder().done(completed).build();
    }
}
