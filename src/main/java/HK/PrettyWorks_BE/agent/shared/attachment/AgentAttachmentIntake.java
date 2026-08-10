package HK.PrettyWorks_BE.agent.shared.attachment;

import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 채팅창에 올라온 파일을 받아 FastAPI에 실어 보낼 형태로 바꾸는 관문입니다.
 *
 * <p>이 클래스가 에이전트 첨부의 유일한 검문소입니다. 여기를 통과한 것만 실행이 만들어지고,
 * 여기서 막히면 Run도 메시지도 생기지 않습니다(빈 대화가 남지 않게 하려는 것입니다).</p>
 *
 * <p>BE는 파일을 저장하지도, 열어서 해석하지도 않습니다. 검증하고 그대로 전달할 뿐입니다.
 * 그래서 이 클래스의 판단은 세 가지뿐입니다 — 받아도 되는 형식인가, 크기가 감당되는가,
 * 약속한 인코딩으로 읽히는가.</p>
 */
@Slf4j
@Component
public class AgentAttachmentIntake {

    /**
     * 서버가 아는 확장자 목록입니다. 실제로 무엇을 받을지는 여기가 아니라
     * {@code agent.upload.allowed-extensions} 설정이 정합니다 — 이 표는 "허용했을 때
     * 어떻게 다룰지"만 정의합니다.
     *
     * <p>확장자를 유일한 판정 근거로 삼는 이유: 클라이언트가 보낸 Content-Type은 신뢰할 수
     * 없습니다. 브라우저·OS에 따라 정상 .txt가 application/octet-stream으로 오기도 하고,
     * 반대로 아무 파일에나 text/plain을 붙여 보낼 수도 있습니다. 확장자로 판정하고
     * 에이전트에 알릴 MIME 타입도 서버가 이 표에서 정해 붙입니다.</p>
     *
     * <p>에이전트 채팅에는 txt, pdf, docx, hwp만 첨부할 수 있습니다. 표에 없는 확장자를
     * 설정에 쓰면 기동이 실패하므로, 설정으로 이 제한을 넓힐 수 없습니다.</p>
     */
    private static final Map<String, FileType> KNOWN_TYPES = Map.of(
            "txt", new FileType("text/plain", AgentFileEncoding.TEXT),
            "pdf", new FileType("application/pdf", AgentFileEncoding.BASE64),
            "docx", new FileType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    AgentFileEncoding.BASE64),
            "hwp", new FileType("application/x-hwp", AgentFileEncoding.BASE64));

    // 파일명 상한. agent_message_attachments.filename 컬럼 길이와 같아야 저장 단계에서 잘리지 않는다.
    private static final int MAX_FILENAME_LENGTH = 255;

    // 윈도우 메모장이 UTF-8로 저장할 때 붙이는 BOM. 남겨 두면 LLM 프롬프트 첫 글자가 보이지 않는
    // 제어문자가 되고, 파일 첫 줄을 키로 쓰는 처리에서 조용히 어긋난다. 읽는 쪽이 아니라 여기서 뗀다.
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private final Map<String, FileType> allowedTypes;
    private final int maxFiles;
    private final long maxFileBytes;
    private final long maxTotalBytes;

    // 허용 확장자를 List<String>이 아니라 String[]로 받는 이유: @Value 는 쉼표 목록을 배열로만
    // 쪼갠다(StringArrayPropertyEditor). List로 받으면 "txt,md" 가 통째로 원소 하나가 되어,
    // 확장자를 하나 더 열려는 순간 기동이 죽는다. SecurityConfig 의 cors.allowed-origins 와 같은 방식이다.
    public AgentAttachmentIntake(
            @Value("${agent.upload.allowed-extensions}") String[] allowedExtensions,
            @Value("${agent.upload.max-files}") int maxFiles,
            @Value("${agent.upload.max-file-bytes}") long maxFileBytes,
            @Value("${agent.upload.max-total-bytes}") long maxTotalBytes
    ) {
        if (maxFiles <= 0 || maxFileBytes <= 0 || maxTotalBytes <= 0) {
            throw new IllegalStateException("agent upload settings must be positive");
        }
        if (maxTotalBytes < maxFileBytes) {
            throw new IllegalStateException(
                    "agent.upload.max-total-bytes must be at least max-file-bytes");
        }
        this.allowedTypes = resolveAllowedTypes(allowedExtensions);
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    // 오타 하나로 "왜 txt가 안 올라가지"를 런타임에 디버깅하게 두지 않는다. 기동 때 끊는다.
    private static Map<String, FileType> resolveAllowedTypes(String[] allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.length == 0) {
            throw new IllegalStateException("agent.upload.allowed-extensions must not be empty");
        }
        Map<String, FileType> resolved = new LinkedHashMap<>();
        for (String extension : allowedExtensions) {
            String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
            FileType type = KNOWN_TYPES.get(normalized);
            if (type == null) {
                throw new IllegalStateException("agent.upload.allowed-extensions contains an "
                        + "unknown extension: '" + extension + "'. known=" + KNOWN_TYPES.keySet());
            }
            resolved.put(normalized, type);
        }
        return Map.copyOf(resolved);
    }

    /**
     * 업로드된 파일을 검증하고 전달용 형태로 바꿉니다. 첨부가 없으면 빈 목록을 돌려줍니다.
     *
     * <p>한 건이라도 규칙을 어기면 전부 거절합니다. 일부만 통과시키면 사용자는 자기가 붙인 파일이
     * 조용히 빠진 채 답변을 받게 되고, 그 답변이 왜 이상한지 알 방법이 없습니다.</p>
     */
    public List<AgentRunRequest.AttachedFile> intake(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        // 파일 파트만 있고 내용이 비어 있는 경우(FE가 빈 input을 그대로 붙인 경우)는 첨부 없음으로 본다.
        List<MultipartFile> uploaded = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (uploaded.isEmpty()) {
            return List.of();
        }
        if (uploaded.size() > maxFiles) {
            throw BaseException.type(AgentErrorCode.TOO_MANY_FILES);
        }

        List<AgentRunRequest.AttachedFile> attachments = new ArrayList<>(uploaded.size());
        long totalBytes = 0L;
        for (MultipartFile file : uploaded) {
            String filename = safeFilename(file.getOriginalFilename());
            FileType type = resolveType(filename);

            // 바이트를 읽기 전에 신고된 크기로 먼저 끊는다. 상한을 넘는 파일을 힙에 올리지 않기 위해서다.
            if (file.getSize() > maxFileBytes) {
                throw BaseException.type(AgentErrorCode.FILE_TOO_LARGE);
            }
            totalBytes += file.getSize();
            if (totalBytes > maxTotalBytes) {
                throw BaseException.type(AgentErrorCode.FILE_TOO_LARGE);
            }

            byte[] bytes = read(file, filename);
            if (bytes.length == 0) {
                throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
            }
            attachments.add(new AgentRunRequest.AttachedFile(
                    filename, type.contentType(), bytes.length, type.encoding(),
                    encode(bytes, type.encoding(), filename)));
        }
        return List.copyOf(attachments);
    }

    // 파일명은 사용자가 지은 값이라 그대로 믿지 않는다. 경로 조각("../", "C:\\...")을 떼고
    // 제어문자를 거른다. 지금은 저장하지 않지만, 이 값은 그대로 DB와 LLM 프롬프트에 들어간다.
    private String safeFilename(String originalFilename) {
        if (originalFilename == null) {
            throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
        }
        String name = originalFilename;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        name = name.trim();
        if (name.isEmpty() || name.length() > MAX_FILENAME_LENGTH
                || name.chars().anyMatch(character -> character < 0x20 || character == 0x7F)) {
            log.warn("[에이전트 첨부] 사용할 수 없는 파일명입니다. length={}", originalFilename.length());
            throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
        }
        return name;
    }

    private FileType resolveType(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        FileType type = allowedTypes.get(extension);
        if (type == null) {
            log.warn("[에이전트 첨부] 허용하지 않는 확장자입니다. extension={} allowed={}",
                    extension, allowedTypes.keySet());
            throw BaseException.type(AgentErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return type;
    }

    private byte[] read(MultipartFile file, String filename) {
        try {
            return file.getBytes();
        } catch (IOException failure) {
            log.warn("[에이전트 첨부] 업로드 파일을 읽지 못했습니다. filename={}", filename, failure);
            throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
        }
    }

    // 텍스트 계열은 UTF-8로만 읽는다. 깨진 바이트를 물음표로 바꿔 통과시키지 않는다 —
    // 그러면 사용자는 "파일은 올라갔는데 답변이 이상한" 상황을 만나고 원인을 짚을 수가 없다.
    private String encode(byte[] bytes, AgentFileEncoding encoding, String filename) {
        if (encoding == AgentFileEncoding.BASE64) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.isEmpty() || text.charAt(0) != BYTE_ORDER_MARK ? text : text.substring(1);
        } catch (CharacterCodingException notUtf8) {
            log.warn("[에이전트 첨부] UTF-8이 아닌 텍스트 파일입니다. filename={}", filename);
            throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
        }
    }

    // 확장자 하나에 대한 처리 규칙. 에이전트에 알릴 MIME 타입과 전달 인코딩을 함께 묶어 둔다.
    private record FileType(String contentType, AgentFileEncoding encoding) {
    }
}
