package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectSearchResult;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentProjectToolService {

    private final ProjectService projectService;

    public AgentPage<ProjectSearchResult> search(Long userId, String status,
                                                  String keyword, int size) {
        int validatedSize = AgentPage.validateSize(size);
        Page<ProjectSearchResult> page = projectService.searchMyProjects(
                userId, status, keyword, PageRequest.of(0, validatedSize));
        return AgentPage.of(page.getContent(), page.getTotalElements());
    }
}
