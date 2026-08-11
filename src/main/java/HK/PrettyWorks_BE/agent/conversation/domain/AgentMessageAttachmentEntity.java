package HK.PrettyWorks_BE.agent.conversation.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 메시지에 붙었던 첨부 파일 한 개의 메타데이터입니다.
 *
 * <p>파일 내용은 저장하지 않습니다. BE는 받아서 에이전트로 넘기는 게이트일 뿐이고,
 * 내용까지 보관하면 저장소 수명·삭제·용량이 전부 우리 책임이 됩니다.
 * 여기 남기는 것은 대화를 다시 열었을 때 "무엇을 붙였는지" 말풍선에 그리기 위한 최소 정보입니다.
 * 다시 내려받기가 필요해지면 그때 별도 저장소(S3 등)를 붙이고 이 행에 키를 더하면 됩니다.</p>
 */
@Entity
@Table(name = "agent_message_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentMessageAttachmentEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 소속 말풍선. FK는 raw Long 컬럼으로 유지(기존 컨벤션).
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    // 한 메시지 안에서의 표시 순서(0부터). id 정렬로도 되지만, 사용자가 고른 순서를
    // 그대로 보장한다는 뜻을 컬럼으로 남긴다.
    @Column(name = "seq", nullable = false)
    private Integer seq;

    // 업로드 당시 파일명(경로 제거 후). AgentAttachmentIntake가 검증한 값만 들어온다.
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    // 확장자를 보고 서버가 정한 MIME 타입. 클라이언트가 보낸 Content-Type이 아니다.
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Builder
    public AgentMessageAttachmentEntity(
            Long messageId,
            Integer seq,
            String filename,
            String contentType,
            Long sizeBytes
    ) {
        this.messageId = messageId;
        this.seq = seq;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }
}
