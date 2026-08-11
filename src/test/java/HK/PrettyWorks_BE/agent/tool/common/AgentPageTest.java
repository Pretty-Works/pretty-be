package HK.PrettyWorks_BE.agent.tool.common;

import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPageTest {
    @Test
    void marksTheResponseAsTruncatedWhenMoreRowsExist() {
        AgentPage<String> page = AgentPage.of(List.of("a", "b"), 3);

        assertThat(page.content()).containsExactly("a", "b");
        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.truncated()).isTrue();
    }

    @Test
    void rejectsSizesOutsideTheCommonLimit() {
        assertThatThrownBy(() -> AgentPage.validateSize(0)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> AgentPage.validateSize(51)).isInstanceOf(BaseException.class);
        assertThat(AgentPage.validateSize(50)).isEqualTo(50);
    }
}
