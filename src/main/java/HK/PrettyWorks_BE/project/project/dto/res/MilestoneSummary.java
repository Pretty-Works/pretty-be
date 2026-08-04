package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import lombok.Builder;

import java.time.LocalDate;

// 마일스톤 한 건의 공통 응답 모양. 목록 조회와 프로젝트 상세 조회가 함께 쓴다.
// 같은 개념을 두 record로 나눠 두면 프론트도 타입을 두 벌 만들게 되고, 한쪽에만 필드가 추가되어 갈라진다.
@Builder
public record MilestoneSummary(
        // 영구 식별자. 프로젝트 수정(PUT) 요청에 그대로 실어 보내야 완료 상태가 보존된다.
        Long milestoneId,
        LocalDate targetDate,
        String goal,
        // 완료 여부. 저장된 완료 시각의 유무에서 파생하며 목표일과는 무관하다.
        boolean done
) {
    public static MilestoneSummary from(MilestoneEntity milestone) {
        return MilestoneSummary.builder()
                .milestoneId(milestone.getId())
                .targetDate(milestone.getTargetDate())
                .goal(milestone.getGoal())
                .done(milestone.isDone())
                .build();
    }
}
