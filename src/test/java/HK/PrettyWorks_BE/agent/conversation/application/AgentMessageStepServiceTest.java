package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageStepEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageStepRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentMessageStepServiceTest {
    private final AgentMessageStepRepository repository = mock(AgentMessageStepRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentMessageStepService service =
            new AgentMessageStepService(repository, objectMapper);

    @Test
    void storesEachStepAsAnOrderedRow() {
        ArrayNode steps = objectMapper.createArrayNode();
        steps.add("first");
        steps.addObject().put("text", "second");

        service.savePayload(10L, steps);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentMessageStepEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(AgentMessageStepEntity::getMessageId)
                .containsExactly(10L, 10L);
        assertThat(captor.getValue()).extracting(AgentMessageStepEntity::getSeq)
                .containsExactly(1L, 2L);
        assertThat(captor.getValue()).extracting(AgentMessageStepEntity::getPayload)
                .containsExactly("\"first\"", "{\"text\":\"second\"}");
    }

    @Test
    void rejectsAnOversizedStepBeforeWritingAnything() {
        ArrayNode steps = objectMapper.createArrayNode();
        steps.add("x".repeat(5 * 1024));

        assertThatThrownBy(() -> service.savePayload(10L, steps))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(AgentErrorCode.AGENT_RESPONSE_INVALID));
        verifyNoInteractions(repository);
    }
}
