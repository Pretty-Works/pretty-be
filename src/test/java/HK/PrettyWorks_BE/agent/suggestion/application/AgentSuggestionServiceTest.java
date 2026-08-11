package HK.PrettyWorks_BE.agent.suggestion.application;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionRequest;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionResponse;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionResult;
import HK.PrettyWorks_BE.agent.suggestion.gateway.AgentSuggestionClient;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 이 서비스가 지켜야 하는 것은 둘입니다.
 * <ul>
 *   <li><b>이 API는 실패하지 않는다</b> — 무엇이 터지든 200에 빈 배열</li>
 *   <li><b>LLM을 쓸데없이 부르지 않는다</b> — 캐시(순차 연타) + inFlight(동시 연타)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AgentSuggestionServiceTest {

    private static final Long USER_ID = 7L;
    private static final long SUGGESTION_TIMEOUT_MILLIS = 15_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AgentSuggestionMaterialService materialService;

    @Mock
    private AgentSuggestionClient suggestionClient;

    @Mock
    private AgentSuggestionCache cache;

    private AgentSuggestionService agentSuggestionService;

    @BeforeEach
    void setUp() {
        // @InjectMocks 대신 직접 만든다 — 생성자에 @Value로 받는 long이 있어
        // 자동 주입에 맡기면 대기 시간이 0이 되고, 동시성 테스트가 의미를 잃는다.
        agentSuggestionService = new AgentSuggestionService(
                materialService, suggestionClient, cache, SUGGESTION_TIMEOUT_MILLIS);
    }

    // ============================ 실패하지 않는다 ============================

    @Test
    void 에이전트_서버가_죽어도_빈_배열로_내려간다() {
        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong()))
                .thenThrow(BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE));

        AgentSuggestionResponse response = agentSuggestionService.get(USER_ID, "HOME");

        // 예외가 밖으로 나가지 않는다. 프론트는 추천 영역만 접으면 된다.
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    void 재료_수집이_실패해도_빈_배열로_내려간다() {
        when(materialService.collect(eq(USER_ID), any(), any()))
                .thenThrow(new IllegalStateException("재료 수집 중 예기치 못한 실패"));

        assertThat(agentSuggestionService.get(USER_ID, "HOME").suggestions()).isEmpty();
    }

    @Test
    void 칩을_해석하지_않고_그대로_돌려준다() {
        JsonNode chip = chip("'환율 연동 검증' 마감이 지났습니다. 처리해드릴까요?",
                "'환율 연동 검증' 마감이 지난 할 일을 처리해줘", "overdue_task");
        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong()))
                .thenReturn(new AgentSuggestionResult(List.of(chip)));

        List<JsonNode> suggestions = agentSuggestionService.get(USER_ID, "HOME").suggestions();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst()).isEqualTo(chip);
    }

    // ============================== screen 정규화 ==============================

    @Test
    void screen을_비우면_HOME으로_보낸다() {
        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong()))
                .thenReturn(new AgentSuggestionResult(List.of()));

        agentSuggestionService.get(USER_ID, "  ");

        assertThat(capturedScreen()).isEqualTo("HOME");
    }

    @Test
    void screen은_대문자로_맞춰_보낸다() {
        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong()))
                .thenReturn(new AgentSuggestionResult(List.of()));

        agentSuggestionService.get(USER_ID, " project ");

        assertThat(capturedScreen()).isEqualTo("PROJECT");
    }

    // ============================ 캐시(순차 연타) ============================

    @Test
    void 보관된_칩이_있으면_LLM을_부르지_않는다() {
        JsonNode cached = chip("보관된 칩", "보관된 요청문", "leave");
        when(cache.find(USER_ID, "HOME")).thenReturn(Optional.of(List.of(cached)));

        List<JsonNode> suggestions = agentSuggestionService.get(USER_ID, "HOME").suggestions();

        assertThat(suggestions).containsExactly(cached);
        // 캐시가 맞았으면 재료 수집도, FastAPI 호출도 일어나지 않아야 캐시가 값을 하는 것이다.
        verifyNoInteractions(materialService, suggestionClient);
    }

    @Test
    void 만들어진_칩은_캐시에_넣는다() {
        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong()))
                .thenReturn(new AgentSuggestionResult(List.of(chip("새 칩", "새 요청문", "due_soon"))));

        agentSuggestionService.get(USER_ID, "HOME");

        verify(cache).put(eq(USER_ID), eq("HOME"), any());
    }

    @Test
    void 생성에_실패하면_폴백을_캐시에_넣지_않는다() {
        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong()))
                .thenThrow(BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE));

        agentSuggestionService.get(USER_ID, "HOME");

        // 빈 폴백이 저장되면 FastAPI가 잠깐 흔들린 대가로 TTL 내내 추천이 사라진다.
        verify(cache, never()).put(anyLong(), any(), any());
    }

    // =========================== inFlight(동시 연타) ===========================

    @Test
    void 동시에_들어오면_LLM은_한_번만_돌고_둘_다_같은_칩을_받는다() throws Exception {
        JsonNode made = chip("동시 요청", "동시 요청문", "project_check");
        AtomicReference<AgentSuggestionResponse> loserResult = new AtomicReference<>();
        AtomicReference<Thread> loserThread = new AtomicReference<>();

        when(materialService.collect(eq(USER_ID), any(), any())).thenReturn(materials());
        when(suggestionClient.generate(any(), anyLong())).thenAnswer(invocation -> {
            // 이 안에 있는 동안 첫 요청은 inFlight에 등록된 상태다. 그래서 여기서 띄우는 두 번째
            // 요청은 반드시 "진 쪽"이 되어 새로 만들지 않고 첫 요청의 결과를 기다리게 된다.
            Thread loser = new Thread(
                    () -> loserResult.set(agentSuggestionService.get(USER_ID, "HOME")));
            loser.start();
            loserThread.set(loser);
            // 고정 sleep 대신 진 쪽이 실제로 대기에 들어간 것을 확인하고 나서 반환한다.
            // 그래야 "두 번째가 등록되기 전에 첫 번째가 끝나 버리는" 경합이 남지 않는다.
            awaitBlocked(loser);
            return new AgentSuggestionResult(List.of(made));
        });

        List<JsonNode> winner = agentSuggestionService.get(USER_ID, "HOME").suggestions();
        loserThread.get().join(TimeUnit.SECONDS.toMillis(10));

        assertThat(winner).containsExactly(made);
        assertThat(loserResult.get().suggestions()).containsExactly(made);
        // 핵심. 탭을 두 개 열어도 LLM은 한 번만 돈다.
        verify(suggestionClient, times(1)).generate(any(), anyLong());
        // 재료 수집도 한 번뿐이다 — 진 쪽은 DB도 다시 긁지 않는다.
        verify(materialService, times(1)).collect(eq(USER_ID), any(), any());
    }

    // "화면이 다르면 각자 만든다"는 테스트를 두지 않는다. 그것을 확인하려면 두 스레드가 같은
    // Mockito mock을 동시에 타야 하는데, 한쪽이 Answer 안에 있는 동안 다른 쪽이 같은 mock을
    // 호출하면 락 경합으로 테스트가 그대로 멈춘다(실제로 걸렸다). 화면이 키에 들어간다는 사실은
    // 위의 캐시 테스트(put에 screen이 넘어가는지)로 확인하고, inFlight 키는 같은 문자열을 쓴다.

    // ================================= 헬퍼 =================================

    /**
     * 스레드가 실제로 대기 상태에 들어갈 때까지 기다립니다.
     *
     * <p>고정 {@code sleep}은 빌드가 느린 순간(콜드 JIT·CI 부하)에 그대로 깨집니다.
     * 이 테스트에서 스레드가 막힐 곳은 {@code CompletableFuture.get} 하나뿐이라
     * 대기 상태 진입을 곧 "진 쪽으로 등록 완료"로 읽을 수 있습니다.</p>
     */
    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("두 번째 요청이 대기 상태로 들어가지 않았습니다");
    }

    private String capturedScreen() {
        ArgumentCaptor<String> screen = ArgumentCaptor.forClass(String.class);
        verify(materialService).collect(eq(USER_ID), screen.capture(), any(LocalDate.class));
        return screen.getValue();
    }

    private JsonNode chip(String text, String prompt, String kind) {
        return objectMapper.readTree(
                "{\"text\":\"%s\",\"prompt\":\"%s\",\"kind\":\"%s\"}".formatted(text, prompt, kind));
    }

    private AgentSuggestionRequest materials() {
        return AgentSuggestionRequest.builder()
                .today(LocalDate.of(2026, 8, 11))
                .screen("HOME")
                .projects(List.of())
                .tasks(List.of())
                .meetings(List.of())
                .upcomingMeetings(List.of())
                .recentQuestions(List.of())
                .build();
    }
}
