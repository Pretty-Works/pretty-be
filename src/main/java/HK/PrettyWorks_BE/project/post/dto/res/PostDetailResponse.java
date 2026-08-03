package HK.PrettyWorks_BE.project.post.dto.res;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostDetailResponse(
        Long postId,
        String title,
        PostPriority priority,
        String content,
        PersonInfo author,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime modifiedAt
) {
    // 작성자 정보 (이름 + 부서)
    @Builder
    public record PersonInfo(
            Long userId,
            String name,
            String department
    ) {
    }
}