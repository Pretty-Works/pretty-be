package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.constant.AgentAccessType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServerEventDecoderTest {
    private final AgentServerEventDecoder decoder =
            new AgentServerEventDecoder(new ObjectMapper());

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
