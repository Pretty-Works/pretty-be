package HK.PrettyWorks_BE.global.base;

import java.util.List;

// 커서 페이지네이션 응답. 계속 위로 쌓이는 목록(알림 등)에 쓴다.
// offset이면 스크롤 도중 새 항목이 들어올 때 경계가 밀려 중복·누락이 생기고, PageResponse와 달리
// 전체 개수를 세지 않으므로 count 쿼리도 사라진다.
//
// 커서 타입(C)이 목록마다 다르다. 알림처럼 id 하나로 기준점이 정해지면 Long이고,
// 에이전트 대화 목록처럼 정렬 키가 두 개면(last_message_at, id) 둘을 묶은 문자열이다.
public record CursorResponse<T, C>(
        List<T> items,          // 실제 목록
        C nextCursor,           // 다음 요청의 cursor. 목록이 비면 null
        boolean hasNext         // 더 가져올 게 남았는지
) {
}
