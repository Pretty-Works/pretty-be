package HK.PrettyWorks_BE.replan.dto.res;

import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import lombok.Builder;

import java.time.LocalDate;

// 재계획 적용 결과. 무엇이 몇 건 바뀌었는지를 종류별로 돌려준다.
//
// 건수를 서버가 세어 주는 이유: 요청한 건수와 실제 바뀐 건수가 다를 수 있다.
// 이미 그 값이던 항목은 건너뛰므로(마감일이 이미 그 날짜였다면 UPDATE도 알림도 없다),
// 호출자가 operations 길이로 답하면 "7건 변경했습니다"가 사실과 어긋난다.
@Builder
public record ReplanApplyResponse(

        Long replanId,
        Long projectId,
        ReplanScenarioType scenarioType,

        // 프로젝트 목표일을 실제로 옮겼다면 최종 값, 안 바꿨으면 null.
        LocalDate projectTargetDate,

        int milestoneDateChangedCount,
        int taskDueDateChangedCount,
        int taskCreatedCount,
        int taskDeletedCount,
        int memberAddedCount
) {
}
