package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentRunUserResponse;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentUserToolServiceTest {

    private final UserService userService = mock(UserService.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentUserToolService service =
            new AgentUserToolService(userService, runRepository, currentUserService);

    @Test
    void resolvesRunOwnerSoAnExternalMcpServerCanFindThatUsersCredential() {
        when(runRepository.findByRunId("run-1")).thenReturn(Optional.of(run(9L)));

        AgentRunUserResponse result = service.runUser("run-1");

        assertThat(result.userId()).isEqualTo(9L);
    }

    @Test
    void rejectsResignedOwnerBecauseThisIsTheOnlyPlaceBeCanStopThem() {
        // gmail-mcp는 메일 작업을 자기 안에서 끝내고 BE를 다시 부르지 않는다.
        // userId를 내주고 나면 그 뒤를 막을 방법이 없으므로 여기서 걸러야 한다.
        when(runRepository.findByRunId("run-1")).thenReturn(Optional.of(run(9L)));
        when(currentUserService.getEmployedUser(9L))
                .thenThrow(BaseException.type(UserErrorCode.RESIGNED_USER));

        assertThatThrownBy(() -> service.runUser("run-1"))
                .isInstanceOf(BaseException.class)
                .extracting(thrown -> ((BaseException) thrown).getCode())
                .isEqualTo(UserErrorCode.RESIGNED_USER);
    }

    @Test
    void answersForFinishedRunsToo() {
        // 도구 호출과 달리 실행 상태를 보지 않는다. OAuth 왕복이 길어 실행이 끝난 뒤에
        // 조회가 들어올 수 있는데, 끝났다고 주인이 바뀌지는 않는다.
        AgentRunEntity finished = run(9L);
        finished.transition(AgentRunStatus.COMPLETED, null, LocalDateTime.of(2026, 8, 10, 12, 0));
        when(runRepository.findByRunId("run-1")).thenReturn(Optional.of(finished));

        assertThat(service.runUser("run-1").userId()).isEqualTo(9L);
    }

    @Test
    void failsWithRunNotFoundForAnUnknownRunId() {
        when(runRepository.findByRunId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runUser("nope"))
                .isInstanceOf(BaseException.class)
                .extracting(thrown -> ((BaseException) thrown).getCode())
                .isEqualTo(AgentErrorCode.RUN_NOT_FOUND);

        verify(currentUserService, never()).getEmployedUser(anyLong());
    }

    private AgentRunEntity run(Long userId) {
        return AgentRunEntity.builder()
                .runId("run-1")
                .conversationId(1L)
                .userId(userId)
                .goal("메일 확인해줘")
                .startedAt(LocalDateTime.of(2026, 8, 10, 11, 0))
                .build();
    }
}
