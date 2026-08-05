package HK.PrettyWorks_BE.agent.internal.dto.req;

import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// task.create 요청. 공개 API와 달리 여러 건을 한 번에 받는다.
//
// 도구를 3번 부르면 승인 카드가 3번 뜨고 사용자가 같은 버튼을 세 번 누른다. 도구 호출 상한
// (실행당 20회)도 빨리 소모된다. 배열 하나로 받아 승인 카드 하나에 전부 보여주고 한 트랜잭션으로 저장한다.
//
// 배열을 최상위로 두지 않고 객체로 감싼 이유: 나중에 배치 옵션(예: 실패 시 부분 저장 허용)이 붙을 때
// 최상위가 배열이면 필드를 더할 자리가 없어 스키마를 통째로 바꿔야 한다.
public record AgentTaskCreateRequest(
        // 건수 상한은 TaskService가 본다(TaskPolicy.MAX_CREATE_BATCH_SIZE) — 여기 숫자를 또 적으면 두 벌이 된다.
        @NotEmpty @Valid List<TaskRequest> tasks
) {
}
