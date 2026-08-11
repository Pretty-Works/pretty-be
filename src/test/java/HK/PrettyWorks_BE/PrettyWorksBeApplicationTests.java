package HK.PrettyWorks_BE;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// 애플리케이션 컨텍스트가 뜨는지만 본다. 빈 배선과 JPQL 파싱이 여기서 걸린다.
//
// 비밀값은 운영 설정에 기본값을 두지 않는다 — 없으면 부팅이 죽는 게 맞다.
// 그래서 테스트에서만 더미를 주입한다. application.yml에 `${...:기본값}`을 넣으면
// 운영에서도 비밀값 없이 뜨게 되어, 막으려던 사고를 테스트 편의로 되살리는 꼴이 된다.
//
// jwt.secret-key와 approval-token-secret은 **base64로 디코딩한 뒤** HMAC-SHA256 키로 쓰인다
// (JwtUtil.afterPropertiesSet · ApprovalTokenService). 평문을 넣으면 디코딩 단계에서 죽으므로
// base64 문자만으로 채운다. 아래 두 값은 각각 48바이트로 디코딩된다(HS256 최소 32바이트 충족).
@SpringBootTest
@TestPropertySource(properties = {
        "agent.internal.api-key=test-internal-api-key",
        "agent.internal.approval-token-secret=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        "jwt.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
})
class PrettyWorksBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
