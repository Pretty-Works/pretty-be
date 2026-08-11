package HK.PrettyWorks_BE.agent.execution.gateway.dto;

import java.util.Arrays;

public enum AgentServerEventType {
    STEP("step"),
    APPROVAL_REQUEST("approval_request"),
    QUESTION("question"),
    DONE("done"),
    ERROR("error");

    private final String wireValue;

    AgentServerEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AgentServerEventType fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown event type"));
    }
}
