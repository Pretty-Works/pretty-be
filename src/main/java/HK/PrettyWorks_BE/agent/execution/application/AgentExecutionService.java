package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentContextRow;
import HK.PrettyWorks_BE.agent.execution.api.request.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.shared.attachment.AgentAttachmentIntake;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentExecutionService {
    private static final String REQUEST_SOURCE_WEB = "WEB";
    private static final String LOCALE_KO = "ko-KR";

    // 사용자가 입력한 메시지의 최소 길이. DTO에 @Size(min=..)로 걸 수 없어 여기서 본다 —
    // 파일만 보내는 경우 goal 이 비어 있는 것이 정상이라, 빈 값과 너무 짧은 값을 구분해야 한다.
    private static final int MIN_GOAL_LENGTH = 2;

    // 텍스트 없이 파일만 보냈을 때 말풍선·실행·제목에 남길 문구.
    //
    // 빈 문자열로 두지 않는 이유가 셋 있다. goal 은 agent_runs·agent_messages 에서 NOT NULL 이고,
    // 다음 턴의 대화 맥락(ContextMessage)은 공백 content 를 거부하며, FastAPI 계약도 goal 을
    // 필수로 본다. 세 곳에 각각 예외를 뚫는 대신 시작점에서 한 번 채워 내려보낸다.
    // 파일 내용 자체는 files 로 따로 실려 가므로 이 문구는 "무엇을 붙였는지"만 알린다.
    private static final String ATTACHMENT_ONLY_GOAL_PREFIX = "(첨부 파일: ";
    private static final String ATTACHMENT_ONLY_GOAL_SUFFIX = ")";

    // 파일명 3개가 각각 255자일 수 있어 그대로 이으면 제목 컬럼(100자)을 훌쩍 넘는다.
    // 자르는 일은 엔티티가 하지만, 실행·말풍선에 남는 원문도 읽을 만한 길이로 유지한다.
    private static final int MAX_ATTACHMENT_GOAL_LENGTH = 200;

    private final AgentRunFactory runFactory;
    private final AgentMessageRepository messageRepository;
    private final AgentStreamService streamService;
    private final AgentSegmentExecutor segmentExecutor;
    private final AgentAttachmentIntake attachmentIntake;
    private final ObjectMapper objectMapper;
    private final int maxContextMessages;
    private final int maxScreenContextBytes;

    public AgentExecutionService(
            AgentRunFactory runFactory,
            AgentMessageRepository messageRepository,
            AgentStreamService streamService,
            AgentSegmentExecutor segmentExecutor,
            AgentAttachmentIntake attachmentIntake,
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
        this.attachmentIntake = attachmentIntake;
        this.objectMapper = objectMapper;
        this.maxContextMessages = maxContextMessages;
        this.maxScreenContextBytes = maxScreenContextBytes;
    }

    /**
     * 메시지(그리고 첨부 파일)로 실행을 시작하고 스트림을 엽니다.
     *
     * <p>검증 순서가 곧 "실패했을 때 무엇이 남는가"입니다. 화면 정보 → 첨부 → 메시지 순으로
     * 전부 통과한 뒤에야 실행과 말풍선을 만듭니다. 형식이 틀린 파일 하나 때문에 빈 대화가
     * 목록에 쌓이면 사용자가 지울 방법이 없습니다.</p>
     */
    public AgentStartedStream start(Long userId, String sessionId, AgentMessageRequest request,
                                    List<MultipartFile> files) {
        JsonNode screenContext = validateAndCopyScreenContext(request.screenContext());
        String screenContextJson = objectMapper.writeValueAsString(screenContext);
        List<AgentRunRequest.AttachedFile> attachments = attachmentIntake.intake(files);
        String goal = resolveGoal(request.goal(), attachments);

        AgentRunFactory.StartedRun started = runFactory.start(
                userId, request.conversationId(), goal, screenContextJson, sessionId, attachments);
        AgentRunEntity run = started.run();

        final AgentRunRequest serverRequest;
        try {
            serverRequest = AgentRunRequest.builder()
                    .runId(run.getRunId())
                    .conversationId(started.conversation().getId())
                    .goal(goal)
                    .messages(loadContext(started))
                    .screenContext(screenContext)
                    .requestSource(REQUEST_SOURCE_WEB)
                    .locale(LOCALE_KO)
                    .files(attachments)
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

    /**
     * 이 실행에 쓸 메시지 본문을 확정합니다.
     *
     * <p>"파일만 보내기"를 허용하면서 goal 의 @NotBlank 를 DTO에서 걷어냈습니다. 파일이 있는지는
     * 바디가 아니라 멀티파트 파트에 있어 빈 검증(bean validation)으로는 표현할 수 없기 때문입니다.
     * 그래서 "둘 중 하나는 있어야 한다"는 규칙을 여기서 지킵니다 — 아래를 지나면 goal 은 항상
     * 비어 있지 않으므로, 그 뒤의 저장·전달 계약은 첨부 여부를 전혀 몰라도 됩니다.</p>
     */
    private String resolveGoal(String goal, List<AgentRunRequest.AttachedFile> attachments) {
        String trimmed = goal == null ? "" : goal.trim();
        if (!trimmed.isEmpty()) {
            // 파일이 있어도 "ㅇ" 한 글자짜리 요청은 그대로 막는다(기존 규칙).
            if (trimmed.length() < MIN_GOAL_LENGTH) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }
            return trimmed;
        }
        // 텍스트도 파일도 없으면 에이전트가 할 수 있는 일이 없다.
        if (attachments.isEmpty()) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        return attachmentOnlyGoal(attachments);
    }

    private String attachmentOnlyGoal(List<AgentRunRequest.AttachedFile> attachments) {
        String names = attachments.stream()
                .map(AgentRunRequest.AttachedFile::filename)
                .collect(Collectors.joining(", "));
        String goal = ATTACHMENT_ONLY_GOAL_PREFIX + names + ATTACHMENT_ONLY_GOAL_SUFFIX;
        return goal.length() <= MAX_ATTACHMENT_GOAL_LENGTH
                ? goal
                : goal.substring(0, MAX_ATTACHMENT_GOAL_LENGTH - 1) + ATTACHMENT_ONLY_GOAL_SUFFIX;
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
