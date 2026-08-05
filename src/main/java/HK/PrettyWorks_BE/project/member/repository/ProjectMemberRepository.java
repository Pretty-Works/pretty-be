package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.user.constant.StatusType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 프로젝트 하위 기능의 참여자 선택 자동완성. 참여중인 재직자만 이름으로 검색한다.
    @Query("""
            select u.id as userId,
                   u.name as name,
                   u.department as department,
                   u.position as position,
                   m.role as role
            from ProjectMemberEntity m
            join UserEntity u on u.id = m.userId
            where m.projectId = :projectId
              and m.status = :memberStatus
              and u.status = :userStatus
              and u.id <> :requesterId
              and u.name like concat('%', :keyword, '%')
            order by u.name asc, u.id asc
            """)
    List<ProjectMemberSearchRow> searchActiveMembers(@Param("projectId") Long projectId,
                                                     @Param("requesterId") Long requesterId,
                                                     @Param("keyword") String keyword,
                                                     @Param("memberStatus") ProjectMemberStatus memberStatus,
                                                     @Param("userStatus") StatusType userStatus,
                                                     Pageable pageable);

    // 참여중 재직자 전체(요청자 본인 포함). 자동완성과 달리 검색어가 없어도 전체를 돌려주고
    // 오너 여부를 함께 실어 준다.
    //
    // 오너를 맨 앞에 고정한다 — "이 프로젝트 누구 있어?"에 오너부터 답하는 것이 자연스럽고,
    // 정렬이 흔들리면 같은 질문에 매번 다른 순서가 나온다.
    @Query("""
            select u.id as userId,
                   u.name as name,
                   u.department as department,
                   u.position as position,
                   m.role as role,
                   m.isOwner as isOwner
            from ProjectMemberEntity m
            join UserEntity u on u.id = m.userId
            where m.projectId = :projectId
              and m.status = :memberStatus
              and u.status = :userStatus
              and (:keyword is null or u.name like concat('%', :keyword, '%'))
            order by m.isOwner desc, u.name asc, u.id asc
            """)
    List<ProjectMemberDetailRow> findActiveMembers(@Param("projectId") Long projectId,
                                                   @Param("keyword") String keyword,
                                                   @Param("memberStatus") ProjectMemberStatus memberStatus,
                                                   @Param("userStatus") StatusType userStatus,
                                                   Pageable pageable);
}
