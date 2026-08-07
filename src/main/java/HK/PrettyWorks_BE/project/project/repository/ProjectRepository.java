package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    // 수정 API용 조회. 커밋 시 프로젝트 version을 무조건 1 올리고 검증한다.
    // 멤버·마일스톤만 바뀌면 projects 행 자체는 그대로라 version이 오르지 않아 동시 수정을 놓치는데,
    // FORCE_INCREMENT로 애그리거트(프로젝트+멤버+마일스톤) 단위 충돌을 잡는다.
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select p from ProjectEntity p where p.id = :id")
    Optional<ProjectEntity> findByIdWithOptimisticLock(@Param("id") Long id);

    // 목록 조회: 내가 참여중(ACTIVE)인 프로젝트를 상태·이름으로 걸러 페이지 단위로 가져온다.
    //
    // 정렬은 서버가 고정한다(화면에 정렬 UI가 없고, 페이지 경계를 안정적으로 유지해야 한다).
    //   1) 상태 그룹: 진행중 → 보류 → 종료(완료·중단)
    //   2) 진행형은 목표일 오름차순(마감 임박순) — 지연된 프로젝트가 최상단에 온다
    //   3) 종료형은 목표일 내림차순(최근 종료순) — 방향이 반대라 그룹을 나눠 처리한다
    //      (각 그룹에서 반대편 CASE는 전 행이 null이라 순서에 영향을 주지 않는다)
    //   4) id 보조 정렬 — 없으면 같은 항목이 두 페이지에 나오거나 누락될 수 있다
    @Query(value = """
            select new HK.PrettyWorks_BE.project.project.repository.MyProjectRow(p, m)
            from ProjectEntity p
            join ProjectMemberEntity m on m.projectId = p.id
            where m.userId = :userId
              and m.status = :memberStatus
              and p.status <> :archived
              and (:status is null or p.status = :status)
              and (:keyword is null or p.name like concat('%', :keyword, '%'))
            order by
              case when p.status = :ongoing then 0
                   when p.status = :holding then 1
                   else 2 end,
              case when p.status in (:ongoing, :holding) then p.targetDate else null end asc,
              case when p.status in (:ongoing, :holding) then null else p.targetDate end desc,
              p.id asc
            """,
            countQuery = """
            select count(p) from ProjectEntity p
            join ProjectMemberEntity m on m.projectId = p.id
            where m.userId = :userId
              and m.status = :memberStatus
              and p.status <> :archived
              and (:status is null or p.status = :status)
              and (:keyword is null or p.name like concat('%', :keyword, '%'))
            """)
    Page<MyProjectRow> findMyProjects(@Param("userId") Long userId,
                                       @Param("memberStatus") ProjectMemberStatus memberStatus,
                                       @Param("archived") ProjectStatus archived,
                                       @Param("status") ProjectStatus status,
                                       @Param("keyword") String keyword,
                                       @Param("ongoing") ProjectStatus ongoing,
                                       @Param("holding") ProjectStatus holding,
                                       Pageable pageable);

    // AI 요약 생성 배치 대상. 엔티티가 아니라 id만 읽는다 — 배치는 프로젝트 본문이 필요 없고,
    // 재료는 어차피 도메인 서비스가 다시 조회한다.
    @Query("select p.id from ProjectEntity p where p.status in :statuses order by p.id asc")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<ProjectStatus> statuses);
}
