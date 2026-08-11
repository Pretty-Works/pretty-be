package HK.PrettyWorks_BE.agent.tool.user.dto;

import lombok.Builder;

import java.time.LocalDate;

// user.me — 요청자가 누구인지 + 오늘이 며칠인지.
//
// 날짜 4필드가 이 도구의 존재 이유다. LLM은 학습 시점에 멈춰 있어 오늘을 모르고,
// "어제"·"다음 주 화요일"을 추측하면 저장은 성공하고 내용만 틀린다.
// 화면용 MyProfileResponse와 달리 부서·직책은 한글명만 보낸다(공동 규격 §4-2) —
// 에이전트가 enum 코드로 할 일이 없어 토큰만 쓴다.
@Builder
public record AgentMeResponse(
        Long userId,
        String name,
        String department,
        String position,
        boolean canCreateProject,
        LocalDate today,
        String todayDayOfWeek,
        LocalDate thisWeekStart,
        LocalDate thisWeekEnd
) {
}
