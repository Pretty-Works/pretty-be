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

    // 홈 조회: 본인 할 일 중 (미완료 OR 완료 3일 이내), ARCHIVED 프로젝트는 제외(개인 할 일은 포함).
    // 연관관계 없이 raw FK라 엔티티 조인(ON)으로 프로젝트명을 함께 가져온다. (개인은 LEFT라 안 빠짐)
    @Query("select new HK.PrettyWorks_BE.task.repository.TaskHomeRow(" +
            "t.id, t.content, t.completedAt, t.dueDate, t.projectId, p.name) " +
            "from TaskEntity t left join ProjectEntity p on p.id = t.projectId " +
            "where t.assigneeId = :userId " +
            "and (t.completedAt is null or t.completedAt >= :threshold) " +
            "and (t.projectId is null or p.status <> :archived) " +
            "order by t.projectId, t.dueDate")
    List<TaskHomeRow> findTaskHomeRows(@Param("userId") Long userId,
                                    @Param("threshold") LocalDateTime threshold,
                                    @Param("archived") ProjectStatus archived);

    // 프로젝트 인원 보드 조회: 해당 프로젝트 할 일 중 (이번 주 범위 OR 지난 주 이전 미완료 carry-over).
    // assigneeId는 NOT NULL이라 담당자 엔티티 조인(ON)으로 이름·부서를 함께 가져온다.
    @Query("select new HK.PrettyWorks_BE.task.repository.TaskProjectRow(" +
            "t.id, t.content, t.completedAt, t.dueDate, u.id, u.name, u.department) " +
            "from TaskEntity t join UserEntity u on u.id = t.assigneeId " +
            "where t.projectId = :projectId " +
            "and ((t.dueDate between :weekStart and :weekEnd) " +
            "or (t.dueDate < :weekStart and t.completedAt is null)) " +
            "order by t.dueDate")
    List<TaskProjectRow> findTaskProjectRows(@Param("projectId") Long projectId,
                                             @Param("weekStart") LocalDate weekStart,
                                             @Param("weekEnd") LocalDate weekEnd);
}
