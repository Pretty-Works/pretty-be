package HK.PrettyWorks_BE.agent.interaction.application;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalTokenServiceTest {
    @Test
    void recoversTheSameTokenWithoutPersistingItsPlaintext() {
        AgentInteractionRepository repository = mock(AgentInteractionRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        AgentInteractionEntity interaction = AgentInteractionEntity.builder()
                .runId(10L)
                .kind(AgentInteractionKind.APPROVAL)
                .label("업무 생성")
                .toolCallId("call-1")
                .tool("task.create")
                .access(AgentAccessType.WRITE)
                .paramsCanonical("{}")
                .paramsHash("hash")
                .autoApproved(true)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .resolvedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(interaction, "id", 99L);
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.of(interaction));
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        ApprovalTokenService service = new ApprovalTokenService(
                repository, transactionManager, 10, secret);

        ApprovalTokenService.IssuedToken first = service.issue(99L);
        ApprovalTokenService.IssuedToken recovered = service.issue(99L);

        assertThat(recovered).isEqualTo(first);
        assertThat(interaction.getTokenHash()).isNotEqualTo(first.token());
        assertThat(first.toString()).doesNotContain(first.token());
    }
}
