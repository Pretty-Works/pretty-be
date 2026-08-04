package HK.PrettyWorks_BE.project.project.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// 마일스톤 목록 조회 응답. 개요 화면 '마일스톤 완료율' 카드(도넛 + 목표 마일스톤 + 타임라인)를 한 번에 채운다.
//
// 완료율은 '완료 건수' 기준이지 날짜 기준이 아니다. 목표일이 지났어도 체크하지 않았으면 미완료로 센다.
// 지연된 마일스톤을 완료로 세면 완료율이 실제 진척을 왜곡하기 때문이다.
// 프로젝트의 progress(기간 경과율)와 의도적으로 다른 지표이며, 둘을 나란히 보면 지연을 읽어낼 수 있다.
@Builder
public record MilestoneListResponse(
        int totalCount,
        int completedCount,
        int pendingCount,
        // 완료율(%) 내림. 마일스톤이 없으면 0.
        int completionRate,
        // 미완료 중 목표일이 가장 이른 것. 전부 완료했거나 마일스톤이 없으면 null.
        NextMilestone nextMilestone,
        // 목표일 오름차순(같으면 등록 순).
        List<MilestoneSummary> milestones
) {
    // 화면 상단 카드가 목록과 별개로 표시하므로 편의상 따로 내려준다. milestones 안의 한 항목과 같은 데이터다.
    // done을 두지 않는 이유: 목표 마일스톤은 정의상 항상 미완료라 언제나 false인 필드가 된다.
    @Builder
    public record NextMilestone(
            Long milestoneId,
            LocalDate targetDate,
            String goal
    ) {
    }
}
