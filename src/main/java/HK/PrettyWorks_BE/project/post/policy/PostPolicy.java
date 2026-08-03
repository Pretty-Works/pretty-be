package HK.PrettyWorks_BE.project.post.policy;

import HK.PrettyWorks_BE.project.post.domain.PostEntity;

public final class PostPolicy {

    private PostPolicy() {
    }

    // 수정 권한: 작성자만 가능
    public static boolean canEdit(PostEntity post, Long userId) {
        return post.getAuthorId().equals(userId);
    }

    // 삭제 권한: 작성자만 가능
    public static boolean canDelete(PostEntity post, Long userId) {
        return post.getAuthorId().equals(userId);
    }
}