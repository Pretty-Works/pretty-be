package HK.PrettyWorks_BE.agent.client.dto;

import HK.PrettyWorks_BE.agent.constant.AgentFileEncoding;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * FastAPI에 새 실행 세그먼트를 시작하도록 요청하는 v2 계약입니다.
 *
 * <p>{@code userId}는 보내지 않습니다. FastAPI가 업무 데이터를 읽고 쓸 때의 신원은
 * 요청 바디가 아니라 내부 도구의 {@code X-Run-Id}를 Spring이 역산해 결정합니다.
 * {@code conversationId}는 FastAPI RunRequest 스키마의 필수 필드라 그대로 실어 보낸다.</p>
 */
@Builder
public record AgentRunRequest(
        String runId,
        Long conversationId,
        String goal,
        List<ContextMessage> messages,
        JsonNode screenContext,
        String requestSource,
        String locale,

        // 이번 턴에 사용자가 붙인 파일. 첨부가 없으면 빈 배열이다(null이 아니다).
        // 이전 턴의 첨부는 다시 보내지 않는다 — messages 와 같은 취급이라면 대화가 길어질수록
        // 같은 파일이 매 턴 프롬프트에 다시 실린다. 필요하면 사용자가 다시 올린다.
        List<AttachedFile> files
) {
    public AgentRunRequest {
        requireText(runId, "runId");
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        requireText(goal, "goal");
        requireText(requestSource, "requestSource");
        requireText(locale, "locale");
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        messages = List.copyOf(messages);
        if (messages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException("messages must not contain null");
        }
        // 첨부는 없는 것이 정상이라 null을 빈 배열로 받아 준다. goal 과 달리 필수가 아니다.
        files = files == null ? List.of() : List.copyOf(files);
        if (files.stream().anyMatch(file -> file == null)) {
            throw new IllegalArgumentException("files must not contain null");
        }
        if (screenContext == null || !screenContext.isObject()
                || !screenContext.has("screen")
                || !screenContext.get("screen").isTextual()
                || screenContext.get("screen").textValue().isBlank()) {
            throw new IllegalArgumentException("screenContext.screen must be a non-blank string");
        }
        screenContext = screenContext.deepCopy();
    }

    @Override
    public JsonNode screenContext() {
        return screenContext.deepCopy();
    }

    public record ContextMessage(String role, String content) {
        public ContextMessage {
            requireText(role, "messages.role");
            requireText(content, "messages.content");
        }
    }

    /**
     * 사용자가 채팅창에 붙인 파일 한 개입니다.
     *
     * <p>BE는 내용을 해석하지 않습니다. 검증(확장자·크기·디코딩 가능 여부)만 하고
     * 원문을 그대로 실어 보내는 게이트입니다. 요약·추출·파싱은 전부 에이전트 쪽 몫입니다.</p>
     *
     * <p>{@code contentType}은 클라이언트가 보낸 값이 아니라 확장자를 보고 서버가 정한 값입니다.
     * {@code encoding}이 {@code TEXT}면 {@code content}가 파일 원문이고,
     * {@code BASE64}면 원본 바이트를 Base64로 감싼 문자열입니다.
     * {@code sizeBytes}는 인코딩 전 원본 크기라 {@code content.length()}와 다를 수 있습니다.</p>
     */
    public record AttachedFile(
            String filename,
            String contentType,
            long sizeBytes,
            AgentFileEncoding encoding,
            String content
    ) {
        public AttachedFile {
            requireText(filename, "files.filename");
            requireText(contentType, "files.contentType");
            if (sizeBytes <= 0) {
                throw new IllegalArgumentException("files.sizeBytes must be positive");
            }
            if (encoding == null) {
                throw new IllegalArgumentException("files.encoding must not be null");
            }
            // 공백뿐인 텍스트 파일도 사용자가 보낸 것이므로 막지 않는다. null만 거른다.
            if (content == null) {
                throw new IllegalArgumentException("files.content must not be null");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
