package HK.PrettyWorks_BE.agent.repository;

// 스레드 상세에서 말풍선에 붙일 첨부 목록 JPQL 프로젝션 결과.
// 메시지 목록과 같은 이유로 엔티티가 아니라 프로젝션으로 읽는다(AgentMessageRow 주석 참고).
public record AgentMessageAttachmentRow(
        Long messageId,
        String filename,
        String contentType,
        Long sizeBytes
) {
}
