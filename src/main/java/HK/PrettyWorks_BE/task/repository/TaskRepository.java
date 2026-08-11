package HK.PrettyWorks_BE.task.repository;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.task.domain.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    // 홈 조회: 본인 할 일 중 (미완료 OR 완료 3일 이내). 개인 할 일은 프로젝트 상태와 무관하게 포함.
    // 연관관계 없이 raw FK라 엔티티 조인(ON)으로 프로젝트명을 함께 가져온다. (개인은 LEFT라 안 빠짐)
    @Query("select new HK.PrettyWorks_BE.task.repository.TaskHomeRow(" +
            "t.id, t.content, t.completedAt, t.dueDate, t.projectId, p.name, p.status, t.creatorId) " +
            "from TaskEntity t left join ProjectEntity p on p.id = t.projectId " +
            "where t.assigneeId = :userId " +
            "and (t.completedAt is null or t.completedAt >= :threshold) " +
            "and (t.projectId is null or p.status in :statuses) " +
            "order by t.projectId, t.dueDate")
    List<TaskHomeRow> findTaskHomeRows(@Param("userId") Long userId,
                                    @Param("threshold") LocalDateTime threshold,
                                    @Param("statuses") List<ProjectStatus> statuses);

    // 개인 주간 조회: 본인 담당 할 일 중 (그 주 범위 OR 지연된 미완료).
    // 홈 조회(findTaskHomeRows)와 대상은 같지만 경계가 "완료 3일 이내"가 아니라 주 단위다 —
    // 에이전트는 "이번 주 할 일"·"지난 주에 뭐 했지"처럼 항상 주를 기준으로 묻는다.
    //
    // carryOverBefore: 지연분을 함께 볼 기준일. null이면 그 주 마감분만 조회한다.
    // 이번 주에만 값을 넘긴다 — 다음 주 목록에 지난 지연이 계속 따라붙으면 "다음 주에 할 일"을
    // 알 수 없고, 지난 주 목록은 그보다 더 오래된 지연까지 섞여 그 주의 기록이 아니게 된다.
    @Query("select new HK.PrettyWorks_BE.task.repository.TaskHomeRow(" +
            "t.id, t.content, t.completedAt, t.dueDate, t.projectId, p.name, p.status, t.creatorId) " +
            "from TaskEntity t left join ProjectEntity p on p.id = t.projectId " +
            "where t.assigneeId = :userId " +
            "and (t.projectId is null or p.status <> :archived) " +
            "and ((t.dueDate between :weekStart and :weekEnd) " +
            "or (:carryOverBefore is not null " +
            "    and t.dueDate < :carryOverBefore and t.completedAt is null)) " +
            "order by t.dueDate, t.id")
    List<TaskHomeRow> findMyWeeklyRows(@Param("userId") Long userId,
                                       @Param("weekStart") LocalDate weekStart,
                                       @Param("weekEnd") LocalDate weekEnd,
                                       @Param("carryOverBefore") LocalDate carryOverBefore,
                                       @Param("archived") ProjectStatus archived);

    // 프로젝트 인원 보드 조회: 해당 프로젝트 할 일 중 (그 주 범위 OR 지연된 미완료).
    // assigneeId는 NOT NULL이라 담당자 엔티티 조인(ON)으로 이름·부서를 함께 가져온다.
    // carryOverBefore의 의미는 findMyWeeklyRows와 같다 — 두 조회가 다르게 동작하면
    // 화면 보드와 에이전트 답변이 서로 다른 목록을 내놓는다.
    @Query("select new HK.PrettyWorks_BE.task.repository.TaskProjectRow(" +
            "t.id, t.content, t.completedAt, t.dueDate, u.id, u.name, u.department, p.name, t.creatorId) " +
            "from TaskEntity t join UserEntity u on u.id = t.assigneeId " +
            "join ProjectEntity p on p.id = t.projectId " +
            "where t.projectId = :projectId " +
            "and ((t.dueDate between :weekStart and :weekEnd) " +
            "or (:carryOverBefore is not null " +
            "    and t.dueDate < :carryOverBefore and t.completedAt is null)) " +
            "order by t.dueDate")
    List<TaskProjectRow> findTaskProjectRows(@Param("projectId") Long projectId,
                                             @Param("weekStart") LocalDate weekStart,
                                             @Param("weekEnd") LocalDate weekEnd,
                                             @Param("carryOverBefore") LocalDate carryOverBefore);

    // 프로젝트 기간 축소 검증: 새 기간을 벗어나는 마감일이 남는지 확인한다.
    @Query("select count(t) > 0 from TaskEntity t " +
            "where t.projectId = :projectId " +
            "and (t.dueDate < :startDate or t.dueDate > :endDate)")
    boolean existsOutsidePeriod(@Param("projectId") Long projectId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);
}
