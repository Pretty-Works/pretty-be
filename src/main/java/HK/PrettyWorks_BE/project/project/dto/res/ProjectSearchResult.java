package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;

import java.time.LocalDate;

/** Narrow project view shared by adapters that need project identity and write constraints. */
public record ProjectSearchResult(
        Long projectId,
        String name,
        ProjectStatus status,
        LocalDate startDate,
        // 목표일. 엔티티 필드명(targetDate)을 그대로 쓴다 — 밖에서만 endDate로 부르면
        // "기간 검증에 쓰는 그 날짜"를 찾을 때 이름이 두 개가 된다.
        LocalDate targetDate,
        Long targetBudget,
        boolean isOpenForContent,
        // 요청자의 프로젝트 내 역할. 지정 안 됐으면 null.
        String myRole,
        // 요청자가 오너인지. myRole과 함께 마일스톤 완료 권한(오너 또는 PM) 판정에 쓴다 —
        // 이걸 모르면 권한 없는 변경을 시도했다가 PROJECT_005로 거절당한 뒤에야 알게 된다.
        boolean isOwner
) {
}
