package HK.PrettyWorks_BE.project.post.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import HK.PrettyWorks_BE.notification.constant.NotificationTarget;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.notification.event.NotificationPublisher;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.domain.PostEntity;
import HK.PrettyWorks_BE.project.post.dto.req.PostCreateRequest;
import HK.PrettyWorks_BE.project.post.dto.req.PostUpdateRequest;
import HK.PrettyWorks_BE.project.post.dto.res.PostCreateResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDeleteResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostDetailResponse;
import HK.PrettyWorks_BE.project.post.dto.res.PostListResponse;
import HK.PrettyWorks_BE.project.post.exception.PostErrorCode;
import HK.PrettyWorks_BE.project.post.policy.PostPolicy;
import HK.PrettyWorks_BE.project.post.repository.PostRepository;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final CurrentUserService currentUserService;
    private final NotificationPublisher notificationPublisher;

    // 멱등 처리 진입점
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않음
    public PostCreateResponse createPost(
            Long projectId, Long authorId, String idempotencyKey, PostCreateRequest request) {

        // 퇴사 직후에도 남아 있을 수 있는 access token으로 새 글을 쓰지 못하게 한다.
        currentUserService.getEmployedUser(authorId);

        String path = "/api/v1/projects/" + projectId + "/posts";

        Supplier<Long> creator = () -> doCreate(projectId, authorId, request);
        String fingerprint = idempotencyService.fingerprint("POST", path, request);

        Long postId = idempotencyService.run(idempotencyKey, "POST " + path, authorId, fingerprint, creator);

        return PostCreateResponse.builder()
                .postId(postId)
                .build();
    }

    // 게시글 작성
    private Long doCreate(Long projectId, Long authorId, PostCreateRequest request) {

        // 존재하는 프로젝트인지 확인
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 작성자가 이 프로젝트의 참여중 멤버인지 확인
        projectMemberService.validateActiveMember(projectId, authorId);

        // 완료/보관된 프로젝트가 아닌지 확인
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(PostErrorCode.PROJECT_CLOSED);
        }

        PostEntity post = PostEntity.builder()
                .projectId(projectId)
                .authorId(authorId)
                .title(request.title())
                .priority(request.priority())
                .content(request.content())
                .build();

        postRepository.save(post);

        // HIGH 우선순위 게시글만 프로젝트 멤버 전원에게 알림(전체 발행하면 스팸이라 팀 결정으로 제한).
        // 게시판 목록이 아니라 그 글 상세로 보낸다 — "중요하니 알려줬다"고 해놓고 직접 찾게 하면 앞뒤가 안 맞는다.
        if (post.getPriority() == PostPriority.HIGH) {
            notificationPublisher.publish(NotificationType.POST_CREATED,
                    projectMemberService.getActiveMemberIds(projectId), authorId,
                    NotificationTarget.post(projectId, post.getId()),
                    project.getName(), post.getTitle());
        }

        return post.getId();
    }

    // 게시글 목록 조회
    @Transactional(readOnly = true)
    public PageResponse<PostListResponse> getPostList(
            Long projectId, Long userId, String title, PostPriority priority, Pageable pageable) {

        // 프로젝트 존재 + 참여중 멤버 검증
        projectMemberService.validateAccess(projectId, userId);

        String normalizedTitle =  StringUtils.hasText(title) ? title.trim() : null;

        Page<PostEntity> posts = postRepository.findPostSummaries(
                projectId,
                normalizedTitle,
                priority,
                pageable
        );

        // 이 페이지의 작성자 id를 모아서 한 번에 조회 (N+1 방지)
        List<Long> authorIds = posts.getContent().stream()
                .map(PostEntity::getAuthorId)
                .distinct()
                .toList();

        Map<Long, UserEntity> authors = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        // 작성자가 탈퇴 등으로 조회되지 않아도 목록 자체는 깨지지 않게 대체값을 쓴다
        Page<PostListResponse> mapped = posts.map(post -> {
            UserEntity author = authors.get(post.getAuthorId());
            return PostListResponse.builder()
                    .postId(post.getId())
                    .title(post.getTitle())
                    .priority(post.getPriority())
                    .authorName(author == null ? "알 수 없음" : author.getName())
                    .department(author == null ? null : author.getDepartment().name())
                    .createdAt(post.getCreatedAt())
                    .build();
        });

        return PageResponse.from(mapped);
    }

    // 게시글 상세 조회
    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetail(Long projectId, Long postId, Long userId) {

        // 프로젝트 존재 + 참여중 멤버 검증
        projectMemberService.validateAccess(projectId, userId);

        PostEntity post = getPostInProject(projectId, postId);

        return toDetailResponse(post);
    }

    // 게시글 수정
    @Transactional
    public PostDetailResponse updatePost(
            Long projectId, Long postId, Long userId, PostUpdateRequest request) {

        // 존재하는 프로젝트인지 확인 (상태 검증에도 쓰므로 직접 로드)
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 사용자가 이 프로젝트의 참여중 멤버인지 확인
        projectMemberService.validateActiveMember(projectId, userId);

        // 게시글 존재 + 프로젝트 소속 확인
        PostEntity post = getPostInProject(projectId, postId);

        // 작성자만 수정 가능 (권한을 상태 검증보다 먼저 판정)
        if (!PostPolicy.canEdit(post, userId)) {
            throw BaseException.type(PostErrorCode.NO_PERMISSION);
        }

        // 권한이 없는 요청에는 사용자 상태나 프로젝트 상태보다 권한 오류를 우선한다.
        currentUserService.getEmployedUser(userId);

        // 완료/보관된 프로젝트가 아닌지 확인
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(PostErrorCode.PROJECT_CLOSED);
        }

        post.update(request.title(), request.priority(), request.content());

        // HIGH 우선순위 게시글만 프로젝트 멤버 전원에게 알림(생성과 동일 기준 — 스팸 방지).
        if (post.getPriority() == PostPriority.HIGH) {
            notificationPublisher.publish(NotificationType.POST_UPDATED,
                    projectMemberService.getActiveMemberIds(projectId), userId,
                    NotificationTarget.post(projectId, post.getId()),
                    project.getName(), post.getTitle());
        }

        return toDetailResponse(post);
    }

    // 게시글 삭제
    @Transactional
    public PostDeleteResponse deletePost(Long projectId, Long postId, Long userId) {

        // 프로젝트 존재 + 참여중 멤버 검증
        projectMemberService.validateAccess(projectId, userId);

        PostEntity post = getPostInProject(projectId, postId);

        // 작성자만 삭제 가능
        if (!PostPolicy.canDelete(post, userId)) {
            throw BaseException.type(PostErrorCode.NO_PERMISSION);
        }

        currentUserService.getEmployedUser(userId);

        // soft delete (@SQLDelete → deleted_at 갱신)
        postRepository.delete(post);

        return PostDeleteResponse.builder()
                .postId(postId)
                .build();
    }

    // 게시글 조회 + 프로젝트 소속 검증 공통 로직
    private PostEntity getPostInProject(Long projectId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> BaseException.type(PostErrorCode.POST_NOT_FOUND));

        if (!projectId.equals(post.getProjectId())) {
            throw BaseException.type(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    // 게시글 + 작성자를 상세 응답 DTO로 조립
    private PostDetailResponse toDetailResponse(PostEntity post) {
        UserEntity author = userRepository.findById(post.getAuthorId())
                .orElseThrow(() -> BaseException.type(PostErrorCode.AUTHOR_NOT_FOUND));

        return PostDetailResponse.builder()
                .postId(post.getId())
                .title(post.getTitle())
                .priority(post.getPriority())
                .content(post.getContent())
                .author(PostDetailResponse.PersonInfo.builder()
                        .userId(author.getId())
                        .name(author.getName())
                        .department(author.getDepartment().name())
                        .build())
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
    }
}
