package HK.PrettyWorks_BE.project.post.dto.res;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostListResponse(
        @Schema(example = "3", description = "게시글 id")
        Long postId,

        @Schema(example = "임베딩 모델 비교표", description = "게시글 제목")
        String title,

        @Schema(example = "LOW", description = "중요도 (HIGH / MID / LOW)")
        PostPriority priority,

        @Schema(example = "강지우", description = "작성자 이름")
        String authorName,

        @Schema(example = "DATA", description = "작성자 부서")
        String department,

        @Schema(example = "2026-07-28 10:00:00", description = "작성 일시")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime createdAt
) {
}
