package HK.PrettyWorks_BE.project.project.dto.res;

import lombok.Builder;

import java.time.LocalDateTime;

// 마일스톤 완료 토글 결과. 화면은 204로 끝나지만, 결과를 말로 옮겨야 하는 호출자(에이전트)를 위해
// 바뀐 값을 돌려준다.
//
// 전부 토글 과정에서 이미 로드된 엔티티의 값이라 조회가 늘지 않는다.
// 변경 후 완료율처럼 새로 세야 하는 값은 여기 넣지 않는다 — 화면이 쓰지도 않는 숫자를 위해
// 체크박스를 누를 때마다 쿼리가 한 번 더 도는 꼴이 된다. 필요한 호출자가 따로 조회한다.
@Builder
public record MilestoneStatusResponse(
        Long milestoneId,
        String goal,
        boolean completed,
        LocalDateTime completedAt,
        // 실제로 바뀌었는지. false면 이미 그 상태였다는 뜻이며 에러가 아니다.
        boolean changed
) {
}
