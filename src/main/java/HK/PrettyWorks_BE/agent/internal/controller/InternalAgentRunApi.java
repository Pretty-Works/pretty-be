package HK.PrettyWorks_BE.agent.internal.controller;

import HK.PrettyWorks_BE.agent.internal.dto.res.AgentRunUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 내부 실행(run) 조회 API 문서.
//
// InternalAgentToolApi(에이전트가 실행 중에 쓰는 도구)와 나눈 이유는 호출 규약이 다르기 때문이다.
// 도구는 X-Run-Id로 실행 문맥을 받고 툴콜 예산을 소모하지만, 여기는 runId가 조회 대상이라
// 경로로 받고 예산도 깎지 않는다. 한 인터페이스에 섞으면 읽는 쪽이 규약을 헷갈린다.
@Tag(name = "에이전트 내부 실행 조회", description = "외부 MCP 서버 전용 실행 메타 조회 API")
public interface InternalAgentRunApi {

    @Operation(summary = "run.user", description = """
            실행(run)의 주인이 누구인지 userId로 알려줍니다. 사용자별 자격증명을 따로 보관하는 외부 MCP 서버가
            에이전트에게서 받은 run_id를 사용자로 바꿀 때 씁니다.
            - X-Internal-Api-Key(헤더): 필수입니다. X-Run-Id 헤더는 쓰지 않습니다.
            - 실행 상태는 보지 않습니다. 이미 끝난 실행도 주인은 그대로라 200으로 응답합니다.
            - AGENT_010(404): 그런 run_id가 없습니다.
            - USER_003(400): 퇴사한 사용자의 실행입니다. 이 경우 해당 사용자를 대신해 작업하면 안 됩니다.
            """)
    ResponseEntity<AgentRunUserResponse> runUser(
            @Parameter(description = "에이전트 실행 식별자", example = "run-01H8XK3M9P")
            String runId);
}
