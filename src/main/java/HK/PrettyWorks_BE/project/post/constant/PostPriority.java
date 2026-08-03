package HK.PrettyWorks_BE.project.post.constant;

// 게시글 중요도. @Enumerated(EnumType.STRING)으로 저장하며 ORDINAL 저장은 금지.
public enum PostPriority {
    HIGH("높음"),
    MID("중간"),
    LOW("낮음");

    private final String description;

    PostPriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
