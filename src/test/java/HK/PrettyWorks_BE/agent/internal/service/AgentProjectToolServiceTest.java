package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectSearchResult;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentProjectToolServiceTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final AgentProjectToolService service = new AgentProjectToolService(projectService);

    @Test
    void preservesTotalCountAndSignalsTruncation() {
        ProjectSearchResult project = new ProjectSearchResult(
                7L, "에이전트 v2", ProjectStatus.ONGOING,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30),
                10_000_000L, true);
        PageRequest pageable = PageRequest.of(0, 1);
        when(projectService.searchMyProjects(3L, "ALL", "에이전트", pageable))
                .thenReturn(new PageImpl<>(List.of(project), pageable, 3));

        AgentPage<ProjectSearchResult> result = service.search(3L, "ALL", "에이전트", 1);

        assertThat(result.content()).containsExactly(project);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.truncated()).isTrue();
        verify(projectService).searchMyProjects(3L, "ALL", "에이전트", pageable);
    }
}
