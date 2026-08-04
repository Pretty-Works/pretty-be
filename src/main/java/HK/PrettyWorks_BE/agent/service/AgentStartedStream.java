package HK.PrettyWorks_BE.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public record AgentStartedStream(String runId, SseEmitter emitter) {
}
