package HK.PrettyWorks_BE.agent.domain;

import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentMessageEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 소속 스레드. FK는 raw Long 컬럼으로 유지(기존 컨벤션).
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    // v1 메시지는 null이다. 실행 삭제가 대화 기록을 지우지 않도록 물리 FK는 두지 않는다.
    @Column(name = "run_id")
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private AgentRole role;

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    // 에이전트 처리 성공 여부. USER 행은 null(해당 없음), FastAPI 호출이 실패하면 false.
    // false인 AGENT 메시지는 다음 턴의 대화 맥락에서 제외한다 — 실패 안내가 맥락에 섞이면 안 된다.
    @Column(name = "success")
    private Boolean success;

    // FILL_FORM / NAVIGATE / CHOICE 등. LLM팀이 타입을 추가해도 BE 수정·배포가 필요 없도록
    // enum이 아니라 문자열로 저장만 한다. 버튼 렌더링 분기는 프론트가 소유한다.
    @Column(name = "action_type", length = 30)
    private String actionType;

    // action 원문. 서버는 열어보지 않고 그대로 저장했다가 승인 시 그대로 반환한다.
    // 프론트가 targetScreen·params·formData·options를 해석해 화면을 채운다.
    @Column(name = "action_json", columnDefinition = "LONGTEXT")
    private String actionJson;

    // FastAPI 응답 원본(연동 디버깅용). LONGTEXT라 목록 조회에 끌고 오면 무겁다 —
    // 메시지 목록은 엔티티가 아니라 DTO projection으로 필요한 컬럼만 조회한다.
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    @Builder
    public AgentMessageEntity(
            Long conversationId,
            Long runId,
            AgentRole role,
            String content,
            Boolean success,
            String actionType,
            String actionJson,
            String rawJson
    ) {
        this.conversationId = conversationId;
        this.runId = runId;
        this.role = role;
        this.content = content;
        this.success = success;
        this.actionType = actionType;
        this.actionJson = actionJson;
        this.rawJson = rawJson;
    }

}
