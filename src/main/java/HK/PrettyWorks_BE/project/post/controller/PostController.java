package HK.PrettyWorks_BE.project.post.controller;

import HK.PrettyWorks_BE.project.post.dto.req.PostCreateRequest;
import HK.PrettyWorks_BE.project.post.service.PostService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "게시판", description = "게시판 관련 API")
@RestController
@RequiredArgsConstructor
public class PostController {
//        private final PostService postService;
//
//    @PostMapping
//    public ResponseEntity<PostCreateRequest> createPost(
//            @PathVariable Long projectId,
//            @Parameter(hidden = true) @AuthenticationPrincipal Long authorId,
//            @Parameter(description = "중복 생성 방지용 멱등 키. 폼 열릴 때 UUID v4 발급해 두고 연타·재시도 시 같은 키 재사용",
//                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
//            @Size(max = 64, message = "Idempotency-Key는 64자 이하여야 합니다.")
//            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
//            @Valid @RequestBody PostCreateRequest request) {
//        PostCreateRequest request = PostService.createPost()
//        return null;
//    }

}
