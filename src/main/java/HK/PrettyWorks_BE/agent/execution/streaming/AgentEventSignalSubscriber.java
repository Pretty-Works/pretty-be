package HK.PrettyWorks_BE.agent.execution.streaming;

import HK.PrettyWorks_BE.agent.execution.application.AgentSegmentExecutor;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventSignalSubscriber implements MessageListener {
    private static final int MAX_SIGNAL_BYTES = 64;

    private final AgentStreamService streamService;
    private final AgentRunRepository runRepository;
    private final AgentSegmentExecutor segmentExecutor;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        byte[] body = message.getBody();
        if (body == null || body.length == 0 || body.length > MAX_SIGNAL_BYTES) {
            log.warn("[에이전트 이벤트 알림] 잘못된 Redis 신호 길이={}",
                    body == null ? 0 : body.length);
            return;
        }
        String value = new String(body, StandardCharsets.UTF_8);
        int separator = value.indexOf(':');
        try {
            if (separator <= 0 || separator == value.length() - 1) {
                throw new NumberFormatException();
            }
            long runId = Long.parseLong(value.substring(0, separator));
            long sequence = Long.parseLong(value.substring(separator + 1));
            if (runId <= 0 || sequence <= 0) {
                throw new NumberFormatException();
            }
            streamService.deliverCommittedSignal(runId, sequence);
            runRepository.findById(runId)
                    .filter(run -> run.getStatus().isTerminal())
                    .ifPresent(run -> segmentExecutor.cancel(run.getId(), run.getRunId()));
        } catch (NumberFormatException invalid) {
            log.warn("[에이전트 이벤트 알림] 잘못된 Redis 신호 형식");
        } catch (RuntimeException failure) {
            // Pub/sub has no retry. Heartbeat catch-up is the durable fallback.
            log.warn("[에이전트 이벤트 알림] 원격 이벤트 전달 실패. signal={}", value, failure);
        }
    }
}
