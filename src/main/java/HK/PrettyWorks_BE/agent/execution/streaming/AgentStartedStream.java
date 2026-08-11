package HK.PrettyWorks_BE.agent.execution.streaming;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public record AgentStartedStream(String runId, SseEmitter emitter) {
}
