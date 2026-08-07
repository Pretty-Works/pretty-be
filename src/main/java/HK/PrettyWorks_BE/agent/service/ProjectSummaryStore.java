package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.client.dto.AgentProjectSummaryResult;
import HK.PrettyWorks_BE.agent.domain.ProjectSummaryEntity;
import HK.PrettyWorks_BE.agent.repository.ProjectSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 요약 배너의 저장·조회만 담당합니다.
 *
 * <p>{@code ProjectSummaryService}에서 떼어 낸 이유는 트랜잭션 경계 때문입니다. FastAPI 호출은
 * 수 초가 걸리는데, 그 호출을 트랜잭션 안에 두면 그동안 DB 커넥션 하나를 붙잡고 있게 됩니다.
 * 그래서 "읽기 → (트랜잭션 밖에서) LLM 호출 → 쓰기"로 나누고, 양 끝의 짧은 트랜잭션만
 * 이 클래스가 소유합니다. 같은 클래스의 메서드를 부르면 프록시를 타지 않아
 * {@code @Transactional}이 걸리지 않는 함정도 이 분리로 함께 피합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectSummaryStore {

    private final ProjectSummaryRepository summaryRepository;
    private final AgentJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    /**
     * 저장된 배너를 표시 순서대로 돌려줍니다. 아직 생성 전이면 빈 목록입니다.
     */
    @Transactional(readOnly = true)
    public List<StoredSummary> load(Long projectId) {
        List<StoredSummary> stored = new ArrayList<>();
        for (ProjectSummaryEntity row : summaryRepository.findByProjectIdOrderByDisplayOrderAscIdAsc(projectId)) {
            JsonNode banner = jsonSupport.read(row.getPayloadJson());
            // 깨진 JSON 한 장 때문에 배너 전체가 500이 되면 안 된다. 다음 배치가 덮어쓴다.
            if (banner != null) {
                stored.add(new StoredSummary(row.getSection(), banner, row.getGeneratedAt()));
            }
        }
        return stored;
    }

    /**
     * 새로 받은 요약으로 이 프로젝트의 배너를 통째로 갈아 끼웁니다.
     *
     * <p>섹션 단위로 맞바꾸고 이번 응답에 없는 섹션은 지웁니다. 남겨 두면 LLM팀이 섹션을 없앤 뒤에도
     * 화면에 옛날 배너가 계속 떠 있게 됩니다.</p>
     */
    @Transactional
    public void save(Long projectId, AgentProjectSummaryResult result, LocalDateTime generatedAt) {
        // 대소문자를 무시하고 짝을 찾는다. (project_id, section) 유니크 인덱스가 utf8mb4_unicode_ci라
        // DB는 "overview"와 "Overview"를 같은 키로 본다. 여기서 대소문자를 구분하면
        // 표기가 바뀐 날 UPDATE가 아니라 INSERT + DELETE가 되는데, 하이버네이트는 INSERT를 먼저
        // 실행하므로 그 시점에 유니크 제약과 부딪힌다.
        Map<String, ProjectSummaryEntity> existing = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ProjectSummaryEntity row : summaryRepository.findByProjectIdOrderByDisplayOrderAscIdAsc(projectId)) {
            existing.put(row.getSection(), row);
        }

        List<ProjectSummaryEntity> toInsert = new ArrayList<>();
        int order = 0;
        for (AgentProjectSummaryResult.Summary summary : result.summaries()) {
            String payloadJson = objectMapper.writeValueAsString(summary.payload());
            ProjectSummaryEntity row = existing.remove(summary.section());
            if (row == null) {
                toInsert.add(ProjectSummaryEntity.builder()
                        .projectId(projectId)
                        .section(summary.section())
                        .displayOrder(order)
                        .payloadJson(payloadJson)
                        .generatedAt(generatedAt)
                        .build());
            } else {
                // 영속 엔티티라 dirty checking으로 UPDATE된다.
                row.refresh(order, payloadJson, generatedAt);
            }
            order++;
        }

        // 남은 것 = 이번 응답에 없던 섹션. 새로 넣는 섹션과 겹치지 않으므로
        // (project_id, section) 유니크 제약과 부딪히지 않는다.
        if (!existing.isEmpty()) {
            summaryRepository.deleteAll(existing.values());
        }
        if (!toInsert.isEmpty()) {
            summaryRepository.saveAll(toInsert);
        }
    }

    /**
     * @param section   overview · board · budget · meeting
     * @param banner    프론트로 그대로 나갈 원문
     * @param generatedAt 이 배너를 만든 시각
     */
    public record StoredSummary(String section, JsonNode banner, LocalDateTime generatedAt) {
    }
}
