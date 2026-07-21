package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
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
}
