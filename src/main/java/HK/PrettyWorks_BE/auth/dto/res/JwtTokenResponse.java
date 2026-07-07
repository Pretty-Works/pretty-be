package HK.PrettyWorks_BE.auth.dto.res;

import lombok.Builder;

@Builder
public record JwtTokenResponse(
        String accessToken,

        String refreshToken
) {
}
