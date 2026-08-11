package HK.PrettyWorks_BE.agent.meetingdraft.application;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 업로드한 회의 기록 txt를 FastAPI에 실어 보낼 문자열로 바꿉니다.
 *
 * <p>채팅 첨부({@code AgentAttachmentIntake})와 규칙을 일부러 맞췄습니다 — 확장자로만 형식을
 * 판정하고, UTF-8로만 읽고, BOM을 뗍니다. 다만 클래스를 따로 두었습니다. 저쪽은 파일을 해석하지
 * 않고 그대로 실어 보내는 통로라 여러 형식(pdf는 Base64)을 다루지만, 이쪽이 필요한 것은
 * "글자로 읽히는 회의록 한 덩어리" 하나뿐이고 상한도 규격이 정한 30,000자로 따로입니다.
 * 저쪽 설정({@code agent.upload.*})에 pdf를 열면 이쪽이 Base64 문자열을 녹취록으로 넘기게 됩니다.</p>
 *
 * <p>인코딩을 추측해 고쳐 읽지 않습니다. CP949 파일을 UTF-8로 우겨 읽으면 깨진 글자가 그대로
 * LLM에 전달돼 엉뚱한 초안이 조용히 나오는데, 그건 에러보다 나쁩니다.</p>
 */
@Slf4j
public final class MeetingTranscriptReader {

    // 규격이 정한 녹취록 상한. FastAPI와 같은 값이어야 한다.
    static final int MAX_TRANSCRIPT_LENGTH = 30_000;

    /**
     * 바이트를 읽기 전에 신고된 크기로 먼저 끊는 상한.
     *
     * <p>UTF-8에서 한 글자는 최대 4바이트이므로, 이 값을 넘는 파일은 정규화 여부와 무관하게
     * {@link #MAX_TRANSCRIPT_LENGTH}자를 넘길 수밖에 없습니다. 즉 "너무 큰 파일"이 아니라
     * "확실히 너무 긴 녹취록"이라 {@code TRANSCRIPT_TOO_LONG}으로 안내합니다.</p>
     *
     * <p>상한을 넘는 파일을 힙에 올리지 않는 것이 목적입니다.</p>
     */
    static final long MAX_TRANSCRIPT_BYTES = (long) MAX_TRANSCRIPT_LENGTH * 4;

    private static final String TXT_EXTENSION = "txt";

    // 윈도우 메모장이 UTF-8로 저장할 때 붙이는 BOM. 남겨 두면 녹취록 첫 글자가 보이지 않는
    // 제어문자가 되고, "이름:" 형태의 첫 발화를 읽는 쪽에서 조용히 어긋난다.
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private MeetingTranscriptReader() {
    }

    /**
     * 업로드 파일에서 녹취록 전문을 뽑아냅니다.
     *
     * @throws BaseException 확장자가 txt가 아니거나(AGENT_029), UTF-8로 읽히지 않거나(AGENT_032),
     *                       내용이 없거나(AGENT_033), 30,000자를 넘는(AGENT_023) 경우
     */
    public static String read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BaseException.type(AgentErrorCode.TRANSCRIPT_EMPTY);
        }
        requireTxt(file.getOriginalFilename());

        if (file.getSize() > MAX_TRANSCRIPT_BYTES) {
            log.warn("[회의록 초안] 파일이 상한보다 큽니다. bytes={} max={}",
                    file.getSize(), MAX_TRANSCRIPT_BYTES);
            throw BaseException.type(AgentErrorCode.TRANSCRIPT_TOO_LONG);
        }

        String transcript = normalize(decode(readBytes(file)));
        if (transcript.isEmpty()) {
            throw BaseException.type(AgentErrorCode.TRANSCRIPT_EMPTY);
        }
        // 여기서 자르지 않고 거절한다. 뒷부분이 잘린 녹취록으로 만든 초안은 "결정 사항이 빠진
        // 회의록"이 되는데, 사용자는 무엇이 빠졌는지 알 방법이 없다.
        if (transcript.length() > MAX_TRANSCRIPT_LENGTH) {
            log.warn("[회의록 초안] 녹취록이 상한보다 깁니다. length={} max={}",
                    transcript.length(), MAX_TRANSCRIPT_LENGTH);
            throw BaseException.type(AgentErrorCode.TRANSCRIPT_TOO_LONG);
        }
        return transcript;
    }

    // Content-Type은 판정에 쓰지 않는다. 브라우저·OS에 따라 정상 .txt가
    // application/octet-stream으로 오기도 하고, 반대로 아무 파일에나 text/plain을 붙일 수도 있다.
    private static void requireTxt(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.trim();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!TXT_EXTENSION.equals(extension)) {
            log.warn("[회의록 초안] txt가 아닌 파일입니다. extension={}", extension);
            throw BaseException.type(AgentErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException failure) {
            log.warn("[회의록 초안] 업로드 파일을 읽지 못했습니다.", failure);
            throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
        }
    }

    // String 생성자는 깨진 바이트를 U+FFFD로 조용히 바꿔 통과시키므로 REPORT 디코더를 쓴다.
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            log.warn("[회의록 초안] UTF-8이 아닌 파일입니다.");
            throw BaseException.type(AgentErrorCode.FILE_NOT_READABLE);
        }
    }

    /**
     * 줄바꿈을 {@code \n}으로 통일하고, 글자가 아닌 제어문자를 걷어냅니다.
     *
     * <p>정규화를 여기서 하는 이유는 길이 판정 때문입니다. 윈도우에서 저장한 파일은 줄마다
     * {@code \r}이 하나씩 더 붙는데, 그걸 세어 30,000자를 넘겼다고 거절하면 사용자는 같은 내용을
     * 리눅스에서 저장하면 통과하는 이유를 알 수 없습니다.</p>
     */
    private static String normalize(String decoded) {
        StringBuilder normalized = new StringBuilder(decoded.length());
        for (int index = 0; index < decoded.length(); index++) {
            char character = decoded.charAt(index);
            if (character == '\r') {
                // \r\n 은 \n 하나로, 홀로 쓰인 \r(구형 맥 개행)도 \n 하나로 접는다.
                if (index + 1 < decoded.length() && decoded.charAt(index + 1) == '\n') {
                    continue;
                }
                normalized.append('\n');
                continue;
            }
            // 개행·탭만 남기고 나머지 제어문자와 BOM은 버린다. 프롬프트에서 눈에 보이지 않으면서
            // 글자 수만 차지하고, 로그에 남으면 읽는 사람이 원인을 짚기 어렵다.
            if (character == BYTE_ORDER_MARK
                    || (Character.isISOControl(character) && character != '\n' && character != '\t')) {
                continue;
            }
            normalized.append(character);
        }
        return normalized.toString().strip();
    }
}
