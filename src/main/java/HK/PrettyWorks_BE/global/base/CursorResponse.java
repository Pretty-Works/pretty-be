package HK.PrettyWorks_BE.global.base;

import java.util.List;

// 커서 페이지네이션 응답. 계속 위로 쌓이는 목록(알림 등)에 쓴다.
// offset이면 스크롤 도중 새 항목이 들어올 때 경계가 밀려 중복·누락이 생기고, PageResponse와 달리
// 전체 개수를 세지 않으므로 count 쿼리도 사라진다.
public record CursorResponse<T>(
        List<T> items,          // 실제 목록
        Long nextCursor,        // 다음 요청의 cursor. 목록이 비면 null
        boolean hasNext         // 더 가져올 게 남았는지
) {
}
