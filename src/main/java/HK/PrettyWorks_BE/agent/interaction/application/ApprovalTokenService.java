package HK.PrettyWorks_BE.agent.interaction.application;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.util.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class ApprovalTokenService {
    private static final int TOKEN_BYTES = 32;

    private final AgentInteractionRepository interactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTemplate;
    private final byte[] tokenSecret;
    private final long tokenTtlMinutes;

    public ApprovalTokenService(AgentInteractionRepository interactionRepository,
                                PlatformTransactionManager transactionManager,
                                @Value("${agent.internal.approval-token-ttl-minutes}") long tokenTtlMinutes,
                                @Value("${agent.internal.approval-token-secret}") String encodedTokenSecret) {
        this.interactionRepository = interactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.tokenSecret = decodeSecret(encodedTokenSecret);
    }

    public IssuedToken issue(Long interactionId) {
        IssuedToken issued = transactionTemplate.execute(status -> {
            AgentInteractionEntity interaction = interactionRepository.findByIdForUpdate(interactionId)
                    .orElseThrow(() -> BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND));
            if (!interaction.isWriteApproval()
                    || interaction.getStatus() != AgentInteractionStatus.APPROVED
                    || interaction.getTokenRevokedAt() != null) {
                throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
            }
            LocalDateTime expiresAt = interaction.getTokenExpiresAt();
            if (interaction.getTokenHash() == null) {
                expiresAt = truncateToMicros(LocalDateTime.now().plusMinutes(tokenTtlMinutes));
            }
            if (expiresAt == null) {
                throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
            }
            String token = deriveToken(interaction.getId(), expiresAt);
            String tokenHash = Sha256.hash(token);
            if (interaction.getTokenHash() == null) {
                interaction.issueToken(tokenHash, expiresAt);
            } else if (!interaction.getTokenHash().equals(tokenHash)) {
                throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
            }
            return new IssuedToken(token, expiresAt);
        });
        if (issued == null) {
            throw new IllegalStateException("승인 토큰 발급 결과가 없습니다.");
        }
        return issued;
    }

    public TokenInspection inspect(String rawToken, Long internalRunId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
        }
        AgentInteractionEntity interaction = interactionRepository.findByTokenHash(Sha256.hash(rawToken))
                .orElseThrow(() -> BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN));
        LocalDateTime now = LocalDateTime.now();
        if (!interaction.getRunId().equals(internalRunId)
                || !interaction.isWriteApproval()
                || interaction.getStatus() != AgentInteractionStatus.APPROVED
                || interaction.getTokenRevokedAt() != null) {
            throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
        }

        // A committed result remains replayable even after its one-time token TTL elapsed.
        if (interaction.getExecutedAt() == null) {
            if (interaction.getTokenUsedAt() != null || interaction.tokenExpired(now)) {
                throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
            }
        }
        return new TokenInspection(interaction.getId(), Sha256.hash(rawToken));
    }

    public void revokeInNewTransaction(Long interactionId) {
        requiresNewTemplate.executeWithoutResult(status -> interactionRepository.findByIdForUpdate(interactionId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND))
                .revokeToken(LocalDateTime.now()));
    }

    private String deriveToken(Long interactionId, LocalDateTime expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret, "HmacSHA256"));
            String material = interactionId + ":" + expiresAt;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("승인 토큰 HMAC을 초기화할 수 없습니다.", unavailable);
        }
    }

    private byte[] decodeSecret(String encodedSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedSecret);
            if (decoded.length < TOKEN_BYTES) {
                throw new IllegalArgumentException();
            }
            return decoded;
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(
                    "agent.internal.approval-token-secret must be base64 for at least 32 bytes", invalid);
        }
    }

    private LocalDateTime truncateToMicros(LocalDateTime value) {
        return value.withNano((value.getNano() / 1_000) * 1_000);
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[token=[REDACTED], expiresAt=" + expiresAt + "]";
        }
    }
    public record TokenInspection(Long interactionId, String tokenHash) {}
}
