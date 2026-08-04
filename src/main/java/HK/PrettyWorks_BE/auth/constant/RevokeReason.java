package HK.PrettyWorks_BE.auth.constant;

// refresh token이 폐기된 사유. "폐기된 토큰이 다시 왔을 때" 도난인지 아닌지를 가르는 기준입니다.
public enum RevokeReason {
    // 재발급으로 정상 회전됨. 이 상태의 토큰이 다시 오면 누군가 옛 토큰을 들고 있다는 뜻이라 도난으로 봅니다.
    ROTATED("회전"),
    // 사용자가 직접 로그아웃함. 이후 재시도는 정상적인 흐름이라 도난 경보를 울리지 않습니다.
    LOGOUT("로그아웃"),
    // 다른 토큰의 재사용이 감지되어 세션 전체가 끊김.
    THEFT("도난 의심"),
    // 동시 세션 수 상한을 넘어 밀려남.
    SESSION_LIMIT("세션 수 초과"),
    // 재직 상태가 아니어서 차단됨(퇴사).
    NOT_EMPLOYED("퇴사");

    private final String description;

    RevokeReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
