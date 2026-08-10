package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentMeResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentRunUserResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentUserSearchResponse;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.util.WeekRange;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.dto.res.MyProfileResponse;
import HK.PrettyWorks_BE.user.dto.res.UserSearchResponse;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// user.me · user.search · run→user 해석. 검증·조회는 전부 UserService가 하고,
// 여기서는 응답을 에이전트용으로 좁힌다.
@Service
@RequiredArgsConstructor
public class AgentUserToolService {

    // 서버 기준 오늘. 사용자·데이터가 전부 국내라 고정한다.
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_USER_SEARCH_SIZE = 20;

    private final UserService userService;
    private final AgentRunRepository runRepository;
    private final CurrentUserService currentUserService;

    public AgentMeResponse me(Long userId) {
        MyProfileResponse profile = userService.getMyProfile(userId);

        LocalDate today = LocalDate.now(SERVICE_ZONE);
        WeekRange thisWeek = WeekRange.of(today);

        return AgentMeResponse.builder()
                .userId(profile.userId())
                .name(profile.name())
                // 부서·직책은 한글명만 — 에이전트는 enum 코드로 할 일이 없다(공동 규격 §4-2)
                .department(profile.department().getDescription())
                .position(profile.position().getDescription())
                .canCreateProject(profile.canCreateProject())
                .today(today)
                .todayDayOfWeek(today.getDayOfWeek().name())
                .thisWeekStart(thisWeek.start())
                .thisWeekEnd(thisWeek.end())
                .build();
    }

    public AgentUserSearchResponse search(Long userId, String keyword, String department, int size) {
        // keyword를 필수로 둔다. 없으면 전사 직원 명부가 통째로 넘어간다.
        // (UserService는 빈 검색어를 "결과 없음"으로 처리하므로 여기서 명시적으로 거부한다)
        if (!StringUtils.hasText(keyword)) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        int validatedSize = AgentPage.validateSize(size, MAX_USER_SEARCH_SIZE);
        DepartmentType departmentFilter = parseDepartment(department);

        List<UserSearchResponse> found = userService.searchUsers(userId, keyword, validatedSize);

        // 부서 필터는 조회 뒤에 건다. 공개 검색 쿼리에 파라미터를 늘리는 대신,
        // 동명이인을 가르는 보조 수단이라 상한 안에서 걸러도 충분하다.
        // 다만 이 때문에 "상한에 걸렸는지"는 필터 전 건수로 판단해야 한다.
        boolean truncated = found.size() >= validatedSize;
        List<AgentUserSearchResponse.AgentUser> users = found.stream()
                .filter(user -> departmentFilter == null || user.department() == departmentFilter)
                .map(user -> AgentUserSearchResponse.AgentUser.builder()
                        .userId(user.userId())
                        .name(user.name())
                        .department(user.department().getDescription())
                        .position(user.position().getDescription())
                        // 공개 검색이 요청자 본인을 결과에서 빼므로 항상 false다.
                        // 일정 참가자·회의록 참석자 모두 본인을 넣지 않는 게 맞아 그 동작을 그대로 따른다.
                        .isMe(false)
                        .build())
                .toList();

        return AgentUserSearchResponse.builder()
                .users(users)
                .totalCount(users.size())
                .truncated(truncated)
                .build();
    }

    // runId → 그 실행의 주인. InternalAgentFilter가 X-Run-Id로 하는 역산과 같은 일이지만,
    // 도구 호출이 아니라 조회라서 run 상태는 보지 않는다 — 끝난 실행이어도 주인은 그대로다.
    //
    // 재직 검증은 여기서 반드시 한다. gmail-mcp는 메일 작업을 MCP 안에서 끝내고 BE를 거치지
    // 않으므로, userId를 내주는 이 지점이 퇴사자를 막을 수 있는 유일한 곳이다.
    public AgentRunUserResponse runUser(String runId) {
        AgentRunEntity run = runRepository.findByRunId(runId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        currentUserService.getEmployedUser(run.getUserId());

        return AgentRunUserResponse.builder()
                .userId(run.getUserId())
                .build();
    }

    private DepartmentType parseDepartment(String department) {
        if (!StringUtils.hasText(department)) {
            return null;
        }
        try {
            return DepartmentType.valueOf(department.trim().toUpperCase());
        } catch (IllegalArgumentException unknownDepartment) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }
}
