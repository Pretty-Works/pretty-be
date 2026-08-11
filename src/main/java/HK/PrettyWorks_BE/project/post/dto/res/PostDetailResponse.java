package HK.PrettyWorks_BE.project.post.dto.res;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostDetailResponse(
        @Schema(example = "1", description = "게시글 id")
        Long postId,

        @Schema(example = "킥오프 회의록 공유", description = "게시글 제목")
        String title,

        @Schema(example = "HIGH", description = "중요도 (HIGH / MID / LOW)")
        PostPriority priority,

        @Schema(example = "검색 고도화 프로젝트 킥오프 내용 정리했습니다. 확인 부탁드려요.", description = "게시글 내용")
        String content,

        @Schema(description = "작성자 정보")
        PersonInfo author,

        @Schema(example = "2026-06-09 10:00:00", description = "작성 일시")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime createdAt,

        @Schema(example = "2026-08-03 14:20:00", description = "최종 수정 일시")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime modifiedAt
) {

    @Builder
    public record PersonInfo(
            @Schema(example = "1", description = "사용자 id")
            Long userId,

            @Schema(example = "김피엠", description = "사용자 이름")
            String name,

            @Schema(example = "PM", description = "사용자 부서")
            String department
    ) {
    }
}
