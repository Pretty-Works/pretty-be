package HK.PrettyWorks_BE.agent.shared.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum AgentErrorCode implements ErrorCode {
    NOT_MY_CONVERSATION(HttpStatus.FORBIDDEN, "AGENT_001", "본인의 대화가 아닙니다."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AGENT_002", "대화를 찾을 수 없습니다."),

    // 502 — FastAPI에 닿지 못한 경우(서버 미기동·커넥션 거부). 우리 잘못이 아니라 상대 서버 문제라 5xx다.
    AGENT_SERVER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AGENT_003", "에이전트 서버에 연결하지 못했습니다."),

    // 409 — 그 대화에 진행 중인 실행(RUNNING·WAITING_APPROVAL·WAITING_INPUT)이 있어 새 메시지를 받을 수 없다.
    //
    // 막는 기준이 "대화가 끝났나"가 아니라 "지금 돌고 있나"인 것이 핵심이다. 대화는 상태를 갖지 않고
    // 잠기지도 않으므로(규격 v2 §3), 이전 요청이 끝난 대화에는 얼마든지 이어서 말할 수 있다.
    RUN_IN_PROGRESS(HttpStatus.CONFLICT, "AGENT_004", "진행 중인 요청이 있습니다. 끝나기를 기다리거나 먼저 응답해 주세요."),

    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "AGENT_005", "메시지를 찾을 수 없습니다."),

    // 409 — 이미 응답한 승인·질문에 다시 응답한 경우. 두 탭에서 동시에 누른 경우가 대표적이다.
    // 조건부 갱신(UPDATE ... WHERE status='PENDING')이 0행이면 이 코드로 떨어진다.
    // 승인과 질문이 공용으로 쓴다 — 화면에서 할 일이 "이미 처리됐다고 알리기"로 같기 때문이다.
    INTERACTION_ALREADY_RESOLVED(HttpStatus.CONFLICT, "AGENT_006", "이미 처리된 요청입니다."),

    // 502 — 연결은 됐는데 응답이 약속한 모양이 아닌 경우(JSON 파싱 실패, answer 누락 등).
    // AGENT_003과 나눈 이유: LLM팀이 스키마를 바꾸면 이쪽이 나는데, 연결 실패로 뭉뚱그리면 원인을 못 찾는다.
    AGENT_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AGENT_007", "에이전트 응답을 해석하지 못했습니다."),

    // 504 — 연결은 됐으나 제한 시간 안에 응답이 오지 않은 경우.
    // AGENT_003과 나눈 이유: 화면 안내가 다르다. 점검 중(재시도 무의미) vs 지연(재시도 가능).
    AGENT_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AGENT_008", "에이전트 응답 시간이 초과되었습니다."),

    // 400 — screenContext는 서버가 해석하지 않고 통과시키므로 필드 단위 검증이 불가능하다.
    // 대신 직렬화 길이로 상한을 둔다. 프론트가 목록 전체를 실어 보내면 LLM 토큰이 폭발한다.
    SCREEN_CONTEXT_TOO_LARGE(HttpStatus.BAD_REQUEST, "AGENT_009", "화면 정보가 너무 큽니다."),

    // ── 실행(Run) ──────────────────────────────────────────────────────────
    // 대화(001·002)와 실행(010·011)의 코드를 나눈 이유: 화면이 다르게 반응해야 한다.
    // 대화가 없으면 목록으로 돌려보내지만, 실행이 없으면 그 대화에 남아 다시 시도하면 된다.
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "AGENT_010", "실행을 찾을 수 없습니다."),
    NOT_MY_RUN(HttpStatus.FORBIDDEN, "AGENT_011", "본인의 실행이 아닙니다."),

    // 404 — 없는 approvalId · questionId. 승인과 질문이 한 테이블(agent_interactions)이라 공용이다.
    INTERACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AGENT_012", "요청을 찾을 수 없습니다."),

    // AGENT_013 은 비어 있다. "이미 종료된 작업입니다"였는데, 대화 : 실행 = 1:N 으로 바꾸면서
    // 그 상황 자체가 사라졌다(종료된 실행이 있어도 새 실행을 열면 되므로). 번호는 재사용하지 않는다 —
    // 재사용하면 예전 로그·문서의 013이 다른 뜻으로 읽힌다.

    // ── 승인 토큰 (내부 API) ────────────────────────────────────────────────
    // 401 — 토큰 없음·만료·이미 소진·runId/toolCallId 불일치를 하나로 묶었다.
    // 나누면 공격자에게 "어디까지 맞았는지" 알려주는 꼴이 된다.
    INVALID_APPROVAL_TOKEN(HttpStatus.UNAUTHORIZED, "AGENT_014", "승인 정보가 유효하지 않습니다."),

    // 409 — 승인 시점 params_hash 와 실제 요청 바디의 해시가 다르다.
    // 이 구조의 핵심 방어선이다. 사용자가 카드에서 본 것과 다른 내용은 물리적으로 저장될 수 없다.
    PARAMS_MISMATCH(HttpStatus.CONFLICT, "AGENT_015", "승인한 내용과 요청 내용이 다릅니다."),

    // ── 스트림 장애 ────────────────────────────────────────────────────────
    // 502 — FastAPI 재배포 등으로 체크포인트가 사라져 resume 가 404를 받은 경우.
    // 클라이언트 잘못이 아니라 상대 서버가 상태를 잃은 것이라 5xx다.
    CHECKPOINT_LOST(HttpStatus.BAD_GATEWAY, "AGENT_016", "작업을 이어갈 수 없습니다. 다시 시도해 주세요."),

    // 502 — done 없이 스트림이 끊긴 경우. 받은 step 까지는 보존한다.
    STREAM_INTERRUPTED(HttpStatus.BAD_GATEWAY, "AGENT_017", "에이전트 응답이 중단되었습니다."),

    // ── 한도 ──────────────────────────────────────────────────────────────
    // 429 — 내 동시 진행 실행이 한도(agent.run.max-active-per-user)를 넘음.
    //       내가 정리하면 풀리는 상황이라 빠져나갈 길을 알려준다.
    TOO_MANY_RUNS(HttpStatus.TOO_MANY_REQUESTS, "AGENT_018",
            "진행 중인 작업이 너무 많습니다. 홈의 '확인이 필요한 요청'을 먼저 정리해 주세요."),

    // 409 — 승인·질문을 agent.interaction.ttl-minutes 동안 방치. 실행은 EXPIRED 로 닫힌다.
    INTERACTION_EXPIRED(HttpStatus.CONFLICT, "AGENT_019", "응답 시간이 지났습니다."),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AGENT_020", "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),

    // 401 — X-Internal-Api-Key 불일치. 상수시간 비교로 판정한다(String.equals 는 타이밍이 샌다).
    INVALID_INTERNAL_KEY(HttpStatus.UNAUTHORIZED, "AGENT_021", "내부 API 인증에 실패했습니다."),

    // 실행당 질문 5회 초과. 스트림 안에서 event: error 로만 나가므로 HTTP 상태는 쓰이지 않는다.
    // enum 이 status 를 요구해 형식상 409를 둔다.
    TOO_MANY_QUESTIONS(HttpStatus.CONFLICT, "AGENT_022",
            "확인할 항목이 너무 많습니다. 화면에서 직접 입력해 주세요."),

    // ── 회의록 초안 생성 (에이전트 Run이 아닌 단발 API) ──────────────────────
    // 400 — @Size 로 걸면 REQUEST_001("잘못된 요청입니다")이 나가는데, 그러면 사용자가 무엇을
    // 고쳐야 할지 알 수 없다. 화면에 그대로 띄울 수 있게 전용 코드를 둔다.
    TRANSCRIPT_TOO_LONG(HttpStatus.BAD_REQUEST, "AGENT_023", "녹취록이 너무 깁니다. 30,000자 이내로 줄여 주세요."),

    // 429 — 같은 사용자가 초안 생성을 동시에 여러 건 돌리는 것을 막는다. LLM 호출은 비싸다.
    DRAFT_IN_PROGRESS(HttpStatus.TOO_MANY_REQUESTS, "AGENT_024", "초안을 만드는 중입니다. 잠시만 기다려 주세요."),

    // 실행당 내부 도구 호출 20회 초과. 프롬프트 인젝션이나 잘못된 루프의 폭주를 끊는다.
    TOOL_CALL_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AGENT_025",
            "도구 호출 한도를 초과해 작업을 중단했습니다."),

    // 413 — 내부 WRITE 요청 본문이 캐시 상한을 넘었다.
    //
    // 이 코드를 따로 두는 이유는 진단 때문이다. 상한을 넘기면 ContentCachingRequestWrapper 가
    // 앞부분만 캐시하는데, 그 잘린 바이트를 해시하면 AGENT_015(승인한 내용과 다름)가 난다.
    // LLM팀은 승인받은 그대로를 보냈는데도 계속 거부당하니 원인을 찾을 수가 없다.
    // "본문이 너무 크다"고 분명히 말해 줘야 한다.
    WRITE_BODY_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "AGENT_026",
            "요청 본문이 너무 큽니다."),

    // 취소 API는 성공 응답을 반환하므로 HTTP 상태는 쓰이지 않고 SSE error 이벤트와
    // 실행 감사 이력에만 남는다. 기존 장애 코드와 섞으면 사용자가 취소한 작업이 장애율로 잡힌다.
    RUN_CANCELED(HttpStatus.CONFLICT, "AGENT_027", "작업을 취소했습니다."),

    // ── 프로젝트 탭 AI 요약 (에이전트 Run이 아닌 단발 API) ────────────────────
    // 429 — 같은 프로젝트의 요약을 이미 만들고 있다. 저장된 배너가 있으면 그것을 돌려주므로,
    // 이 코드는 "첫 생성이 진행 중이라 아직 보여줄 것이 없다"는 한 가지 경우에만 나간다.
    // 초안 생성(AGENT_024)과 나눈 이유는 화면이 다르기 때문이다 — 이쪽은 배너를 잠시 접어두면 된다.
    SUMMARY_IN_PROGRESS(HttpStatus.TOO_MANY_REQUESTS, "AGENT_028",
            "요약을 만드는 중입니다. 잠시 후 다시 확인해 주세요."),

    // ── 채팅 첨부 파일 ─────────────────────────────────────────────────────
    // screenContext(AGENT_009)와 같은 이유로 전용 코드를 둔다. @Size·@Pattern으로 걸면 전부
    // REQUEST_001("잘못된 요청입니다")이 나가는데, 파일은 사용자가 직접 고를 수 있는 대상이라
    // "무엇이 잘못됐는지"를 화면에 그대로 띄워 줘야 다시 시도할 수 있다.

    // 400 — 허용 목록(agent.upload.allowed-extensions)에 없는 확장자.
    // 판정 근거는 확장자 하나뿐이다. 클라이언트가 보낸 Content-Type은 믿지 않는다 —
    // 정상 .txt가 application/octet-stream으로 오기도 하고, 그 반대도 얼마든지 가능하다.
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "AGENT_029", "보낼 수 없는 형식의 파일입니다."),

    // 413 — 파일 하나 또는 합계가 상한을 넘었다. 톰캣의 멀티파트 상한(20MB)보다 훨씬 앞에서 끊는다.
    // 파일 내용이 그대로 LLM 프롬프트에 실리므로, 여기서 막지 않으면 토큰이 폭발한다.
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "AGENT_030", "파일이 너무 큽니다."),

    // 400 — 한 번에 보낼 수 있는 파일 개수 초과.
    TOO_MANY_FILES(HttpStatus.BAD_REQUEST, "AGENT_031", "한 번에 보낼 수 있는 파일 수를 넘었습니다."),

    // 400 — 빈 파일, 파일명이 없거나 위험한 경우, 그리고 텍스트 파일이 UTF-8이 아닌 경우.
    // 인코딩을 추측해 고쳐 읽지 않는다. CP949 파일을 UTF-8로 우겨 읽으면 깨진 글자가 그대로
    // LLM에 전달돼 조용히 엉뚱한 답이 나오는데, 그건 에러보다 나쁘다.
    FILE_NOT_READABLE(HttpStatus.BAD_REQUEST, "AGENT_032",
            "파일을 읽지 못했습니다. UTF-8로 저장된 파일인지 확인해 주세요."),

    // 400 — 회의록 초안 생성에 올린 파일이 읽히기는 했으나 알맹이가 없다(공백·개행뿐).
    // AGENT_032와 나눈 이유는 사용자가 할 일이 다르기 때문이다. 저쪽은 "다른 인코딩으로 저장해
    // 다시 올려라"지만, 이쪽은 파일 형식이 아니라 내용이 없는 것이라 바꿔 올릴 파일이 따로 있다.
    TRANSCRIPT_EMPTY(HttpStatus.BAD_REQUEST, "AGENT_033", "회의 기록이 비어 있습니다."),

    // 429 — 서버 전체의 동시 실행이 한도(agent.run.max-active-total)를 넘음.
    // AGENT_018과 나눈 이유는 사용자가 할 일이 다르기 때문이다. 저쪽은 내 작업을 정리하면 풀리지만
    // 이쪽은 남들이 끝나기를 기다리는 수밖에 없다.
    SERVER_BUSY(HttpStatus.TOO_MANY_REQUESTS, "AGENT_034",
            "지금은 처리 중인 작업이 많습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
