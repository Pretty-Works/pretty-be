package HK.PrettyWorks_BE.agent.client;

/**
 * SSE 연결 자체가 아니라 이벤트 한 건이 공동 규격을 위반했음을 나타냅니다.
 * 스트림 소비자는 이 예외를 센 뒤 세 번째 위반에서 AGENT_007로 실행을 중단합니다.
 */
public class AgentServerEventDecodingException extends RuntimeException {
    public AgentServerEventDecodingException(String message) {
        super(message);
    }

    public AgentServerEventDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
