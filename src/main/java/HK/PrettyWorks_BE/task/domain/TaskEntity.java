package HK.PrettyWorks_BE.task.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 개인 할 일이면 null. 있으면 그 프로젝트의 할 일.
    @Column(name = "project_id")
    private Long projectId;

    // 담당자 — 이 할 일을 실제로 하는 사람. FK는 raw Long 컬럼으로 유지(기존 컨벤션).
    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    // 작성자 — 이 할 일을 만든 사람. 오너·PM이 팀원에게 배정하면 담당자와 달라진다.
    // 권한이 갈린다: 완료 토글은 담당자만, 삭제는 작성자만, 수정은 둘 다(TaskPolicy).
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    // 완료 시각. null이면 미완료, 값이 있으면 완료. (완료 여부는 이 값의 null 여부로 파생)
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 마감일(필수). D-day는 저장하지 않고 조회 시 dueDate - today 로 파생 계산.
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Builder
    public TaskEntity(Long projectId, Long assigneeId, Long creatorId,
                      String content, LocalDate dueDate) {
        this.projectId = projectId;
        this.assigneeId = assigneeId;
        this.creatorId = creatorId;
        this.content = content;
        this.dueDate = dueDate;
        // 생성 시 completedAt은 null(미완료) — 별도 세팅 불필요
    }

    // 담당자 = 작성자인지. 개인 할 일과 스스로 만든 할 일이 여기 해당한다.
    public boolean isSelfAssigned() {
        return assigneeId.equals(creatorId);
    }

    // 수정 API: 내용·소속(projectId)·마감일을 갱신합니다. (완료 상태·assignee는 이 API로 바꾸지 않음)
    public void update(String content, Long projectId, LocalDate dueDate) {
        this.content = content;
        this.projectId = projectId;
        this.dueDate = dueDate;
    }

    // 완료 토글: done=true면 미완료였을 때만 완료 시각 기록(멱등), done=false면 완료 취소.
    public void toggleDone(boolean done, LocalDateTime now) {
        if (done) {
            if (this.completedAt == null) {
                this.completedAt = now;
            }
        } else {
            this.completedAt = null;
        }
    }

}
