package HK.PrettyWorks_BE.project.post.dto.res;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostListResponse(
        Long postId,
        String title,
        PostPriority priority,
        String authorName,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime createdAt
) {
}