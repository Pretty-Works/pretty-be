package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import HK.PrettyWorks_BE.project.project.policy.MilestonePolicy;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

// 마일스톤 한 건의 공통 응답 모양. 목록 조회와 프로젝트 상세 조회가 함께 쓴다.
// 같은 개념을 두 record로 나눠 두면 프론트도 타입을 두 벌 만들게 되고, 한쪽에만 필드가 추가되어 갈라진다.
@Builder
public record MilestoneSummary(
        // 영구 식별자. 프로젝트 수정(PUT) 요청에 그대로 실어 보내야 완료 상태가 보존된다.
        Long milestoneId,
        LocalDate targetDate,
        String goal,
        // 완료 여부. 저장된 완료 시각의 유무에서 파생하며 목표일과는 무관하다.
        boolean done,

        // 지금 이 마일스톤의 완료 상태를 바꿀 수 있는지. 목록에서 위치를 봐야 알 수 있어 서버가 판정한다.
        // 클라이언트가 같은 계산을 하면 서버 규칙과 어긋난 순간 체크박스는 열려 있는데 요청은 409가 된다.
        // ⚠️ 화면 표시용이다. 권한(오너·PM)은 포함하지 않으며 실제 허용 여부는 요청 시 서버가 다시 판정한다.
        boolean toggleable
) {
    public static MilestoneSummary from(MilestoneEntity milestone, boolean toggleable) {
        return MilestoneSummary.builder()
                .milestoneId(milestone.getId())
                .targetDate(milestone.getTargetDate())
                .goal(milestone.getGoal())
                .done(milestone.isDone())
                .toggleable(toggleable)
                .build();
    }

    // toggleable은 목록 안에서의 위치로 정해지므로 한 건만 보고는 만들 수 없다.
    // 목록 조회와 프로젝트 상세가 같은 판정을 쓰도록 변환을 여기 모은다.
    public static List<MilestoneSummary> listFrom(List<MilestoneEntity> ordered) {
        return IntStream.range(0, ordered.size())
                .mapToObj(i -> from(ordered.get(i), MilestonePolicy.canToggle(ordered, i)))
                .toList();
    }
}
