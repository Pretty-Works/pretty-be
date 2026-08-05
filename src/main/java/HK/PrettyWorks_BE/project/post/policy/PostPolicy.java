package HK.PrettyWorks_BE.project.post.policy;

import HK.PrettyWorks_BE.project.post.domain.PostEntity;

public final class PostPolicy {

    private PostPolicy() {
    }

    public static boolean canEdit(PostEntity post, Long userId) {
        return post.getAuthorId().equals(userId);
    }

    public static boolean canDelete(PostEntity post, Long userId) {
        return post.getAuthorId().equals(userId);
    }
}
