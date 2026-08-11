package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageAttachmentRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageStepRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentConversationRunRow;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentLastAgentMessageRow;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentEventRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.projection.AgentEventSeqRow;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentApprovalRow;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingApprovalRow;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingInteractionRow;
import HK.PrettyWorks_BE.agent.shared.json.AgentJsonSupport;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentQueryServiceTest {
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentMessageStepRepository stepRepository =
            mock(AgentMessageStepRepository.class);
    private final AgentMessageAttachmentRepository attachmentRepository =
            mock(AgentMessageAttachmentRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentEventRepository eventRepository = mock(AgentEventRepository.class);
    private final AgentInteractionRepository interactionRepository =
            mock(AgentInteractionRepository.class);
    private final AgentAccessGuard accessGuard = mock(AgentAccessGuard.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentQueryService service = new AgentQueryService(
            conversationRepository, messageRepository, stepRepository, attachmentRepository,
            runRepository, eventRepository, interactionRepository, accessGuard,
            new AgentJsonSupport(new ObjectMapper()), currentUserService);

    @Test
    void conversationProjectionIncludesLatestRunAndPendingApproval() {
        AgentConversationEntity conversation = conversation(true);
        when(conversationRepository.findScroll(eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(conversation));
        when(runRepository.findRunRowsByConversationIdIn(eq(List.of(20L)))).thenReturn(List.of(
                new AgentConversationRunRow(20L, 7L, "run-public-1", AgentRunStatus.WAITING_APPROVAL),
                // 같은 대화의 예전 실행. id가 작으므로 무시돼야 한다.
                new AgentConversationRunRow(20L, 3L, "run-public-0", AgentRunStatus.COMPLETED)));
        when(interactionRepository.findPendingApprovalRows(eq(List.of(7L)),
                eq(AgentInteractionKind.APPROVAL), eq(AgentInteractionStatus.PENDING)))
                .thenReturn(List.of(new AgentPendingApprovalRow(7L, 44L)));

        var response = service.getConversations(1L, null, 20);

        assertThat(response.hasNext()).isFalse();
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.conversationId()).isEqualTo(20L);
            assertThat(item.runId()).isEqualTo("run-public-1");
            assertThat(item.status()).isEqualTo(AgentRunStatus.WAITING_APPROVAL);
            assertThat(item.pendingApprovalId()).isEqualTo(44L);
        });
        verify(currentUserService).getEmployedUser(1L);
    }

    // 실행이 한 번도 없던 대화도 목록에는 나와야 한다.
    @Test
    void conversationWithoutRunKeepsNullStatus() {
        when(conversationRepository.findScroll(eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(conversation(false)));
        when(runRepository.findRunRowsByConversationIdIn(eq(List.of(20L)))).thenReturn(List.of());

        var response = service.getConversations(1L, null, 20);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isNull();
            assertThat(item.runId()).isNull();
            assertThat(item.pendingApprovalId()).isNull();
        });
    }

    // 첫 페이지는 커서 없이 조회한다. 다음 페이지가 있는지 보려고 한 건을 더 가져오고,
    // 더 가져온 그 한 건은 목록에서 잘라낸 뒤 hasNext만 켠다.
    @Test
    void firstPageQueriesWithoutCursorAndTrimsTheProbeRow() {
        when(conversationRepository.findScroll(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(conversation(false), conversation(false)));
        when(runRepository.findRunRowsByConversationIdIn(any())).thenReturn(List.of());

        var response = service.getConversations(1L, null, 1);

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasNext()).isTrue();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(conversationRepository).findScroll(eq(1L), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    // 다음 커서는 마지막 항목의 정렬 키 두 개(lastMessageAt, id)를 그대로 얼린 값이다.
    // id만으로는 부족하다 — 그 대화에 답변이 도착해 맨 위로 올라가면 기준점까지 함께 움직인다.
    @Test
    void nextCursorFreezesSortKeysOfLastItem() {
        AgentConversationEntity conversation = conversation(false);
        ReflectionTestUtils.setField(conversation, "lastMessageAt",
                LocalDateTime.of(2026, 8, 2, 14, 25, 30));
        listing(conversation);

        var response = service.getConversations(1L, null, 20);

        assertThat(response.nextCursor()).isEqualTo("2026-08-02T14:25:30_20");
    }

    // 받은 커서는 해석해서 쿼리의 경계로 넘긴다. 응답의 nextCursor를 그대로 되돌려 줘도 값이 변하지 않는다.
    @Test
    void suppliedCursorBecomesQueryBoundary() {
        listing(conversation(false));

        service.getConversations(1L, "2026-08-02T14:25:30_12", 20);

        verify(conversationRepository).findScroll(eq(1L),
                eq(LocalDateTime.of(2026, 8, 2, 14, 25, 30)), eq(12L), any(Pageable.class));
    }

    // 손댄 커서는 조용히 첫 페이지로 되돌리지 않고 거부한다.
    @Test
    void malformedCursorIsRejected() {
        assertThatThrownBy(() -> service.getConversations(1L, "not-a-cursor", 20))
                .isInstanceOfSatisfying(BaseException.class, error ->
                        assertThat(error.getCode()).isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
    }

    // 대화가 하나도 없으면 이어받을 지점도 없다.
    @Test
    void emptyListingHasNoCursor() {
        when(conversationRepository.findScroll(eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        var response = service.getConversations(1L, null, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    // 읽음 지점보다 뒤에 온 에이전트 답변이 '새 답장' 표시를 켠다.
    @Test
    void agentReplyAfterLastReadMarksConversationUnread() {
        AgentConversationEntity conversation = conversation(false);
        ReflectionTestUtils.setField(conversation, "lastReadMessageId", 100L);
        listing(conversation);
        when(messageRepository.findLastAgentMessageRows(eq(List.of(20L)), eq(AgentRole.AGENT)))
                .thenReturn(List.of(new AgentLastAgentMessageRow(20L, 102L)));

        var response = service.getConversations(1L, null, 20);

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.unread()).isTrue());
    }

    // 읽은 뒤 내가 질문만 더 보낸 대화는 안 읽음이 아니다 — 마지막 AGENT 메시지가 읽음 지점 이전이다.
    @Test
    void ownMessageAfterLastReadDoesNotMarkUnread() {
        AgentConversationEntity conversation = conversation(false);
        ReflectionTestUtils.setField(conversation, "lastReadMessageId", 102L);
        listing(conversation);
        when(messageRepository.findLastAgentMessageRows(eq(List.of(20L)), eq(AgentRole.AGENT)))
                .thenReturn(List.of(new AgentLastAgentMessageRow(20L, 102L)));

        var response = service.getConversations(1L, null, 20);

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.unread()).isFalse());
    }

    // 답변이 한 번도 없던 대화는 읽은 적이 없어도 안 읽음이 아니다.
    @Test
    void conversationWithoutAgentReplyIsNeverUnread() {
        listing(conversation(false));
        when(messageRepository.findLastAgentMessageRows(eq(List.of(20L)), eq(AgentRole.AGENT)))
                .thenReturn(List.of());

        var response = service.getConversations(1L, null, 20);

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.unread()).isFalse());
    }

    // 목록 한 건짜리 공통 스텁. 실행·승인은 안 읽음·커서 판정과 무관하다.
    private void listing(AgentConversationEntity conversation) {
        when(conversationRepository.findScroll(eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(conversation));
        when(runRepository.findRunRowsByConversationIdIn(eq(List.of(20L)))).thenReturn(List.of());
    }

    @Test
    void pendingProjectionReturnsRenderablePayloadAndBadgeCount() {
        LocalDateTime now = LocalDateTime.now();
        when(interactionRepository.findPendingRows(eq(1L),
                eq(AgentInteractionStatus.PENDING), any(Collection.class),
                any(LocalDateTime.class))).thenReturn(List.of(
                new AgentPendingInteractionRow(30L, AgentInteractionKind.APPROVAL,
                        "할 일 저장",
                        "{\"approvalId\":30,\"previewText\":\"· 첫 번째\","
                                + "\"alternatives\":[{\"id\":\"FILL_FORM\",\"label\":\"직접 고칠래요\"}]}",
                        "run-public-1", 20L, "대화", now.plusMinutes(30), now)));

        var response = service.getPendingInteractions(1L);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.interactionId()).isEqualTo(30L);
            assertThat(item.previewText()).isEqualTo("· 첫 번째");
            assertThat(item.multiple()).isFalse();
            assertThat(item.requestedAt()).isEqualTo(now);
            assertThat(item.options()).extracting("id")
                    .containsExactly("APPROVE", "FILL_FORM", "REJECT");
        });
    }

    // 에이전트가 options를 완성해 보내면 승인·거절을 덧붙이지 않고 그대로 통과시킨다.
    @Test
    void pendingProjectionKeepsAgentSuppliedOptions() {
        LocalDateTime now = LocalDateTime.now();
        when(interactionRepository.findPendingRows(eq(1L),
                eq(AgentInteractionStatus.PENDING), any(Collection.class),
                any(LocalDateTime.class))).thenReturn(List.of(
                new AgentPendingInteractionRow(51L, AgentInteractionKind.QUESTION,
                        "휴가 날짜 선택",
                        "{\"questionId\":51,\"multiple\":true,\"options\":["
                                + "{\"id\":\"2026-03-02\",\"label\":\"3/2 (화)\"},"
                                + "{\"id\":\"__FREE__\",\"label\":\"직접 입력\"}]}",
                        "run-public-2", 15L, "8월 휴가 일정 추천", now.plusMinutes(30), now)));

        var response = service.getPendingInteractions(1L);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.multiple()).isTrue();
            assertThat(item.previewText()).isNull();
            assertThat(item.options()).extracting("id")
                    .containsExactly("2026-03-02", "__FREE__");
        });
    }

    // 대화 상세는 답이 끝난 승인 카드도 결과와 함께 복원한다.
    @Test
    void messagesIncludeResolvedApprovalCards() {
        AgentConversationEntity conversation = conversation(false);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 8, 2, 14, 25, 12);
        when(accessGuard.conversation(eq(20L), eq(1L))).thenReturn(conversation);
        when(runRepository.findByConversationIdAndStatusIn(eq(20L), any(Collection.class)))
                .thenReturn(List.of());
        when(messageRepository.findMessageRows(eq(20L))).thenReturn(List.of());
        when(interactionRepository.findApprovalRows(eq(20L), eq(AgentInteractionKind.APPROVAL)))
                .thenReturn(List.of(new AgentApprovalRow(42L, 55L, AgentAccessType.WRITE,
                        "회의록 저장 · 스프린트 리뷰 4차", "· 회의명: 스프린트 리뷰 4차",
                        "{\"alternatives\":[{\"id\":\"FILL_FORM\",\"label\":\"작성 화면에서 직접 고칠래요\"}]}",
                        AgentInteractionStatus.ALTERNATIVE, "FILL_FORM", decidedAt)));
        when(eventRepository.findSeqRows(eq(List.of(55L)), eq("approval_request")))
                .thenReturn(List.of(new AgentEventSeqRow(5L, "{\"approvalId\":42}")));

        var response = service.getMessages(1L, 20L);

        assertThat(response.approvals()).singleElement().satisfies(approval -> {
            assertThat(approval.approvalId()).isEqualTo(42L);
            assertThat(approval.seq()).isEqualTo(5L);
            assertThat(approval.access()).isEqualTo(AgentAccessType.WRITE);
            assertThat(approval.summary()).isEqualTo("회의록 저장 · 스프린트 리뷰 4차");
            assertThat(approval.status()).isEqualTo(AgentInteractionStatus.ALTERNATIVE);
            assertThat(approval.chosenAlternativeId()).isEqualTo("FILL_FORM");
            assertThat(approval.decidedAt()).isEqualTo(decidedAt);
            assertThat(approval.alternatives()).extracting("id").containsExactly("FILL_FORM");
        });
    }

    // 이벤트가 이미 정리된 오래된 카드는 seq 없이 내려간다 — 목록 자체가 사라지면 안 된다.
    @Test
    void approvalWithoutEventKeepsNullSeq() {
        when(accessGuard.conversation(eq(20L), eq(1L))).thenReturn(conversation(false));
        when(runRepository.findByConversationIdAndStatusIn(eq(20L), any(Collection.class)))
                .thenReturn(List.of());
        when(messageRepository.findMessageRows(eq(20L))).thenReturn(List.of());
        when(interactionRepository.findApprovalRows(eq(20L), eq(AgentInteractionKind.APPROVAL)))
                .thenReturn(List.of(new AgentApprovalRow(42L, 55L, AgentAccessType.WRITE,
                        "회의록 저장", null, null, AgentInteractionStatus.APPROVED, null, null)));
        when(eventRepository.findSeqRows(eq(List.of(55L)), eq("approval_request")))
                .thenReturn(List.of());

        var response = service.getMessages(1L, 20L);

        assertThat(response.approvals()).singleElement().satisfies(approval -> {
            assertThat(approval.seq()).isNull();
            assertThat(approval.alternatives()).isEmpty();
        });
    }

    private AgentConversationEntity conversation(boolean autoApprove) {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now())
                .autoApprove(autoApprove)
                .build();
        ReflectionTestUtils.setField(conversation, "id", 20L);
        return conversation;
    }

    private AgentRunEntity run(AgentRunStatus status) {
        AgentRunEntity run = AgentRunEntity.builder()
                .runId("run-public-1")
                .conversationId(20L)
                .userId(1L)
                .goal("업무 처리")
                .startedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(run, "id", 10L);
        ReflectionTestUtils.setField(run, "status", status);
        return run;
    }
}
