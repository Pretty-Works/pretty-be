package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// project.search — 이름으로 말한 프로젝트를 projectId로 바꾼다. 거의 모든 작업의 첫 도구다.
//
// 화면용 DTO를 그대로 내보내지 않는다. 화면 사정으로 필드가 바뀌면 FastAPI와의 계약이
// 컴파일러도 못 잡는 채로 깨진다.
@Builder
public record AgentProjectSearchResponse(
        List<AgentProject> projects,
        int totalCount,
        // true면 검색어를 좁혀야 한다.
        boolean truncated
) {
    @Builder
    public record AgentProject(
            Long projectId,
            String name,
            String status,
            LocalDate startDate,
            // 회의록·할일·지출 날짜가 모두 이 범위 안이어야 한다. 날짜를 정하기 전에 확인할 것.
            LocalDate targetDate,
            String myRole,
            // myRole과 함께 마일스톤 완료 권한(오너 또는 PM) 판정에 쓴다.
            boolean isOwner,
            // 0이면 예산 제한 없음.
            Long targetBudget,
            // false면 할일·회의록·지출을 추가할 수 없다(PROJECT_020). 쓰기 도구를 시도하지 말 것.
            boolean isOpenForContent
    ) {
    }
}
