package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.constant.AgentAccessType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServerEventDecoderTest {
    private static final String ALLOWED_ORIGINS = "https://agent.example.com, http://localhost:8000";

    private final AgentServerEventDecoder decoder =
            new AgentServerEventDecoder(new ObjectMapper(), ALLOWED_ORIGINS);

    @Test
    void decodesAllValidEventShapes() {
        var decodedStep = decoder.decode(
                "step", "{\"text\":\"프로젝트를 찾고 있어요\"}");
        AgentServerEvent.Step step = (AgentServerEvent.Step) decodedStep.event();
        var decodedApproval = decoder.decode("approval_request", """
                        {"toolCallId":"tc_1","tool":"task.create","access":"WRITE",
                         "summary":"할 일 2건 추가","previewText":"· 첫 번째\\n· 두 번째",
                         "params":{"tasks":[{"title":"첫 번째"},{"title":"두 번째"}]},
                         "alternatives":[{"id":"FILL_FORM","label":"직접 고칠래요"}],
                         "futureField":"additive-change"}
                        """.strip().replace("\n", ""));
        AgentServerEvent.ApprovalRequest approval =
                (AgentServerEvent.ApprovalRequest) decodedApproval.event();
        AgentServerEvent.Question question = (AgentServerEvent.Question) decoder.decode(
                "question", """
                        {"label":"프로젝트 선택","text":"어느 프로젝트인가요?",
                         "options":[{"id":"3","label":"그룹웨어","description":"진행 중"}]}
                        """.strip().replace("\n", "")).event();
        AgentServerEvent.Done done = (AgentServerEvent.Done) decoder.decode(
                "done", """
                        {"answer":"등록했습니다.","action":{"type":"NAVIGATE","label":"할 일 보기",
                         "targetScreen":"TASK_LIST","params":{"projectId":3}}}
                        """.strip().replace("\n", "")).event();
        AgentServerEvent.Failure error = (AgentServerEvent.Failure) decoder.decode(
                "error", "{\"code\":\"AGENT_017\",\"message\":\"응답이 중단되었습니다.\"}").event();

        assertThat(step.text()).isEqualTo("프로젝트를 찾고 있어요");
        assertThat(approval.access()).isEqualTo(AgentAccessType.WRITE);
        assertThat(approval.params().get("tasks")).hasSize(2);
        assertThat(approval.alternatives()).containsExactly(
                new AgentServerEvent.Alternative("FILL_FORM", "직접 고칠래요"));
        assertThat(question.multiple()).isFalse();
        assertThat(question.allowFreeText()).isTrue();
        assertThat(done.action().type()).isEqualTo("NAVIGATE");
        assertThat(error.code()).isEqualTo("AGENT_017");
        assertThat(decodedStep.payload().get("text").textValue()).isEqualTo("프로젝트를 찾고 있어요");
        assertThat(decodedApproval.payload().get("futureField").textValue())
                .isEqualTo("additive-change");
    }

    @Test
    void approvalRejectsReadAccessAndServerOwnedId() {
        String readApproval = """
                {"toolCallId":"tc_1","tool":"project.search","access":"READ",
                 "summary":"프로젝트 조회","previewText":"프로젝트를 조회합니다.","params":{}}
                """.strip().replace("\n", "");
        String forgedApprovalId = """
                {"approvalId":41,"toolCallId":"tc_1","tool":"task.create","access":"WRITE",
                 "summary":"할 일 추가","previewText":"할 일을 추가합니다.","params":{}}
                """.strip().replace("\n", "");

        assertThatThrownBy(() -> decoder.decode("approval_request", readApproval))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("READ tools");
        assertThatThrownBy(() -> decoder.decode("approval_request", forgedApprovalId))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("approvalId");
    }

    @Test
    void approvalRejectsAlwaysBecauseItIsReservedByBe() {
        String data = """
                {"toolCallId":"tc_1","tool":"task.create","access":"WRITE",
                 "summary":"할 일 추가","previewText":"할 일을 추가합니다.","params":{},
                 "alternatives":[{"id":"ALWAYS","label":"항상 허용"}]}
                """.strip().replace("\n", "");

        assertThatThrownBy(() -> decoder.decode("approval_request", data))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("ALWAYS");
    }

    @Test
    void questionRequiresOptionsArrayAndRejectsServerOwnedId() {
        assertThatThrownBy(() -> decoder.decode("question",
                "{\"label\":\"장소 입력\",\"text\":\"어디인가요?\"}"))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("options");

        assertThatThrownBy(() -> decoder.decode("question", """
                {"questionId":51,"label":"장소 입력","text":"어디인가요?","options":[]}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("questionId");
    }

    @Test
    void rejectsAmbiguousOrUnanswerableChoices() {
        assertThatThrownBy(() -> decoder.decode("approval_request", """
                {"toolCallId":"tc_1","tool":"task.create","access":"WRITE",
                 "summary":"할 일 추가","previewText":"할 일을 추가합니다.","params":{},
                 "alternatives":[{"id":"FILL_FORM","label":"직접 수정"},
                                 {"id":"FILL_FORM","label":"화면에서 수정"}]}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("unique");

        assertThatThrownBy(() -> decoder.decode("question", """
                {"label":"장소 입력","text":"어디인가요?","options":[],"allowFreeText":false}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("answer path");
    }

    @Test
    void validatesDoneActionByType() {
        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"초안을 만들었습니다.","action":{"type":"FILL_FORM","label":"확인하기",
                 "targetScreen":"TASK_CREATE"}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("formData");

        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"등록했습니다.","action":{"type":"NAVIGATE","label":"확인하기",
                 "targetScreen":"TASK_LIST","formData":{"title":"잘못된 필드"}}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("must not contain formData");
    }

    @Test
    void acceptsExternalUrlActionWithoutTargetScreen() {
        // 외부 인증(Gmail OAuth 등) 진입점. 이동할 사내 화면이 없어 targetScreen이 null로 온다.
        AgentServerEvent.Done done = (AgentServerEvent.Done) decoder.decode("done", """
                {"answer":"Gmail을 연동하시겠습니까?","action":{"type":"OPEN_EXTERNAL_URL",
                 "label":"Gmail 연동하기","targetScreen":null,
                 "params":{"url":"https://agent.example.com/oauth/google/start?request=abc"}}}
                """.strip().replace("\n", "")).event();

        assertThat(done.action().type()).isEqualTo("OPEN_EXTERNAL_URL");
        assertThat(done.action().targetScreen()).isNull();
        assertThat(done.action().params().get("url").textValue())
                .isEqualTo("https://agent.example.com/oauth/google/start?request=abc");
    }

    @Test
    void rejectsExternalUrlActionThatCannotBeOpenedSafely() {
        // 이 값은 그대로 저장돼 새로고침 때 다시 내려간다. 프론트 검증만 믿으면 위험한 스킴이 눌러앉는다.
        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"연동해 주세요.","action":{"type":"OPEN_EXTERNAL_URL","label":"연동하기",
                 "params":{"url":"javascript:alert(1)"}}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("http(s)");

        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"연동해 주세요.","action":{"type":"OPEN_EXTERNAL_URL","label":"연동하기"}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("params.url");
    }

    @Test
    void allowsOnlyConfiguredOriginsForExternalLinks() {
        // 프롬프트 인젝션 한 번이면 "Gmail 연동하기"처럼 보이는 피싱 버튼이 사내 도구 안에 그려진다.
        // 목적지를 서버에서 묶지 않으면 프론트 검증 하나가 유일한 방어선이 된다.
        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"Gmail을 연동하시겠습니까?","action":{"type":"OPEN_EXTERNAL_URL",
                 "label":"Gmail 연동하기","params":{"url":"https://accounts-google.evil.com/login"}}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("origin is not allowed");
    }

    @Test
    void matchesOriginExactlyInsteadOfBySuffix() {
        // endsWith·contains로 비교하면 아래 두 주소가 전부 통과한다. 실무에서 제일 흔한 우회다.
        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"연동해 주세요.","action":{"type":"OPEN_EXTERNAL_URL","label":"연동하기",
                 "params":{"url":"https://agent.example.com.evil.com/oauth/start"}}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("origin is not allowed");

        // 포트가 다르면 다른 origin이다. 허용 목록엔 localhost:8000만 있다.
        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"연동해 주세요.","action":{"type":"OPEN_EXTERNAL_URL","label":"연동하기",
                 "params":{"url":"http://localhost:9999/oauth/start"}}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("origin is not allowed");
    }

    @Test
    void refusesToStartWithoutAnExternalUrlAllowlist() {
        // 기본값을 "전부 허용"으로 두면 설정을 깜빡한 환경이 조용히 무방비가 된다.
        assertThatThrownBy(() -> new AgentServerEventDecoder(new ObjectMapper(), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.external-url.allowed-origins");
    }

    @Test
    void stillRequiresTargetScreenForInAppActions() {
        // targetScreen을 종류별로 갈랐으니, 화면으로 가는 종류에서 필수가 풀리지 않았는지 확인한다.
        assertThatThrownBy(() -> decoder.decode("done", """
                {"answer":"등록했습니다.","action":{"type":"NAVIGATE","label":"할 일 보기"}}
                """.strip().replace("\n", "")))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("targetScreen");
    }

    @Test
    void rejectsUnknownEventMalformedJsonAndMultilineData() {
        assertThatThrownBy(() -> decoder.decode("token", "{}"))
                .isInstanceOf(AgentServerEventDecodingException.class);
        assertThatThrownBy(() -> decoder.decode("step", "not-json"))
                .isInstanceOf(AgentServerEventDecodingException.class);
        assertThatThrownBy(() -> decoder.decode("step", "{\"text\":\"a\"}\n"))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("one line");
    }

    @Test
    void validatesErrorCodeNamespace() {
        assertThatThrownBy(() -> decoder.decode(
                "error", "{\"code\":\"TASK_007\",\"message\":\"실패\"}"))
                .isInstanceOf(AgentServerEventDecodingException.class)
                .hasMessageContaining("code");
    }
}
