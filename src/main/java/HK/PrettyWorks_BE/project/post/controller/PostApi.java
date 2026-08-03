package HK.PrettyWorks_BE.project.post.controller;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.dto.req.PostCreateRequest;
import HK.PrettyWorks_BE.project.post.dto.req.PostUpdateRequest;
import HK.PrettyWorks_BE.project.post.dto.res.PostCreateResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDeleteResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDetailResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;

// 파라미터 제약(@Valid·@Size)은 반드시 이 인터페이스(최상위 선언)에만 둡니다.
// 구현체(PostController)에서 다시 선언하면 Bean Validation 규칙 위반(HV000151)으로 호출 시 500이 납니다.
@Tag(name = "게시판", description = "게시판 관련 API")
public interface PostApi {

    // 게시글 작성
    @Operation(
            summary = "게시글 작성",
            description = """
                    특정 프로젝트 게시판에 게시글을 작성합니다.

                    - 작성자(로그인 사용자)는 해당 프로젝트의 참여중(ACTIVE) 멤버여야 합니다.
                    - 완료/보관된 프로젝트에는 작성할 수 없습니다.
                    - 중요도(priority)는 HIGH / MID / LOW 중 하나입니다.
                    - Idempotency-Key 헤더를 보내면 연타·재시도로 게시글이 중복 생성되지 않습니다.
                    - 검증 순서: 프로젝트 존재(404) → 참여중 멤버(403) → 프로젝트 상태(400)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "게시글 작성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "postId": 8 } }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 업무 규칙 위반",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_003", "message": "완료 또는 보관된 프로젝트에는 게시글을 추가/수정할 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_004", "message": "프로젝트를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<PostCreateResponse> createPost(
            Long projectId,
            Long authorId,
            @Size(max = 64, message = "Idempotency-Key는 64자 이하여야 합니다.") String idempotencyKey,
            @Valid PostCreateRequest request);

    // 게시글 목록 조회
    @Operation(
            summary = "게시글 목록 조회",
            description = """
                    프로젝트의 게시글 목록을 검색·페이징하여 조회합니다. (해당 프로젝트의 참여중 멤버만 가능)

                    - title: 제목 부분 일치 검색 (선택)
                    - priority: 중요도 필터, HIGH / MID / LOW (선택, 잘못된 값이면 400)
                    - page는 0 이상, size는 1~100 — 범위를 벗어나면 400
                    - 작성일(createdAt) 최신순으로 정렬됩니다.
                    - 검증 순서: 프로젝트 존재(404) → 참여중 멤버(403)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "content": [
                                          {
                                            "postId": 3,
                                            "title": "임베딩 모델 비교표",
                                            "priority": "LOW",
                                            "authorName": "강지우",
                                            "department": "DATA",
                                            "createdAt": "2026-07-28 10:00:00"
                                          },
                                          {
                                            "postId": 2,
                                            "title": "인덱싱 개선 논의",
                                            "priority": "MID",
                                            "authorName": "이하늘",
                                            "department": "BACKEND",
                                            "createdAt": "2026-07-14 10:00:00"
                                          },
                                          {
                                            "postId": 1,
                                            "title": "킥오프 회의록 공유",
                                            "priority": "HIGH",
                                            "authorName": "김피엠",
                                            "department": "PM",
                                            "createdAt": "2026-06-09 10:00:00"
                                          }
                                        ],
                                        "page": 0,
                                        "size": 10,
                                        "totalElements": 3,
                                        "totalPages": 1,
                                        "last": true
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "올바르지 않은 조회 조건 (page/size 범위 초과, 잘못된 priority 값 등)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "'priority' 파라미터 값이 잘못되었습니다: URGENT", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_004", "message": "프로젝트를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<PageResponse<PostListResponse>> getPostList(
            Long projectId,
            Long userId,
            String title,
            PostPriority priority,
            int page,
            int size);

    // 게시글 상세 조회
    @Operation(
            summary = "게시글 상세 조회",
            description = """
                    프로젝트에 속한 게시글 1건의 상세 정보를 조회합니다. (해당 프로젝트의 참여중 멤버만 가능)

                    - 작성자 정보(userId·이름·부서)를 함께 내려줍니다. (본인 글 여부 판단·수정 화면 프리필용)
                    - URL의 projectId와 게시글의 소속 프로젝트가 일치하는지 검증합니다.
                    - 검증 순서: 프로젝트 존재(404) → 참여중 멤버(403) → 게시글 존재·소속(404)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시글 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "postId": 1,
                                        "title": "킥오프 회의록 공유",
                                        "priority": "HIGH",
                                        "content": "검색 고도화 프로젝트 킥오프 내용 정리했습니다. 확인 부탁드려요.",
                                        "author": { "userId": 1, "name": "김피엠", "department": "PM" },
                                        "createdAt": "2026-06-09 10:00:00",
                                        "modifiedAt": "2026-06-09 10:00:00"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트·게시글을 찾을 수 없거나 해당 프로젝트 소속이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_001", "message": "존재하지 않는 게시글입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<PostDetailResponse> getPostDetail(
            Long projectId,
            Long postId,
            Long userId);

    // 게시글 수정
    @Operation(
            summary = "게시글 수정",
            description = """
                    게시글 1건을 수정합니다. (작성자만 가능, 완료/보관 프로젝트는 불가)

                    - 제목·중요도·내용을 수정합니다.
                    - 작성자·소속 프로젝트는 변경되지 않습니다.
                    - 수정된 최신 상세를 반환합니다.
                    - 검증 순서: 프로젝트 존재(404) → 참여중 멤버(403) → 게시글 존재·소속(404) → 작성자 권한(403) → 프로젝트 상태(400)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시글 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "postId": 1,
                                        "title": "킥오프 회의록 공유(수정)",
                                        "priority": "MID",
                                        "content": "킥오프 내용 v2로 갱신했습니다.",
                                        "author": { "userId": 1, "name": "김피엠", "department": "PM" },
                                        "createdAt": "2026-06-09 10:00:00",
                                        "modifiedAt": "2026-08-03 14:20:00"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 업무 규칙 위반",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_003", "message": "완료 또는 보관된 프로젝트에는 게시글을 추가/수정할 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음(작성자 아님 / 프로젝트 미참여)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_004", "message": "게시글에 대한 권한이 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트·게시글을 찾을 수 없거나 해당 프로젝트 소속이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_001", "message": "존재하지 않는 게시글입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<PostDetailResponse> updatePost(
            Long projectId,
            Long postId,
            Long userId,
            @Valid PostUpdateRequest request);

    // 게시글 삭제
    @Operation(
            summary = "게시글 삭제",
            description = """
                    게시글 1건을 삭제합니다. (작성자만 가능)

                    - soft delete 방식입니다. deleted_at만 기록되며, 이후 목록·상세 조회에서 제외됩니다.
                    - 검증 순서: 프로젝트 존재(404) → 참여중 멤버(403) → 게시글 존재·소속(404) → 작성자 권한(403)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시글 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "postId": 1 } }
                                    """))),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음(작성자 아님 / 프로젝트 미참여)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_004", "message": "게시글에 대한 권한이 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트·게시글을 찾을 수 없거나 해당 프로젝트 소속이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "POST_002", "message": "해당 프로젝트의 게시글이 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<PostDeleteResponse> deletePost(
            Long projectId,
            Long postId,
            Long userId);
}
