package HK.PrettyWorks_BE.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// 요청 하나에 추적 ID를 붙여 그 요청이 남긴 로그를 전부 이어볼 수 있게 합니다.
//
// 로그 패턴의 %X{traceId}가 MDC에서 값을 꺼내가므로, 각 서비스는 아무것도 하지 않아도 로그에 ID가 붙습니다.
// 응답 헤더로도 내려주어 사용자가 알려준 ID로 서버 로그를 바로 찾을 수 있게 합니다.
//
// 필터 순서를 가장 앞으로 두는 이유: 인증 실패처럼 뒤쪽 필터에서 끝나는 요청도 추적 대상이기 때문입니다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // UUID 앞 8자리. 로그에서 눈으로 훑기 좋을 만큼 짧으면서 한 서버의 동시 요청을 구분하기엔 충분합니다.
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 반드시 지웁니다. 톰캣이 스레드를 재사용하므로 남겨두면 다음 요청이 남의 ID를 달고 로그를 남깁니다.
            MDC.clear();
        }
    }
}
