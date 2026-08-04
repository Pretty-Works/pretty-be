package HK.PrettyWorks_BE.user.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import org.springframework.util.StringUtils;

// 사용자 이름 자동완성 API들의 공통 검색어 정규화·범위 검증.
public record UserSearchCondition(String keyword, int limit) {

    private static final int MAX_KEYWORD_LENGTH = 20;
    private static final int MAX_LIMIT = 20;

    public static UserSearchCondition of(String keyword, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        if (!StringUtils.hasText(keyword)) {
            return new UserSearchCondition(null, limit);
        }

        String normalizedKeyword = keyword.trim();
        if (normalizedKeyword.length() > MAX_KEYWORD_LENGTH
                || !UserPolicy.isValidName(normalizedKeyword)) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        return new UserSearchCondition(normalizedKeyword, limit);
    }

    public boolean isEmpty() {
        return keyword == null;
    }
}
