package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentContextRow;
import HK.PrettyWorks_BE.agent.execution.api.request.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.shared.attachment.AgentAttachmentIntake;
import HK.PrettyWorks_BE.agent.shared.attachment.AgentFileEncoding;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
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

    // 인테이크는 진짜 객체를 쓴다. "파일만 보내기"의 동작이 검증 규칙과 맞물려 있어,
    // 목으로 대체하면 정작 확인하고 싶은 조합(파일 통과 → goal 생성)이 검증되지 않는다.
    private final AgentAttachmentIntake attachmentIntake =
            new AgentAttachmentIntake(new String[]{"txt"}, 3, 1024, 2048);

    private AgentExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AgentExecutionService(runFactory, messageRepository, streamService,
                segmentExecutor, attachmentIntake, objectMapper, 20, 4_096);
    }

    @Test
    void rejectsInvalidScreenContextBeforeCreatingRun() {
        AgentMessageRequest request = new AgentMessageRequest(
                null, "업무를 정리해줘", objectMapper.createObjectNode());

        assertThatThrownBy(() -> service.start(1L, "session-1", request, List.of()))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(runFactory, streamService, segmentExecutor);
    }

    @Test
    void persistsRunFirstAndRelaysOnlyPreviousConversationContext() throws Exception {
        AgentRunFactory.StartedRun started = startedRun();
        when(runFactory.start(eq(1L), eq(10L), eq("업무를 정리해줘"),
                anyString(), eq("session-1"), eq(List.of()))).thenReturn(started);
        when(messageRepository.findRecentContextBeforeMessage(eq(10L), eq(30L), any()))
                .thenReturn(List.of(
                        new AgentContextRow(AgentRole.AGENT, "두 번째"),
                        new AgentContextRow(AgentRole.USER, "첫 번째")));
        when(streamService.connect(1L, "run-public-1", null)).thenReturn(new SseEmitter());
        AgentMessageRequest request = new AgentMessageRequest(10L, "업무를 정리해줘",
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"));
        AgentStartedStream response =
                service.start(1L, "session-1", request, List.of());

        assertThat(response.runId()).isEqualTo("run-public-1");
        ArgumentCaptor<AgentRunRequest> sent = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(segmentExecutor).submitStart(eq(20L), eq("run-public-1"), sent.capture());
        assertThat(sent.getValue().messages()).containsExactly(
                new AgentRunRequest.ContextMessage("USER", "첫 번째"),
                new AgentRunRequest.ContextMessage("AGENT", "두 번째"));
        assertThat(sent.getValue().goal()).isEqualTo("업무를 정리해줘");
        assertThat(sent.getValue().conversationId()).isEqualTo(10L);
        assertThat(sent.getValue().files()).isEmpty();
        verify(messageRepository).findRecentContextBeforeMessage(eq(10L), eq(30L), any());
    }

    // 파일과 메시지를 함께 보낸 경우. 파일 내용은 해석하지 않고 그대로 실려 나가야 한다.
    @Test
    void relaysAttachedTextFileAlongsideTheMessage() {
        AgentRunFactory.StartedRun started = startedRun();
        when(runFactory.start(eq(1L), eq(10L), eq("이 파일 요약해줘"),
                anyString(), eq("session-1"), any())).thenReturn(started);
        when(messageRepository.findRecentContextBeforeMessage(eq(10L), eq(30L), any()))
                .thenReturn(List.of());
        when(streamService.connect(1L, "run-public-1", null)).thenReturn(new SseEmitter());
        AgentMessageRequest request = new AgentMessageRequest(10L, "이 파일 요약해줘",
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"));

        service.start(1L, "session-1", request, List.of(textFile("회의록.txt", "회의 내용입니다")));

        ArgumentCaptor<AgentRunRequest> sent = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(segmentExecutor).submitStart(eq(20L), eq("run-public-1"), sent.capture());
        assertThat(sent.getValue().goal()).isEqualTo("이 파일 요약해줘");
        assertThat(sent.getValue().files()).singleElement().satisfies(file -> {
            assertThat(file.filename()).isEqualTo("회의록.txt");
            assertThat(file.contentType()).isEqualTo("text/plain");
            assertThat(file.encoding()).isEqualTo(AgentFileEncoding.TEXT);
            assertThat(file.content()).isEqualTo("회의 내용입니다");
        });
    }

    // 파일만 보낸 경우. 저장·전달 계약이 전부 goal 을 필수로 보므로 여기서 문구가 채워져야 한다.
    @Test
    void fillsGoalFromFilenamesWhenOnlyFilesAreSent() {
        AgentRunFactory.StartedRun started = startedRun();
        when(runFactory.start(eq(1L), eq(10L), eq("(첨부 파일: 회의록.txt)"),
                anyString(), eq("session-1"), any())).thenReturn(started);
        when(messageRepository.findRecentContextBeforeMessage(eq(10L), eq(30L), any()))
                .thenReturn(List.of());
        when(streamService.connect(1L, "run-public-1", null)).thenReturn(new SseEmitter());
        AgentMessageRequest request = new AgentMessageRequest(10L, "   ",
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"));

        service.start(1L, "session-1", request, List.of(textFile("회의록.txt", "회의 내용입니다")));

        ArgumentCaptor<AgentRunRequest> sent = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(segmentExecutor).submitStart(eq(20L), eq("run-public-1"), sent.capture());
        assertThat(sent.getValue().goal()).isEqualTo("(첨부 파일: 회의록.txt)");
        assertThat(sent.getValue().files()).hasSize(1);
        verify(runFactory).start(eq(1L), eq(10L), eq("(첨부 파일: 회의록.txt)"),
                anyString(), eq("session-1"), any());
    }

    @Test
    void rejectsRequestWithNeitherMessageNorFile() {
        AgentMessageRequest request = new AgentMessageRequest(10L, "  ",
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"));

        assertThatThrownBy(() -> service.start(1L, "session-1", request, List.of()))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(runFactory, streamService, segmentExecutor);
    }

    // 파일이 있어도 한 글자짜리 요청은 그대로 막는다(첨부 도입 전 규칙 유지).
    @Test
    void keepsMinimumLengthRuleForNonBlankMessages() {
        AgentMessageRequest request = new AgentMessageRequest(10L, "ㅇ",
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"));

        assertThatThrownBy(() -> service.start(1L, "session-1", request,
                List.of(textFile("회의록.txt", "내용"))))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(runFactory, streamService, segmentExecutor);
    }

    private MultipartFile textFile(String filename, String content) {
        return new MockMultipartFile("files", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
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
