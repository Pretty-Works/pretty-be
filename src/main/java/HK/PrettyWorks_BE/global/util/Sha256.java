package HK.PrettyWorks_BE.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// SHA-256 해시(hex 64자) 공용 유틸.
// 멱등 키의 요청 지문, refresh token 저장값 등 "원본을 보관하지 않고 대조만 하면 되는" 값에 사용합니다.
public final class Sha256 {

    private Sha256() {
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 제공하도록 표준에 명시되어 있어 실제로는 발생하지 않습니다.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
