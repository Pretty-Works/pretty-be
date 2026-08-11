package HK.PrettyWorks_BE.agent.meetingdraft.application;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 파일을 녹취록 문자열로 바꾸는 규칙의 테스트.
 *
 * <p>여기서 막지 못한 값은 그대로 LLM 프롬프트가 되므로, "거절해야 할 것을 거절하는가"와
 * "통과시킨 값이 깨끗한가"를 함께 본다.</p>
 */
class MeetingTranscriptReaderTest {

    // 소스에 눈에 보이지 않는 문자를 직접 적지 않는다. 편집기에서 지워져도 아무도 눈치채지 못한다.
    private static final char BOM = 0xFEFF;
    private static final char NUL = 0x00;
    private static final char BELL = 0x07;

    private static MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "text/plain", content);
    }

    private static MockMultipartFile txt(String content) {
        return file("meeting.txt", content.getBytes(StandardCharsets.UTF_8));
    }

    private static AgentErrorCode codeOf(Throwable failure) {
        return (AgentErrorCode) ((BaseException) failure).getCode();
    }

    @Test
    @DisplayName("txt를 읽어 본문을 그대로 돌려준다")
    void readsUtf8Text() {
        String transcript = MeetingTranscriptReader.read(
                txt("김서준: 오늘 스프린트 리뷰 시작하겠습니다.\n이하늘: API 개발은 68% 완료되었습니다."));

        assertThat(transcript)
                .isEqualTo("김서준: 오늘 스프린트 리뷰 시작하겠습니다.\n이하늘: API 개발은 68% 완료되었습니다.");
    }

    @Test
    @DisplayName("확장자가 txt가 아니면 거절한다 — Content-Type이 text/plain이어도 마찬가지")
    void rejectsNonTxtExtension() {
        assertThatThrownBy(() -> MeetingTranscriptReader.read(
                file("meeting.pdf", "회의 내용".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("확장자가 없으면 거절한다")
    void rejectsMissingExtension() {
        assertThatThrownBy(() -> MeetingTranscriptReader.read(
                file("meeting", "회의 내용".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("대문자 확장자(.TXT)도 받는다")
    void acceptsUppercaseExtension() {
        assertThatNoException().isThrownBy(() -> MeetingTranscriptReader.read(
                file("MEETING.TXT", "회의 내용".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("UTF-8이 아닌 파일은 물음표로 바꿔 통과시키지 않고 거절한다")
    void rejectsNonUtf8() {
        // 메모장에서 "ANSI"로 저장한 국문 txt. 인코딩을 추측해 고쳐 읽으면
        // 깨진 글자가 그대로 LLM에 전달돼 엉뚱한 초안이 조용히 나온다.
        Charset cp949 = Charset.forName("x-windows-949");

        assertThatThrownBy(() -> MeetingTranscriptReader.read(
                file("meeting.txt", "김서준: 회의를 시작합니다.".getBytes(cp949))))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.FILE_NOT_READABLE);
    }

    @Test
    @DisplayName("빈 파일은 거절한다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> MeetingTranscriptReader.read(file("meeting.txt", new byte[0])))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.TRANSCRIPT_EMPTY);
    }

    @Test
    @DisplayName("공백과 개행뿐인 파일은 거절한다 — 파일 형식이 아니라 내용의 문제다")
    void rejectsBlankContent() {
        assertThatThrownBy(() -> MeetingTranscriptReader.read(txt("   \r\n\t  \n  ")))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.TRANSCRIPT_EMPTY);
    }

    @Test
    @DisplayName("BOM을 떼고, 줄바꿈은 개행 하나로 통일하고, 앞뒤 공백을 없앤다")
    void normalizesText() {
        String transcript = MeetingTranscriptReader.read(
                txt(BOM + "  김서준: 시작합니다.\r\n이하늘: 네.\r다음 주에 봐요.\n  "));

        assertThat(transcript).isEqualTo("김서준: 시작합니다.\n이하늘: 네.\n다음 주에 봐요.");
    }

    @Test
    @DisplayName("탭은 남기고 그 밖의 제어문자는 버린다")
    void keepsTabsAndDropsOtherControlCharacters() {
        String transcript = MeetingTranscriptReader.read(txt("이름\t발언" + NUL + BELL + " 끝"));

        assertThat(transcript).isEqualTo("이름\t발언 끝");
    }

    @Test
    @DisplayName("30,000자까지는 통과한다")
    void acceptsMaxLength() {
        String transcript = MeetingTranscriptReader.read(
                txt("가".repeat(MeetingTranscriptReader.MAX_TRANSCRIPT_LENGTH)));

        assertThat(transcript).hasSize(MeetingTranscriptReader.MAX_TRANSCRIPT_LENGTH);
    }

    @Test
    @DisplayName("30,000자를 넘으면 자르지 않고 거절한다")
    void rejectsTooLongTranscript() {
        assertThatThrownBy(() -> MeetingTranscriptReader.read(
                txt("가".repeat(MeetingTranscriptReader.MAX_TRANSCRIPT_LENGTH + 1))))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.TRANSCRIPT_TOO_LONG);
    }

    @Test
    @DisplayName("윈도우 줄바꿈의 캐리지리턴은 길이에 세지 않는다")
    void doesNotCountCarriageReturnsTowardTheLimit() {
        // 같은 내용을 윈도우에서 저장했다는 이유만으로 거절당하면 사용자는 원인을 알 수 없다.
        String windowsSaved = "가\r\n".repeat(MeetingTranscriptReader.MAX_TRANSCRIPT_LENGTH / 2);

        String transcript = MeetingTranscriptReader.read(txt(windowsSaved));

        // 마지막 개행은 strip()이 떼므로 상한보다 한 글자 적다.
        assertThat(transcript).hasSize(MeetingTranscriptReader.MAX_TRANSCRIPT_LENGTH - 1);
    }

    @Test
    @DisplayName("바이트 상한을 넘는 파일은 읽지 않고 '너무 긴 녹취록'으로 거절한다")
    void rejectsOversizedFileBeforeReading() {
        byte[] oversized = new byte[(int) MeetingTranscriptReader.MAX_TRANSCRIPT_BYTES + 1];

        assertThatThrownBy(() -> MeetingTranscriptReader.read(file("meeting.txt", oversized)))
                .isInstanceOf(BaseException.class)
                .extracting(MeetingTranscriptReaderTest::codeOf)
                .isEqualTo(AgentErrorCode.TRANSCRIPT_TOO_LONG);
    }
}
