package HK.PrettyWorks_BE.agent.tool.common;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import java.util.List;

public record AgentPage<T>(List<T> content, long totalCount, boolean truncated) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public AgentPage { content = List.copyOf(content); }

    public static int validateSize(int size) {
        return validateSize(size, MAX_SIZE);
    }

    // 도구마다 상한이 다르다(목록 50 · 회의록 20 · 사용자 검색 20). 범위를 벗어나면 REQUEST_001.
    public static int validateSize(int size, int maxSize) {
        if (size < 1 || size > maxSize) throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        return size;
    }

    public static <T> AgentPage<T> of(List<T> content, long totalCount) {
        return new AgentPage<>(content, totalCount, totalCount > content.size());
    }
}
