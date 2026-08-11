package HK.PrettyWorks_BE.agent.summary.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 프로젝트 탭 상단 AI 요약 배너 한 장.
 *
 * <p>프로젝트 하나가 섹션 수만큼 행을 갖습니다(overview·board·budget·meeting). 4개를 한 행에
 * 몰아넣지 않은 이유는 조회가 {@code ?section=budget}처럼 한 장 단위로 들어오기 때문입니다.</p>
 *
 * <p>{@code section}을 enum이 아니라 문자열로 두는 것이 이 설계의 핵심입니다. BE는 게이트라서
 * 섹션의 의미를 알지 않고 저장 키로만 씁니다 — LLM팀이 섹션을 하나 더 만들어도 BE는 그대로 받아
 * 저장하고 돌려줍니다. 같은 이유로 {@code payloadJson}도 열어보지 않습니다.</p>
 *
 * <p>{@code displayOrder}는 FastAPI 응답 배열의 순서입니다. 섹션 이름을 해석하지 않고도
 * overview → board → budget → meeting 순서를 유지하려면 순번을 받아 적어 두는 수밖에 없습니다.</p>
 */
@Entity
@Table(name = "project_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSummaryEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "section", nullable = false, length = 20)
    private String section;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    // created_at은 이 행이 처음 만들어진 시각이라 재생성해도 그대로다.
    // 화면이 "언제 기준 요약인지" 말하려면 마지막 생성 시각이 따로 필요하다.
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    // 만들 때의 재료 상태를 나타내는 지문(최근 수정 시각 + 행 수). 다음 조회에서 지금 값과 비교해
    // "그사이 바뀌었나"를 판정한다. 이 칼럼이 있어서 도메인 코드에 캐시 무효화를 심지 않아도 된다.
    // 서버는 값을 해석하지 않고 같은지만 본다.
    @Column(name = "source_stamp", length = 64)
    private String sourceStamp;

    @Builder
    public ProjectSummaryEntity(Long projectId, String section, int displayOrder,
                                String payloadJson, LocalDateTime generatedAt, String sourceStamp) {
        this.projectId = projectId;
        this.section = section;
        this.displayOrder = displayOrder;
        this.payloadJson = payloadJson;
        this.generatedAt = generatedAt;
        this.sourceStamp = sourceStamp;
    }

    public void refresh(int displayOrder, String payloadJson, LocalDateTime generatedAt,
                        String sourceStamp) {
        this.displayOrder = displayOrder;
        this.payloadJson = payloadJson;
        this.generatedAt = generatedAt;
        this.sourceStamp = sourceStamp;
    }
}
