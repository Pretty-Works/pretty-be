package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;

import java.time.LocalDate;

/** Narrow project view shared by adapters that need project identity and write constraints. */
public record ProjectSearchResult(
        Long projectId,
        String name,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Long targetBudget,
        boolean isOpenForContent
) {
}
