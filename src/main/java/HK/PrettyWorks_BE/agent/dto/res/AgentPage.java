package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import java.util.List;

public record AgentPage<T>(List<T> content, long totalCount, boolean truncated) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public AgentPage { content = List.copyOf(content); }

    public static int validateSize(int size) {
        if (size < 1 || size > MAX_SIZE) throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        return size;
    }

    public static <T> AgentPage<T> of(List<T> content, long totalCount) {
        return new AgentPage<>(content, totalCount, totalCount > content.size());
    }
}
