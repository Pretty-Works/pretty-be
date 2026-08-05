package HK.PrettyWorks_BE.project.post.controller;

import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.dto.req.PostCreateRequest;
import HK.PrettyWorks_BE.project.post.dto.req.PostUpdateRequest;
import HK.PrettyWorks_BE.project.post.dto.res.PostCreateResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDeleteResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDetailResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostListResponse;
import HK.PrettyWorks_BE.project.post.service.PostService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/posts")
public class PostController implements PostApi {
    private final PostService postService;

    // 게시글 작성
    @Override
    @PostMapping
    public ResponseEntity<PostCreateResponse> createPost(
            @PathVariable Long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long authorId,
            @Parameter(description = "중복 생성 방지용 멱등 키. 폼 열릴 때 UUID v4 발급해 두고 연타·재시도 시 같은 키 재사용",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PostCreateRequest request) {
        PostCreateResponse response = postService.createPost(projectId, authorId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시글 목록 조회
    @Override
    @GetMapping
    public ResponseEntity<PageResponse<PostListResponse>> getPostList(
            @PathVariable Long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "게시글 제목 검색어 (부분 일치)")
            @RequestParam(required = false) String title,
            @Parameter(description = "중요도 필터 (HIGH / MID / LOW)")
            @RequestParam(required = false) PostPriority priority,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지당 개수")
            @RequestParam(defaultValue = "10") int size) {

        // 잘못된 page/size(page<0, size<1, size>100)는 PageRequests가 400으로 거부한다
        PageResponse<PostListResponse> response =
                postService.getPostList(projectId, userId, title, priority, PageRequests.of(page, size));
        return ResponseEntity.ok(response);
    }

    // 게시글 상세 조회
    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(
            @PathVariable Long projectId,
            @PathVariable Long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        PostDetailResponse response = postService.getPostDetail(projectId, postId, userId);
        return ResponseEntity.ok(response);
    }

    // 게시글 수정
    @Override
    @PutMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable Long projectId,
            @PathVariable Long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PostUpdateRequest request) {
        PostDetailResponse response = postService.updatePost(projectId, postId, userId, request);
        return ResponseEntity.ok(response);
    }

    // 게시글 삭제
    @Override
    @DeleteMapping("/{postId}")
    public ResponseEntity<PostDeleteResponse> deletePost(
            @PathVariable Long projectId,
            @PathVariable Long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        PostDeleteResponse response = postService.deletePost(projectId, postId, userId);
        return ResponseEntity.ok(response);
    }
}
