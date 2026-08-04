package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.client.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.dto.req.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.repository.AgentContextRow;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentExecutionServiceTest {
    private final AgentRunFactory runFactory = mock(AgentRunFactory.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentStreamService streamService = mock(AgentStreamService.class);
    private final AgentSegmentExecutor segmentExecutor = mock(AgentSegmentExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AgentExecutionService(runFactory, messageRepository, streamService,
                segmentExecutor, objectMapper, 20, 4_096);
    }

    @Test
    void rejectsInvalidScreenContextBeforeCreatingRun() {
        AgentMessageRequest request = new AgentMessageRequest(
                null, "업무를 정리해줘", objectMapper.createObjectNode());

        assertThatThrownBy(() -> service.start(1L, "session-1", request))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(runFactory, streamService, segmentExecutor);
    }

    @Test
    void persistsRunFirstAndRelaysOnlyPreviousConversationContext() throws Exception {
        AgentRunFactory.StartedRun started = startedRun();
        when(runFactory.start(eq(1L), eq(10L), eq("업무를 정리해줘"),
                anyString(), eq("session-1"))).thenReturn(started);
        when(messageRepository.findRecentContextBeforeMessage(eq(10L), eq(30L), any()))
                .thenReturn(List.of(
                        new AgentContextRow(AgentRole.AGENT, "두 번째"),
                        new AgentContextRow(AgentRole.USER, "첫 번째")));
        when(streamService.connect(1L, "run-public-1", null)).thenReturn(new SseEmitter());
        AgentMessageRequest request = new AgentMessageRequest(10L, "업무를 정리해줘",
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"));
        AgentStartedStream response =
                service.start(1L, "session-1", request);

        assertThat(response.runId()).isEqualTo("run-public-1");
        ArgumentCaptor<AgentRunRequest> sent = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(segmentExecutor).submitStart(eq(20L), eq("run-public-1"), sent.capture());
        assertThat(sent.getValue().messages()).containsExactly(
                new AgentRunRequest.ContextMessage("USER", "첫 번째"),
                new AgentRunRequest.ContextMessage("AGENT", "두 번째"));
        assertThat(sent.getValue().goal()).isEqualTo("업무를 정리해줘");
        verify(messageRepository).findRecentContextBeforeMessage(eq(10L), eq(30L), any());
    }

    private AgentRunFactory.StartedRun startedRun() {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now())
                .autoApprove(false)
                .build();
        ReflectionTestUtils.setField(conversation, "id", 10L);

        AgentRunEntity run = AgentRunEntity.builder()
                .runId("run-public-1")
                .conversationId(10L)
                .userId(1L)
                .goal("업무를 정리해줘")
                .startedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(run, "id", 20L);

        AgentMessageEntity message = AgentMessageEntity.builder()
                .conversationId(10L)
                .runId(20L)
                .role(AgentRole.USER)
                .content("업무를 정리해줘")
                .build();
        ReflectionTestUtils.setField(message, "id", 30L);
        return new AgentRunFactory.StartedRun(conversation, run, message);
    }
}
