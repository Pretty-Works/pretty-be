package HK.PrettyWorks_BE.task.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.task.domain.TaskEntity;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import HK.PrettyWorks_BE.task.exception.TaskErrorCode;
import HK.PrettyWorks_BE.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        Long projectId = request.projectId();

        // 프로젝트 할 일이면 존재·멤버십 검증, 개인 할 일(null)이면 통과
        validateProjectAccess(projectId, userId);

        // 담당자 = 작성자 본인(userId), done=false는 엔티티가 고정, projectId는 nullable
        TaskEntity task = TaskEntity.builder()
                .projectId(projectId)
                .assigneeId(userId)
                .content(request.content())
                .dueDate(request.dueDate())
                .build();
        taskRepository.save(task);

        return TaskResponse.builder()
                .taskId(task.getId())
                .build();
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 작성자 본인만 수정 (TASK_004) — self-only라 assigneeId가 곧 작성자
        if (!task.getAssigneeId().equals(userId)) {
            throw BaseException.type(TaskErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) projectId 있으면 존재·멤버십 재검증, null이면 개인 할 일로 전환
        Long projectId = request.projectId();
        validateProjectAccess(projectId, userId);

        // 4) 갱신 (dirty checking으로 바뀐 컬럼만 UPDATE)
        task.update(request.content(), projectId, request.dueDate());

        return TaskResponse.builder()
                .taskId(task.getId())
                .build();
    }

    @Transactional
    public TaskResponse delete(Long userId, Long taskId) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 작성자 본인만 삭제 (TASK_005) — self-only라 assigneeId가 곧 작성자
        if (!task.getAssigneeId().equals(userId)) {
            throw BaseException.type(TaskErrorCode.NO_DELETE_PERMISSION);
        }

        // 3) hard delete (참조 자식 테이블 없어 안전)
        taskRepository.delete(task);

        return TaskResponse.builder()
                .taskId(taskId)
                .build();
    }

    // projectId가 있으면 프로젝트 존재(TASK_001)·작성자 멤버(TASK_002) 검증. null이면 개인 할 일이라 통과.
    private void validateProjectAccess(Long projectId, Long userId) {
        if (projectId == null) {
            return;
        }
        if (!projectRepository.existsById(projectId)) {
            throw BaseException.type(TaskErrorCode.PROJECT_NOT_FOUND);
        }
        if (!projectMemberService.isActiveMember(projectId, userId)) {
            throw BaseException.type(TaskErrorCode.NO_ADD_PERMISSION);
        }
    }
}
