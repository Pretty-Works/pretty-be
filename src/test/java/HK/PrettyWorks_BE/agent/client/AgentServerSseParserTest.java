package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.client.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServerSseParserTest {
    private final AgentServerSseParser parser = new AgentServerSseParser(
            new AgentServerEventDecoder(new ObjectMapper(), "https://agent.example.com"));

    @Test
    void relaysStepsAndStopsAtDone() throws Exception {
        String stream = """
                : keepalive

                event: step
                data: {"text":"프로젝트를 찾고 있어요"}

                event: step
                data: {"text":"할 일을 확인하고 있어요"}

                event: done
                data: {"answer":"확인했습니다.","action":null}

                event: step
                data: {"text":"종료 뒤 이벤트는 읽지 않습니다"}

                """;
        List<DecodedAgentServerEvent> events = new ArrayList<>();

        AgentSegmentOutcome outcome = parser.parse(new StringReader(stream), events::add);

        assertThat(outcome).isEqualTo(AgentSegmentOutcome.COMPLETED);
        assertThat(events).hasSize(3);
    }

    @Test
    void waitingEventClosesTheCurrentSegmentLocally() throws Exception {
        String stream = """
                event: question
                data: {"label":"장소 입력","text":"장소가 어디인가요?","options":[]}

                event: step
                data: {"text":"이 이벤트는 다음 세그먼트 규칙 위반입니다"}

                """;

        AgentSegmentOutcome outcome = parser.parse(new StringReader(stream), ignored -> {});

        assertThat(outcome).isEqualTo(AgentSegmentOutcome.WAITING_INPUT);
    }

    @Test
    void discardsTwoInvalidEventsButFailsOnThirdAccumulatedViolation() {
        String stream = """
                event: unknown
                data: {}

                event: step
                data: {"text":"이 정상 이벤트 뒤에도 누적 횟수는 유지됩니다"}

                event: step
                data: {"text":1}

                event: step
                data: {"text":"한 줄"}
                data: {"text":"두 줄"}

                """;

        assertThatThrownBy(() -> parser.parse(new StringReader(stream), ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.AGENT_RESPONSE_INVALID));
    }

    @Test
    void eofWithoutWaitingOrTerminalEventIsInterrupted() {
        String stream = """
                \uFEFFevent: step
                data: {"text":"처리 중입니다"}

                """;

        assertThatThrownBy(() -> parser.parse(new StringReader(stream), ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.STREAM_INTERRUPTED));
    }

    @Test
    void errorEventIsAValidFailedSegment() throws Exception {
        String stream = """
                event: error
                data: {"code":"AGENT_017","message":"응답이 중단되었습니다."}

                """;

        AgentSegmentOutcome outcome = parser.parse(new StringReader(stream), ignored -> {});

        assertThat(outcome).isEqualTo(AgentSegmentOutcome.FAILED);
    }
}
