package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageStepEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageStepRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.limit.AgentPayloadLimits;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentMessageStepService {
    private final AgentMessageStepRepository stepRepository;
    private final ObjectMapper objectMapper;

    public void savePayload(Long messageId, JsonNode steps) {
        if (steps == null || steps.isNull()) {
            return;
        }
        if (!steps.isArray() || steps.size() > AgentPayloadLimits.MAX_STEPS_PER_RUN) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        if (steps.isEmpty()) {
            return;
        }
        List<AgentMessageStepEntity> rows = new ArrayList<>(steps.size());
        long sequence = 1L;
        for (JsonNode step : steps) {
            if (step == null || step.isNull()) {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            String payload = objectMapper.writeValueAsString(step);
            if (payload.getBytes(StandardCharsets.UTF_8).length
                    > AgentPayloadLimits.MAX_STEP_DATA_BYTES) {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            rows.add(AgentMessageStepEntity.builder()
                    .messageId(messageId)
                    .seq(sequence++)
                    .payload(payload)
                    .build());
        }
        stepRepository.saveAll(rows);
    }

    public void copyFromRun(Long messageId, Long runId) {
        stepRepository.copyFromRun(messageId, runId);
    }
}
