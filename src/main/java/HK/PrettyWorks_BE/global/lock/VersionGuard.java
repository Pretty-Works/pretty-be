package HK.PrettyWorks_BE.global.lock;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;

import java.util.Objects;

// 낙관적 락의 선행 조건 검사. 도메인 무관하게 재사용합니다.
// 클라이언트가 조회 시점에 받아둔 버전과 현재 버전이 다르면, 그 사이 다른 사용자가 먼저 수정한 것이므로 차단합니다.
public final class VersionGuard {

    private VersionGuard() {
    }

    // 반드시 Objects.equals로 비교합니다. Long은 -128~127만 캐싱되어 '!='로 비교하면 128부터 참조 비교가 되고,
    // '.equals()'는 아직 저장되지 않은 엔티티의 null 버전에서 NPE가 납니다.
    public static void validate(Long currentVersion, Long expectedVersion) {
        if (!Objects.equals(currentVersion, expectedVersion)) {
            throw BaseException.type(GlobalErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
