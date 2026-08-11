package HK.PrettyWorks_BE.agent.shared.json;

import HK.PrettyWorks_BE.global.util.Sha256;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ParamsCanonicalizer {
    private final ObjectMapper objectMapper;

    public CanonicalParams canonicalize(Object params) {
        return canonicalize(objectMapper.valueToTree(params));
    }

    public CanonicalParams canonicalize(JsonNode params) {
        StringBuilder canonical = new StringBuilder();
        append(params, canonical);
        String value = canonical.toString();
        return new CanonicalParams(value, Sha256.hash(value));
    }

    public String hashRaw(byte[] body) { return Sha256.hash(body); }

    private void append(JsonNode node, StringBuilder target) {
        if (node == null || node.isNull()) {
            target.append("null");
        } else if (node.isObject()) {
            target.append('{');
            boolean[] first = {true};
            node.properties().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(property -> {
                if (!first[0]) target.append(',');
                first[0] = false;
                target.append(objectMapper.writeValueAsString(property.getKey())).append(':');
                append(property.getValue(), target);
            });
            target.append('}');
        } else if (node.isArray()) {
            target.append('[');
            boolean first = true;
            for (JsonNode element : node) {
                if (!first) target.append(',');
                first = false;
                append(element, target);
            }
            target.append(']');
        } else {
            target.append(objectMapper.writeValueAsString(node));
        }
    }

    public record CanonicalParams(String value, String hash) {}
}
