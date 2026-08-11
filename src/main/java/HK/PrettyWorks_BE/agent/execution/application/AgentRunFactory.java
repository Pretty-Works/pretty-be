package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageAttachmentEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageAttachmentRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentRunFactory {

    // 한 사람이 한도를 안 넘어도 여럿이 겹치면 FastAPI·LLM 쪽이 먼저 무너져 총량도 함께 막습니다.
    @Value("${agent.run.max-active-per-user}")
    private long maxActiveRunsPerUser;

    @Value("${agent.run.max-active-total}")
    private long maxActiveRunsTotal;

    private final CurrentUserService currentUserService;
    private final AgentConversationRepository conversationRepository;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentMessageAttachmentRepository attachmentRepository;
    private final AgentAccessGuard accessGuard;

    /**
     * 실행과 사용자 말풍선을 한 트랜잭션에 만듭니다.
     *
     * <p>{@code goal}은 이미 호출부에서 확정된 값입니다 — 사용자가 아무것도 입력하지 않고 파일만
     * 보낸 경우에도 여기서는 빈 값이 오지 않습니다(AgentExecutionService가 첨부 안내 문구로 채웁니다).
     * 그 덕분에 goal 이 NOT NULL 인 이 테이블도, 맥락 조회도 첨부 여부를 몰라도 됩니다.</p>
     *
     * <p>{@code files}는 메타데이터만 저장합니다. 파일 내용은 이 트랜잭션을 지나 FastAPI로 흘러갈 뿐
     * DB에 남지 않습니다(AgentMessageAttachmentEntity 주석 참고).</p>
     */
    @Transactional
    public StartedRun start(Long userId, Long conversationId, String goal,
                            String screenContext, String sessionId,
                            List<AgentRunRequest.AttachedFile> files) {
        currentUserService.getEmployedUserForUpdate(userId);

        LocalDateTime now = LocalDateTime.now();
        // 기존 대화가 없으면 새 대화 생성
        AgentConversationEntity conversation = conversationId == null
                ? conversationRepository.save(AgentConversationEntity.builder()
                        .userId(userId)
                        .title(fallbackTitle(goal))
                        .lastMessageAt(now)
                        .autoApprove(false)
                        .build())
                : accessGuard.conversationForUpdate(conversationId, userId);

        if (runRepository.existsByConversationIdAndStatusIn(
                conversation.getId(), AgentRunStatus.activeStatuses())) {
            throw BaseException.type(AgentErrorCode.RUN_IN_PROGRESS);
        }
        if (runRepository.countByUserIdAndStatusIn(userId, AgentRunStatus.activeStatuses())
                >= maxActiveRunsPerUser) {
            throw BaseException.type(AgentErrorCode.TOO_MANY_RUNS);
        }
        if (runRepository.countByStatusIn(AgentRunStatus.activeStatuses())
                >= maxActiveRunsTotal) {
            throw BaseException.type(AgentErrorCode.SERVER_BUSY);
        }

        AgentRunEntity run = runRepository.save(AgentRunEntity.builder()
                .runId(UUID.randomUUID().toString())
                .conversationId(conversation.getId())
                .userId(userId)
                .sessionId(sessionId)
                .goal(goal)
                .screenContext(screenContext)
                .startedAt(now)
                .build());
        AgentMessageEntity userMessage = messageRepository.save(AgentMessageEntity.builder()
                .conversationId(conversation.getId())
                .runId(run.getId())
                .role(AgentRole.USER)
                .content(goal)
                .build());
        saveAttachments(userMessage.getId(), files);
        conversation.touch(now);
        return new StartedRun(conversation, run, userMessage);
    }

    // 첨부는 없는 것이 정상이라 없으면 조용히 지나간다. 순서는 사용자가 고른 순서 그대로 둔다.
    private void saveAttachments(Long messageId, List<AgentRunRequest.AttachedFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<AgentMessageAttachmentEntity> rows = new ArrayList<>(files.size());
        for (int seq = 0; seq < files.size(); seq++) {
            AgentRunRequest.AttachedFile file = files.get(seq);
            rows.add(AgentMessageAttachmentEntity.builder()
                    .messageId(messageId)
                    .seq(seq)
                    .filename(file.filename())
                    .contentType(file.contentType())
                    .sizeBytes(file.sizeBytes())
                    .build());
        }
        attachmentRepository.saveAll(rows);
    }

    private String fallbackTitle(String goal) {
        return goal == null || goal.isBlank() ? "새 에이전트 대화" : goal;
    }

    public record StartedRun(AgentConversationEntity conversation, AgentRunEntity run,
                             AgentMessageEntity userMessage) {}
}
