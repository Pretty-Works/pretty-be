package HK.PrettyWorks_BE.agent.tool.project;

import HK.PrettyWorks_BE.agent.tool.project.dto.AgentProjectSearchResponse;
import HK.PrettyWorks_BE.project.finance.service.ExpenseService;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectSearchResult;
import HK.PrettyWorks_BE.project.project.service.MilestoneService;
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
    private final ProjectMemberService projectMemberService = mock(ProjectMemberService.class);
    private final MilestoneService milestoneService = mock(MilestoneService.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final AgentProjectToolService service = new AgentProjectToolService(
            projectService, projectMemberService, milestoneService, expenseService);

    @Test
    void preservesTotalCountAndSignalsTruncation() {
        ProjectSearchResult project = new ProjectSearchResult(
                7L, "에이전트 v2", ProjectStatus.ONGOING,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30),
                10_000_000L, true, "BE", false);
        PageRequest pageable = PageRequest.of(0, 1);
        when(projectService.searchMyProjects(3L, "ALL", "에이전트", pageable))
                .thenReturn(new PageImpl<>(List.of(project), pageable, 3));

        AgentProjectSearchResponse result = service.search(3L, "ALL", "에이전트", 1);

        assertThat(result.projects()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.truncated()).isTrue();
        verify(projectService).searchMyProjects(3L, "ALL", "에이전트", pageable);
    }

    @Test
    void exposesTheRolesNeededToPredictMilestonePermission() {
        // milestone.toggleStatus는 오너 또는 PM만 가능하다. 이 두 필드가 없으면 에이전트가
        // 권한을 모른 채 승인 카드를 올리고, 사용자가 누른 뒤에야 PROJECT_005로 실패한다.
        ProjectSearchResult owned = new ProjectSearchResult(
                7L, "에이전트 v2", ProjectStatus.ONGOING,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30),
                0L, true, "PM", true);
        PageRequest pageable = PageRequest.of(0, 20);
        when(projectService.searchMyProjects(3L, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(owned), pageable, 1));

        AgentProjectSearchResponse result = service.search(3L, null, null, 20);

        AgentProjectSearchResponse.AgentProject project = result.projects().get(0);
        assertThat(project.myRole()).isEqualTo("PM");
        assertThat(project.isOwner()).isTrue();
        assertThat(project.targetDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.truncated()).isFalse();
    }
}
