package HK.PrettyWorks_BE.agent.execution.gateway.dto;

import tools.jackson.databind.node.ObjectNode;

/**
 * 의미 검증 결과와 원본 payload 스냅샷을 함께 보관합니다.
 *
 * <p>상태 전이는 {@link #event()}의 타입을 사용하고, FE 릴레이는 {@link #payload()}에
 * BE 소유 식별자와 서버가 다시 렌더한 승인 문구를 반영해 사용합니다. 그래야 FastAPI가 추가한
 * 미지의 필드를 BE가 버리지 않습니다.</p>
 */
public record DecodedAgentServerEvent(AgentServerEvent event, ObjectNode payload) {
    public DecodedAgentServerEvent {
        if (event == null || payload == null) {
            throw new IllegalArgumentException("event and payload must not be null");
        }
        payload = payload.deepCopy();
    }

    @Override
    public ObjectNode payload() {
        return payload.deepCopy();
    }
}
