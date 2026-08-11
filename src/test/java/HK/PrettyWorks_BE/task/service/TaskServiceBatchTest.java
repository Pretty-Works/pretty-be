package HK.PrettyWorks_BE.task.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.notification.event.NotificationPublisher;
import HK.PrettyWorks_BE.task.exception.TaskErrorCode;
import HK.PrettyWorks_BE.task.repository.TaskRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceBatchTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectMemberService projectMemberService = mock(ProjectMemberService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final NotificationPublisher notificationPublisher = mock(NotificationPublisher.class);
    private final TaskService service = new TaskService(
            taskRepository, projectRepository, projectMemberService, currentUserService,
            notificationPublisher);

    @Test
    void validatesSharedProjectOnceAndPersistsTheBatchOnce() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project()));
        List<TaskRequest> requests = List.of(
                new TaskRequest("첫 번째", 7L, null, LocalDate.of(2026, 8, 10)),
                new TaskRequest("두 번째", 7L, null, LocalDate.of(2026, 8, 11)));

        var result = service.createBatch(3L, requests);

        assertThat(result).hasSize(2);
        verify(projectRepository, times(1)).findById(7L);
        verify(projectMemberService, times(1)).validateActiveMember(7L, 3L);
        verify(taskRepository).saveAll(anyList());
    }

    @Test
    void invalidItemStopsBeforeAnyTaskIsPersisted() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project()));
        List<TaskRequest> requests = List.of(
                new TaskRequest("정상", 7L, null, LocalDate.of(2026, 8, 10)),
                new TaskRequest("기간 밖", 7L, null, LocalDate.of(2027, 1, 1)));

        assertThatThrownBy(() -> service.createBatch(3L, requests))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(TaskErrorCode.DUE_DATE_OUT_OF_RANGE));

        verify(taskRepository, never()).saveAll(anyList());
    }

    private ProjectEntity project() {
        return ProjectEntity.builder()
                .name("에이전트 v2")
                .status(ProjectStatus.ONGOING)
                .startDate(LocalDate.of(2026, 8, 1))
                .targetDate(LocalDate.of(2026, 9, 30))
                .targetBudget(0L)
                .build();
    }
}
