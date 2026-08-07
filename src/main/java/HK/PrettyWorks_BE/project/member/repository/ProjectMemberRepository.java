package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.user.constant.StatusType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {

    // 회의록·게시판 등에서 "이 사용자가 이 프로젝트의 참여중(ACTIVE) 멤버인가"를 인가할 때 사용합니다.
    boolean existsByProjectIdAndUserIdAndStatus(Long projectId, Long userId, ProjectMemberStatus status);

    // 수정 API: 호출자의 해당 프로젝트 멤버십 (오너/PM 여부로 수정 권한 판정).
    Optional<ProjectMemberEntity> findByProjectIdAndUserIdAndStatus(Long projectId, Long userId, ProjectMemberStatus status);

    // 수정 API: 프로젝트의 오너 행 (오너 역할 갱신용).
    // boolean 필드(isOwner)는 파생 쿼리 이름 규칙과 헷갈릴 수 있어 JPQL로 명시합니다.
    @Query("select m from ProjectMemberEntity m where m.projectId = :projectId and m.isOwner = true")
    Optional<ProjectMemberEntity> findOwner(@Param("projectId") Long projectId);

    // 수정 API: 오너를 제외한 참여자 전체 (ACTIVE·LEFT 모두 — diff·재활성화 판단용).
    @Query("select m from ProjectMemberEntity m where m.projectId = :projectId and m.isOwner = false")
    List<ProjectMemberEntity> findParticipants(@Param("projectId") Long projectId);

    // 상세 조회 API: 참여중(ACTIVE)인 멤버 전체. 오너·참여자를 한 번에 가져와 isOwner로 나눠 쓴다.
    // 참여 순(id 오름차순) 고정 — 정렬이 없으면 조회할 때마다 순서가 달라져 화면이 흔들린다.
    List<ProjectMemberEntity> findByProjectIdAndStatusOrderByIdAsc(Long projectId, ProjectMemberStatus status);

    // 참여중 재직자(휴직 포함) 조회. 명단 화면과 참여자 추가 자동완성이 함께 쓴다.
    //
    // 두 용도를 한 쿼리로 합친 이유: 필터 두 개만 다르고 조인·컬럼·재직 기준이 모두 같았다.
    // 나눠 두면 한쪽에만 조건이 추가되어 "화면에는 보이는데 후보로는 안 뜨는" 사람이 생긴다.
    //   keyword       null이면 전체, 있으면 이름 부분 일치
    //   excludeUserId null이면 전원, 있으면 그 사람만 제외(자동완성에서 요청자 본인을 뺄 때)
    //
    // 오너를 맨 앞에 고정한다 — 명단에서 오너부터 보이는 것이 자연스럽고,
    // 정렬이 흔들리면 같은 조회에 매번 다른 순서가 나온다.
    @Query("""
            select u.id as userId,
                   u.name as name,
                   u.department as department,
                   u.position as position,
                   u.status as status,
                   m.role as role,
                   m.isOwner as isOwner
            from ProjectMemberEntity m
            join UserEntity u on u.id = m.userId
            where m.projectId = :projectId
              and m.status = :memberStatus
              and u.status in :employedStatuses
              and (:keyword is null or u.name like concat('%', :keyword, '%'))
              and (:excludeUserId is null or u.id <> :excludeUserId)
            order by m.isOwner desc, u.name asc, u.id asc
            """)
    List<ProjectMemberRow> findActiveMembers(@Param("projectId") Long projectId,
                                             @Param("keyword") String keyword,
                                             @Param("excludeUserId") Long excludeUserId,
                                             @Param("memberStatus") ProjectMemberStatus memberStatus,
                                             @Param("employedStatuses") Collection<StatusType> employedStatuses,
                                             Pageable pageable);
}
