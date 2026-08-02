package HK.PrettyWorks_BE.global.base;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.springframework.data.domain.PageRequest;

// 페이지 파라미터 검증 + PageRequest 생성.
// 컨트롤러에 @Min·@Max를 붙이면 @Validated가 필요하고, Swagger 인터페이스와 겹쳐 HV000151로 API 전체가 500이 된다.
// 기본값은 각 컨트롤러의 @RequestParam(defaultValue)이 갖고, 여기선 범위만 본다.
public final class PageRequests {

    // 초과 요청은 조용히 줄이지 않고 거부한다. 줄여서 주면 "왜 100건만 오지?"를 알 수 없다.
    private static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        return PageRequest.of(page, size);
    }
}
