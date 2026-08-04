package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.client.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.dto.req.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentContextRow;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentExecutionService {
    private static final String REQUEST_SOURCE_WEB = "WEB";
    private static final String LOCALE_KO = "ko-KR";

    private final AgentRunFactory runFactory;
    private final AgentMessageRepository messageRepository;
    private final AgentStreamService streamService;
    private final AgentSegmentExecutor segmentExecutor;
    private final ObjectMapper objectMapper;
    private final int maxContextMessages;
    private final int maxScreenContextBytes;

    public AgentExecutionService(
            AgentRunFactory runFactory,
            AgentMessageRepository messageRepository,
            AgentStreamService streamService,
            AgentSegmentExecutor segmentExecutor,
            ObjectMapper objectMapper,
            @Value("${agent.context.max-messages}") int maxContextMessages,
            @Value("${agent.context.max-screen-bytes}") int maxScreenContextBytes
    ) {
        if (maxContextMessages <= 0 || maxScreenContextBytes <= 0) {
            throw new IllegalStateException("agent execution settings must be positive");
        }
        this.runFactory = runFactory;
        this.messageRepository = messageRepository;
        this.streamService = streamService;
        this.segmentExecutor = segmentExecutor;
        this.objectMapper = objectMapper;
        this.maxContextMessages = maxContextMessages;
        this.maxScreenContextBytes = maxScreenContextBytes;
    }

    public AgentStartedStream start(Long userId, String sessionId, AgentMessageRequest request) {
        JsonNode screenContext = validateAndCopyScreenContext(request.screenContext());
        String screenContextJson = objectMapper.writeValueAsString(screenContext);
        AgentRunFactory.StartedRun started = runFactory.start(
                userId, request.conversationId(), request.goal(), screenContextJson, sessionId);
        AgentRunEntity run = started.run();

        final AgentRunRequest serverRequest;
        try {
            serverRequest = AgentRunRequest.builder()
                    .runId(run.getRunId())
                    .goal(request.goal())
                    .messages(loadContext(started))
                    .screenContext(screenContext)
                    .requestSource(REQUEST_SOURCE_WEB)
                    .locale(LOCALE_KO)
                    .build();
        } catch (RuntimeException failure) {
            segmentExecutor.failRun(run.getId(), failure);
            throw failure;
        }

        SseEmitter emitter;
        try {
            // 연결을 먼저 등록해야 매우 빠른 FastAPI 이벤트도 DB replay에만 의존하지 않고 즉시 전달된다.
            emitter = streamService.connect(userId, run.getRunId(), null);
        } catch (RuntimeException failure) {
            segmentExecutor.failRun(run.getId(), failure);
            throw failure;
        }

        segmentExecutor.submitStart(run.getId(), run.getRunId(), serverRequest);
        return new AgentStartedStream(run.getRunId(), emitter);
    }

    private List<AgentRunRequest.ContextMessage> loadContext(AgentRunFactory.StartedRun started) {
        List<AgentContextRow> rows = messageRepository.findRecentContextBeforeMessage(
                started.conversation().getId(), started.userMessage().getId(),
                PageRequest.of(0, maxContextMessages));
        List<AgentRunRequest.ContextMessage> context = new ArrayList<>(rows.size());
        for (int index = rows.size() - 1; index >= 0; index--) {
            AgentContextRow row = rows.get(index);
            context.add(new AgentRunRequest.ContextMessage(row.role().name(), row.content()));
        }
        return context;
    }

    private JsonNode validateAndCopyScreenContext(JsonNode screenContext) {
        if (screenContext == null || !screenContext.isObject()
                || !screenContext.has("screen") || !screenContext.get("screen").isTextual()
                || screenContext.get("screen").textValue().isBlank()) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        JsonNode copy = screenContext.deepCopy();
        int bytes = objectMapper.writeValueAsBytes(copy).length;
        if (bytes > maxScreenContextBytes) {
            throw BaseException.type(AgentErrorCode.SCREEN_CONTEXT_TOO_LARGE);
        }
        return copy;
    }

}
