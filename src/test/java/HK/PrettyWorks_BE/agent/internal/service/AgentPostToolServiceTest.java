package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.internal.dto.req.AgentPostCreateRequest;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentPostDetailResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentPostListResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentWriteResults;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.dto.req.PostCreateRequest;
import HK.PrettyWorks_BE.project.post.dto.res.PostCreateResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDetailResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostListResponse;
import HK.PrettyWorks_BE.project.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPostToolServiceTest {

    private final PostService postService = mock(PostService.class);
    private final AgentPostToolService service = new AgentPostToolService(postService);

    @Test
    void signalsTruncationSoTheAgentDoesNotAnswerAsIfItSawEveryPost() {
        PostListResponse post = PostListResponse.builder()
                .postId(3L)
                .title("배포 일정 공지")
                .priority(PostPriority.HIGH)
                .authorName("김피엠")
                .department("PM")
                .createdAt(LocalDateTime.of(2026, 8, 3, 10, 0))
                .build();
        when(postService.getPostList(eq(7L), eq(3L), isNull(), isNull(), any()))
                .thenReturn(new PageResponse<>(List.of(post), 0, 1, 12, 12, false));

        AgentPostListResponse result = service.list(3L, 7L, null, null, 1);

        assertThat(result.posts()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.truncated()).isTrue();
        // 코드값과 한국어 라벨을 함께 준다. 에이전트가 HIGH를 임의로 옮겨 적지 않게 하려는 것이다.
        assertThat(result.posts().get(0).priority()).isEqualTo("HIGH");
        assertThat(result.posts().get(0).priorityLabel()).isEqualTo("높음");
    }

    @Test
    void leavesOrderingToTheRepositoryInsteadOfPassingItsOwnSort() {
        // findPostSummaries가 이미 createdAt DESC, id DESC로 고정돼 있다. 여기서 Sort를 또 주면
        // 같은 절이 한 번 더 붙는다.
        when(postService.getPostList(eq(7L), eq(3L), eq("공지"), eq(PostPriority.LOW), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        service.list(3L, 7L, "공지", "low", 20);

        verify(postService).getPostList(7L, 3L, "공지", PostPriority.LOW, PageRequest.of(0, 20));
    }

    @Test
    void rejectsAnUnknownPriorityBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.list(3L, 7L, null, "URGENT", 20))
                .isInstanceOf(BaseException.class);

        verify(postService, never()).getPostList(any(), any(), any(), any(), any());
    }

    @Test
    void marksAuthorshipSoTheAgentKnowsWhoCanEditOrDelete() {
        // 게시글 수정·삭제는 작성자만 가능하다(POST_004). isMine이 없으면 에이전트가
        // "수정해 드릴까요"를 남의 글에도 말한다.
        when(postService.getPostDetail(7L, 5L, 3L)).thenReturn(detailOf(3L));

        AgentPostDetailResponse mine = service.detail(3L, 7L, 5L);

        assertThat(mine.isMine()).isTrue();
        assertThat(mine.projectId()).isEqualTo(7L);
        assertThat(mine.priorityLabel()).isEqualTo("중간");

        when(postService.getPostDetail(7L, 5L, 9L)).thenReturn(detailOf(3L));

        assertThat(service.detail(9L, 7L, 5L).isMine()).isFalse();
    }

    @Test
    void writesThroughTheSameServiceThePublicApiUses() {
        AgentPostCreateRequest request = new AgentPostCreateRequest(
                7L, "배포 일정 공지", PostPriority.HIGH, "8월 10일 배포 예정입니다.");
        when(postService.createPost(eq(7L), eq(3L), eq("agent:42"), any(PostCreateRequest.class)))
                .thenReturn(PostCreateResponse.builder().postId(8L).build());

        AgentWriteResults.PostCreated created = service.create(3L, request, "agent:42");

        assertThat(created.postId()).isEqualTo(8L);
        assertThat(created.projectId()).isEqualTo(7L);
        assertThat(created.title()).isEqualTo("배포 일정 공지");
        assertThat(created.priority()).isEqualTo("HIGH");
        verify(postService).createPost(7L, 3L, "agent:42",
                new PostCreateRequest("배포 일정 공지", PostPriority.HIGH, "8월 10일 배포 예정입니다."));
    }

    private PostDetailResponse detailOf(Long authorId) {
        return PostDetailResponse.builder()
                .postId(5L)
                .title("배포 일정 공지")
                .priority(PostPriority.MID)
                .content("8월 10일 배포 예정입니다.")
                .author(PostDetailResponse.PersonInfo.builder()
                        .userId(authorId)
                        .name("김피엠")
                        .department("PM")
                        .build())
                .createdAt(LocalDateTime.of(2026, 8, 3, 10, 0))
                .modifiedAt(LocalDateTime.of(2026, 8, 3, 10, 0))
                .build();
    }
}
