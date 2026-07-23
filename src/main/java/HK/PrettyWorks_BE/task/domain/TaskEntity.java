package HK.PrettyWorks_BE.task.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    // 담당자 (현재는 작성자 본인). FK는 raw Long 컬럼으로 유지(기존 컨벤션).
    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    @Column(name = "done", nullable = false)
    private boolean done;

    // 마감일(필수). D-day는 저장하지 않고 조회 시 dueDate - today 로 파생 계산.
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Builder
    public TaskEntity(Long projectId, Long assigneeId, String content, LocalDate dueDate) {
        this.projectId = projectId;
        this.assigneeId = assigneeId;
        this.content = content;
        this.dueDate = dueDate;
        this.done = false;   // 생성 시 항상 미완료
    }

    // 수정 API: 내용·소속(projectId)·마감일을 갱신합니다. (done·assignee는 이 API로 바꾸지 않음)
    public void update(String content, Long projectId, LocalDate dueDate) {
        this.content = content;
        this.projectId = projectId;
        this.dueDate = dueDate;
    }

}
