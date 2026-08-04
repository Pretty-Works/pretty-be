package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.domain.AgentEventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AgentEventSignalPublisher {
    private final StringRedisTemplate redisTemplate;
    private final String channel;
    private final ExecutorService publisher;

    public AgentEventSignalPublisher(StringRedisTemplate redisTemplate,
                                     @Value("${agent.events.channel:agent-events-v2}") String channel) {
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.publisher = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1024), runnable -> {
                    Thread thread = new Thread(runnable, "agent-event-signal-publisher");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void publish(AgentEventEntity event) {
        try {
            publisher.execute(() -> publishNow(event));
        } catch (RejectedExecutionException overloadedOrStopping) {
            log.warn("[에이전트 이벤트 알림] Redis 발행 대기열 포화. runId={} seq={}",
                    event.getRunId(), event.getSeq());
        }
    }

    private void publishNow(AgentEventEntity event) {
        try {
            redisTemplate.convertAndSend(channel, event.getRunId() + ":" + event.getSeq());
        } catch (RuntimeException unavailable) {
            // DB is authoritative. Remote instances recover a missed signal on heartbeat.
            log.warn("[에이전트 이벤트 알림] Redis 발행 실패. runId={} seq={}",
                    event.getRunId(), event.getSeq(), unavailable);
        }
    }

    @PreDestroy
    public void shutdown() {
        publisher.shutdownNow();
    }
}
