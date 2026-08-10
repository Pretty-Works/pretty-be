package HK.PrettyWorks_BE.agent.summary.persistence;

import HK.PrettyWorks_BE.agent.summary.domain.ProjectSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectSummaryRepository extends JpaRepository<ProjectSummaryEntity, Long> {

    // 프로젝트당 행이 섹션 수(4)뿐이라 section 필터는 서비스에서 건다.
    // 조회 조건을 나누면 쿼리가 둘이 되는데, 걸러낼 데이터가 세 행이라 얻는 게 없다.
    List<ProjectSummaryEntity> findByProjectIdOrderByDisplayOrderAscIdAsc(Long projectId);

    /**
     * 요약 재료의 현재 상태를 문자열 지문 하나로 읽습니다. 예: {@code 2026-08-07 09:31:02.000000/822}
     *
     * <p>이 쿼리가 있어서 도메인 코드에 캐시 무효화를 심지 않아도 됩니다. 할 일 상태 변경·지출
     * 등록·게시글 작성·에이전트 쓰기 도구 등 재료를 바꾸는 지점이 도메인 전반에 흩어져 있는데,
     * 그 자리마다 무효화를 호출하면 지점이 하나 늘 때마다 빠뜨릴 자리가 하나씩 늘어납니다.
     * "바뀌었다고 알려 달라" 대신 "바뀌었는지 물어본다"로 뒤집은 것입니다.</p>
     *
     * <p>수정 시각과 <b>건수</b>를 함께 담습니다. 시각만으로는 삭제를 못 잡습니다 —
     * 할 일·마일스톤은 하드 삭제라 행이 그냥 사라지고, 게시글·회의록은 {@code @SQLDelete}가
     * 네이티브 UPDATE로 지워 {@code @LastModifiedDate}가 동작하지 않아 {@code modified_at}이
     * 그대로입니다. 건수는 삭제를 반드시 움직입니다.</p>
     *
     * <p>두 값을 칼럼 둘로 나누지 않고 문자열 하나로 합친 이유는, 서버가 이 값을 해석하지 않고
     * 같은지만 보기 때문입니다. 스칼라 하나로 받으면 네이티브 쿼리 프로젝션 매핑에 기댈 일도 없습니다.</p>
     *
     * <p>JPQL이 아니라 네이티브인 이유: 여섯 테이블의 집계를 UNION ALL로 한 번에 왕복해야 하는데
     * JPQL은 FROM 절 서브쿼리를 지원하지 않습니다. 전부 project_id 인덱스를 탑니다.</p>
     *
     * <p>일정(schedules)은 빠져 있습니다. 프로젝트에 묶이지 않아 project_id로 좁힐 수 없어
     * 값싸게 지문을 만들 수 없습니다 — 새 회의 일정은 다음 배치에서 반영됩니다.</p>
     */
    @Query(value = """
            SELECT CONCAT(COALESCE(MAX(x.m), '-'), '/', COALESCE(SUM(x.c), 0))
            FROM (
                SELECT MAX(t.modified_at) AS m, COUNT(*) AS c
                  FROM tasks t
                 WHERE t.project_id = :projectId
                UNION ALL
                SELECT MAX(ms.modified_at), COUNT(*)
                  FROM milestones ms
                 WHERE ms.project_id = :projectId
                UNION ALL
                SELECT MAX(e.modified_at), COUNT(*)
                  FROM expenses e
                 WHERE e.project_id = :projectId AND e.deleted_at IS NULL
                UNION ALL
                SELECT MAX(p.modified_at), COUNT(*)
                  FROM project_posts p
                 WHERE p.project_id = :projectId AND p.deleted_at IS NULL
                UNION ALL
                SELECT MAX(mt.modified_at), COUNT(*)
                  FROM meetings mt
                 WHERE mt.project_id = :projectId AND mt.deleted_at IS NULL
                UNION ALL
                SELECT pr.modified_at, 1
                  FROM projects pr
                 WHERE pr.id = :projectId
            ) x
            """, nativeQuery = true)
    String findSourceStamp(@Param("projectId") Long projectId);
}
