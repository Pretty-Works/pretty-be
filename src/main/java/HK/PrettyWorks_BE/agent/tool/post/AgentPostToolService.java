package HK.PrettyWorks_BE.agent.tool.post;

import HK.PrettyWorks_BE.agent.tool.common.AgentPage;
import HK.PrettyWorks_BE.agent.tool.common.AgentWriteResults;
import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostCreateRequest;
import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostDetailResponse;
import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostListResponse;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.dto.res.PostCreateResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDetailResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostListResponse;
import HK.PrettyWorks_BE.project.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

// post.list · post.detail · post.create.
//
// 회의록과 같은 구조다 — 목록은 훑기용, 본문은 상세 한 건. 접근 권한·프로젝트 상태·멱등은
// 전부 PostService가 소유하고, 여기서는 상한을 걸고 응답을 최소 필드로 좁힌다.
@Service
@RequiredArgsConstructor
public class AgentPostToolService {

    private final PostService postService;

    // ================================= 조회 =================================

    public AgentPostListResponse list(Long userId, Long projectId, String title,
                                      String priority, int size) {
        // 회의록(20)보다 상한을 낮출 이유가 없다. 목록 한 줄이 제목·중요도·작성자·일시뿐이라
        // 항목당 길이가 짧다. 공용 상한 50을 그대로 쓴다.
        int validatedSize = AgentPage.validateSize(size);

        // 정렬을 넘기지 않는다. PostRepository.findPostSummaries가 이미
        // ORDER BY createdAt DESC, id DESC로 고정돼 있고(동점에도 순서가 흔들리지 않는다),
        // 여기서 Sort를 또 주면 같은 절이 뒤에 한 번 더 붙는다.
        PageResponse<PostListResponse> page = postService.getPostList(
                projectId, userId, title, parsePriority(priority), PageRequest.of(0, validatedSize));

        List<AgentPostListResponse.AgentPost> posts = page.content().stream()
                .map(post -> AgentPostListResponse.AgentPost.builder()
                        .postId(post.postId())
                        .title(post.title())
                        .priority(post.priority().name())
                        .priorityLabel(post.priority().getDescription())
                        .authorName(post.authorName())
                        .department(post.department())
                        .createdAt(post.createdAt())
                        .build())
                .toList();

        return AgentPostListResponse.builder()
                .projectId(projectId)
                .posts(posts)
                .totalCount(posts.size())
                .truncated(page.totalElements() > posts.size())
                .build();
    }

    public AgentPostDetailResponse detail(Long userId, Long projectId, Long postId) {
        PostDetailResponse source = postService.getPostDetail(projectId, postId, userId);

        // 작성자를 찾지 못하면 상세 조회 자체가 POST_005로 끊긴다. 여기까지 왔으면 author는 항상 있다.
        PostDetailResponse.PersonInfo author = source.author();

        return AgentPostDetailResponse.builder()
                .postId(source.postId())
                .projectId(projectId)
                .title(source.title())
                .priority(source.priority().name())
                .priorityLabel(source.priority().getDescription())
                .content(source.content())
                .authorId(author.userId())
                .authorName(author.name())
                .department(author.department())
                .createdAt(source.createdAt())
                .modifiedAt(source.modifiedAt())
                .isMine(userId.equals(author.userId()))
                .build();
    }

    // ================================= 쓰기 =================================

    // 승인된 게시글을 실제로 저장한다. 검증·멱등은 공개 API와 같은 PostService.createPost가 담당한다.
    public AgentWriteResults.PostCreated create(Long userId, AgentPostCreateRequest request,
                                                String idempotencyKey) {
        Long projectId = request.projectId();
        PostCreateResponse created =
                postService.createPost(projectId, userId, idempotencyKey, request.toDomain());

        // 저장 뒤 다시 읽지 않는다. 회의록의 문서번호처럼 서버가 붙이는 파생값이 없어
        // 저장된 값이 곧 요청값이다 — 조회를 한 번 더 걸 이유가 없다.
        return AgentWriteResults.PostCreated.builder()
                .postId(created.postId())
                .projectId(projectId)
                .title(request.title())
                .priority(request.priority().name())
                .build();
    }

    // ================================= 내부 헬퍼 =================================

    private PostPriority parsePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return null;
        }
        try {
            return PostPriority.valueOf(priority.trim().toUpperCase());
        } catch (IllegalArgumentException unknownPriority) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }
}
