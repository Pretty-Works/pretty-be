package HK.PrettyWorks_BE.agent.execution.gateway;

import HK.PrettyWorks_BE.agent.execution.domain.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEventType;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.function.Consumer;

/**
 * FastAPI 응답 바이트를 SSE frame으로 나누는 제한형 파서입니다.
 * JSON 의미 검증은 {@link AgentServerEventDecoder}가 담당합니다.
 */
@Slf4j
@Component
public class AgentServerSseParser {
    private static final int MAX_LINE_CHARS = 256 * 1024;
    private static final int MAX_INVALID_EVENTS = 3;

    private final AgentServerEventDecoder decoder;

    public AgentServerSseParser(AgentServerEventDecoder decoder) {
        this.decoder = decoder;
    }

    public AgentSegmentOutcome parse(Reader source,
                                     Consumer<DecodedAgentServerEvent> eventConsumer)
            throws IOException {
        return parse(source, "unknown", eventConsumer);
    }

    public AgentSegmentOutcome parse(Reader source, String runId,
                                     Consumer<DecodedAgentServerEvent> eventConsumer)
            throws IOException {
        PushbackReader reader = new PushbackReader(source, 1);
        Frame frame = new Frame();
        int invalidEvents = 0;
        boolean firstLine = true;

        while (true) {
            BoundedLine line = readLine(reader);
            if (line == null) {
                // SSE는 빈 줄로 frame을 끝낸다. 끝나지 않은 마지막 frame은 성공으로 보지 않는다.
                throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
            }
            if (line.tooLong()) {
                frame.invalidate();
                continue;
            }

            String value = line.value();
            if (firstLine) {
                firstLine = false;
                if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
                    value = value.substring(1);
                }
            }

            if (value.isEmpty()) {
                if (frame.isEmpty()) {
                    continue;
                }
                try {
                    DecodedAgentServerEvent decoded = frame.decode(decoder);
                    eventConsumer.accept(decoded);
                    AgentSegmentOutcome outcome = outcome(decoded.event().type());
                    if (outcome != null) {
                        return outcome;
                    }
                } catch (AgentServerEventDecodingException exception) {
                    invalidEvents++;
                    log.warn("[에이전트 SSE] 규격 밖 이벤트를 폐기했습니다. runId={} count={} reason={}",
                            runId, invalidEvents, exception.getMessage());
                    if (invalidEvents >= MAX_INVALID_EVENTS) {
                        throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
                    }
                } finally {
                    frame = new Frame();
                }
                continue;
            }

            // SSE comment는 FastAPI 연결 생존용일 뿐 유효한 에이전트 이벤트가 아니다.
            if (value.charAt(0) == ':') {
                continue;
            }
            frame.accept(value);
        }
    }

    private AgentSegmentOutcome outcome(AgentServerEventType type) {
        return switch (type) {
            case STEP -> null;
            case APPROVAL_REQUEST -> AgentSegmentOutcome.WAITING_APPROVAL;
            case QUESTION -> AgentSegmentOutcome.WAITING_INPUT;
            case DONE -> AgentSegmentOutcome.COMPLETED;
            case ERROR -> AgentSegmentOutcome.FAILED;
        };
    }

    private BoundedLine readLine(PushbackReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        boolean tooLong = false;
        boolean sawCharacter = false;
        while (true) {
            int read = reader.read();
            if (read == -1) {
                return !sawCharacter && line.isEmpty() && !tooLong
                        ? null
                        : new BoundedLine(line.toString(), tooLong);
            }
            sawCharacter = true;
            if (read == '\n') {
                return new BoundedLine(line.toString(), tooLong);
            }
            if (read == '\r') {
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.unread(next);
                }
                return new BoundedLine(line.toString(), tooLong);
            }
            if (!tooLong) {
                if (line.length() >= MAX_LINE_CHARS) {
                    tooLong = true;
                    line.setLength(0);
                } else {
                    line.append((char) read);
                }
            }
        }
    }

    private record BoundedLine(String value, boolean tooLong) {
    }

    private static final class Frame {
        private String event;
        private String data;
        private boolean invalid;
        private boolean hasContent;

        void accept(String line) {
            hasContent = true;
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            switch (field) {
                case "event" -> {
                    if (event != null) invalid = true;
                    else event = value;
                }
                case "data" -> {
                    // 공동 규격은 data 한 줄만 허용한다. 두 줄을 개행으로 합치지 않는다.
                    if (data != null) invalid = true;
                    else data = value;
                }
                default -> invalid = true;
            }
        }

        void invalidate() {
            hasContent = true;
            invalid = true;
        }

        boolean isEmpty() {
            return !hasContent;
        }

        DecodedAgentServerEvent decode(AgentServerEventDecoder decoder) {
            if (invalid) {
                throw new AgentServerEventDecodingException("invalid SSE frame");
            }
            return decoder.decode(event, data);
        }
    }
}
