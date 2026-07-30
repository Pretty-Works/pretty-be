package HK.PrettyWorks_BE.auth.jwt;

// access/refresh 토큰 쌍을 JwtUtil→AuthService→Controller로 전달하는 내부 전용 타입입니다.
// API 응답 DTO(JwtTokenResponse)와 분리해, refreshToken이 실수로 응답 바디에 섞여 나가지 않게 합니다.
public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
