package HK.PrettyWorks_BE.agent.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ParamsCanonicalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ParamsCanonicalizer canonicalizer = new ParamsCanonicalizer(objectMapper);

    @Test
    void sortsObjectKeysRecursivelyWhilePreservingNullAndArrayOrder() {
        var input = objectMapper.readTree(
                "{\"z\":1,\"a\":{\"y\":2,\"x\":null},\"list\":[3,1]}");

        var canonical = canonicalizer.canonicalize(input);

        assertThat(canonical.value())
                .isEqualTo("{\"a\":{\"x\":null,\"y\":2},\"list\":[3,1],\"z\":1}");
        assertThat(canonical.hash())
                .isEqualTo(canonicalizer.hashRaw(
                        canonical.value().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void logicallyEqualObjectsProduceTheSameHash() {
        var first = canonicalizer.canonicalize(objectMapper.readTree("{\"projectId\":7,\"content\":\"A\"}"));
        var second = canonicalizer.canonicalize(objectMapper.readTree("{\"content\":\"A\",\"projectId\":7}"));

        assertThat(first).isEqualTo(second);
    }
}
