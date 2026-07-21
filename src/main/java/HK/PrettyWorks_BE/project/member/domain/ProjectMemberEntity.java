package HK.PrettyWorks_BE.project.member.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMemberEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 생성자(오너)면 true, 일반 참여자면 false. 참여 여부 인가는 is_owner와 무관하게 status로 판단합니다.
    @Column(name = "is_owner", nullable = false)
    private boolean isOwner;

    @Column(name = "role", length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectMemberStatus status;

    // 탈퇴 시각. 참여중이면 null.
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Builder
    public ProjectMemberEntity(Long projectId, Long userId, boolean isOwner,
                               String role, ProjectMemberStatus status, LocalDateTime leftAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.isOwner = isOwner;
        this.role = role;
        this.status = status;
        this.leftAt = leftAt;
    }

    // 직무 역할 변경 (오너 역할 갱신 / 참여자 역할 변경)
    public void updateRole(String role) {
        this.role = role;
    }

    // 참여 종료(soft delete): 상태를 LEFT로 바꾸고 탈퇴 시각을 기록
    public void leave(LocalDateTime leftAt) {
        this.status = ProjectMemberStatus.LEFT;
        this.leftAt = leftAt;
    }

    // 재참여: 다시 ACTIVE로 되살리고 탈퇴 시각 제거, 역할도 갱신
    public void reactivate(String role) {
        this.status = ProjectMemberStatus.ACTIVE;
        this.leftAt = null;
        this.role = role;
    }

}
