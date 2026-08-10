package HK.PrettyWorks_BE.agent.execution.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AgentEventRedisConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService agentEventSubscriberExecutor() {
        return new ThreadPoolExecutor(
                2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                runnable -> {
                    Thread thread = new Thread(runnable, "agent-event-signal-subscriber");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }

    @Bean
    public RedisMessageListenerContainer agentEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            AgentEventSignalSubscriber subscriber,
            ExecutorService agentEventSubscriberExecutor,
            @Value("${agent.events.channel:agent-events-v2}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // The default SimpleAsyncTaskExecutor can create an unbounded number of threads under a
        // Redis burst. Signals are only hints, so bounded overload should discard and let each
        // SSE heartbeat recover the committed DB tail.
        container.setTaskExecutor(agentEventSubscriberExecutor);
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }
}
