package HK.PrettyWorks_BE.global.base;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(
        List<T> content,        // 실제 목록
        int page,               // 현재 페이지 번호
        int size,               // 페이지 크기
        long totalElements,     // 전체 개수
        int totalPages,         // 전체 페이지 수
        boolean last            // 마지막 페이지 여부
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}