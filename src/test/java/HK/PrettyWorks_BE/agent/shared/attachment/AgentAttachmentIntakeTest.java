package HK.PrettyWorks_BE.agent.shared.attachment;

import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAttachmentIntakeTest {

    // 운영 허용 형식과 같은 구성(3개, 개당 1KB·합계 2KB로 축소).
    private final AgentAttachmentIntake intake =
            new AgentAttachmentIntake(new String[]{"txt", "pdf", "docx", "hwp"},
                    3, 1024, 2048);

    @Test
    void returnsEmptyWhenNothingWasAttached() {
        assertThat(intake.intake(null)).isEmpty();
        assertThat(intake.intake(List.of())).isEmpty();
    }

    // FE가 빈 file input을 그대로 붙여 보내는 경우. 첨부 없는 전송과 같게 다뤄야 한다.
    @Test
    void ignoresEmptyParts() {
        MultipartFile empty = new MockMultipartFile("files", "", "text/plain", new byte[0]);

        assertThat(intake.intake(List.of(empty))).isEmpty();
    }

    @Test
    void passesTextFileThroughUnchanged() {
        List<AgentRunRequest.AttachedFile> files =
                intake.intake(List.of(file("회의록.txt", "첫 줄\n둘째 줄")));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.filename()).isEqualTo("회의록.txt");
            assertThat(file.contentType()).isEqualTo("text/plain");
            assertThat(file.encoding()).isEqualTo(AgentFileEncoding.TEXT);
            assertThat(file.content()).isEqualTo("첫 줄\n둘째 줄");
            assertThat(file.sizeBytes())
                    .isEqualTo("첫 줄\n둘째 줄".getBytes(StandardCharsets.UTF_8).length);
        });
    }

    @Test
    void passesAllowedBinaryDocumentFormatsAsBase64() {
        byte[] content = new byte[]{0x01, (byte) 0xFF, 0x20};
        Map<String, String> contentTypes = Map.of(
                "pdf", "application/pdf",
                "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "hwp", "application/x-hwp");

        contentTypes.forEach((extension, contentType) -> {
            MultipartFile document = new MockMultipartFile(
                    "files", "문서." + extension, "application/octet-stream", content);

            assertThat(intake.intake(List.of(document))).singleElement().satisfies(attached -> {
                assertThat(attached.contentType()).isEqualTo(contentType);
                assertThat(attached.encoding()).isEqualTo(AgentFileEncoding.BASE64);
                assertThat(attached.content()).isEqualTo(Base64.getEncoder().encodeToString(content));
            });
        });
    }

    // 메모장이 UTF-8로 저장할 때 붙이는 BOM. 남기면 프롬프트 첫 글자가 보이지 않는 제어문자가 된다.
    @Test
    void stripsByteOrderMark() {
        List<AgentRunRequest.AttachedFile> files =
                intake.intake(List.of(file("메모.txt", "﻿본문")));

        assertThat(files.getFirst().content()).isEqualTo("본문");
    }

    // 확장자가 유일한 판정 근거다. Content-Type이 text/plain이어도 통과시키지 않는다.
    @Test
    void rejectsDisallowedExtensionEvenWhenContentTypeLooksFine() {
        MultipartFile disguised = new MockMultipartFile("files", "악성.exe", "text/plain",
                "내용".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> intake.intake(List.of(disguised)))
                .isInstanceOfSatisfying(BaseException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AgentErrorCode.FILE_TYPE_NOT_ALLOWED));
    }

    // 반대로, 브라우저가 엉뚱한 Content-Type을 붙여도 확장자가 맞으면 받는다.
    @Test
    void acceptsAllowedExtensionRegardlessOfDeclaredContentType() {
        MultipartFile file = new MockMultipartFile("files", "메모.TXT",
                "application/octet-stream", "내용".getBytes(StandardCharsets.UTF_8));

        assertThat(intake.intake(List.of(file))).singleElement()
                .satisfies(attached -> assertThat(attached.contentType()).isEqualTo("text/plain"));
    }

    // CP949로 저장된 한글 txt. 추측해서 고쳐 읽지 않고 사용자에게 되돌려 준다 —
    // 깨진 채 통과하면 답변만 이상해지고 원인을 짚을 수가 없다.
    @Test
    void rejectsTextFileThatIsNotUtf8() {
        MultipartFile cp949 = new MockMultipartFile("files", "메모.txt", "text/plain",
                "한글 내용".getBytes(Charset.forName("x-windows-949")));

        assertThatThrownBy(() -> intake.intake(List.of(cp949)))
                .isInstanceOfSatisfying(BaseException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AgentErrorCode.FILE_NOT_READABLE));
    }

    // 파일명에 섞인 경로 조각은 떼고 마지막 이름만 남긴다.
    @Test
    void stripsPathFromFilename() {
        MultipartFile withPath = new MockMultipartFile("files", "../../etc/메모.txt",
                "text/plain", "내용".getBytes(StandardCharsets.UTF_8));

        assertThat(intake.intake(List.of(withPath)).getFirst().filename()).isEqualTo("메모.txt");
    }

    @Test
    void rejectsFileOverPerFileLimit() {
        MultipartFile big = new MockMultipartFile("files", "메모.txt", "text/plain",
                "a".repeat(1025).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> intake.intake(List.of(big)))
                .isInstanceOfSatisfying(BaseException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AgentErrorCode.FILE_TOO_LARGE));
    }

    // 개당 상한은 넘지 않지만 합계가 넘는 경우.
    @Test
    void rejectsWhenTotalSizeExceedsLimit() {
        MultipartFile first = file("첫째.txt", "a".repeat(1000));
        MultipartFile second = file("둘째.txt", "b".repeat(1000));
        MultipartFile third = file("셋째.txt", "c".repeat(1000));

        assertThatThrownBy(() -> intake.intake(List.of(first, second, third)))
                .isInstanceOfSatisfying(BaseException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AgentErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void rejectsTooManyFiles() {
        List<MultipartFile> files = List.of(
                file("1.txt", "가"), file("2.txt", "나"),
                file("3.txt", "다"), file("4.txt", "라"));

        assertThatThrownBy(() -> intake.intake(files))
                .isInstanceOfSatisfying(BaseException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AgentErrorCode.TOO_MANY_FILES));
    }

    /**
     * 설정에서 허용 형식을 늘리는 길이 실제로 열려 있는지 확인합니다.
     *
     * <p>"txt 외의 형식도 열 수 있다"가 이 기능의 전제라, 그 스위치가 실제로 동작하는지는
     * 생성자를 직접 부르는 단위 테스트가 아니라 실제 프로퍼티 바인딩으로 확인해야 합니다.
     * 쉼표 목록이 쪼개지지 않으면 "txt,md" 가 통째로 확장자 하나가 되어, 테스트가 아니라
     * 애플리케이션 기동이 죽습니다({@code List<String>} 으로 받으면 실제로 그렇게 됩니다).</p>
     */
    @Test
    void bindsCommaSeparatedExtensionsFromConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withConfiguration(UserConfigurations.of(AgentAttachmentIntake.class))
                .withPropertyValues(
                        "agent.upload.allowed-extensions=txt,pdf,docx,hwp",
                        "agent.upload.max-files=3",
                        "agent.upload.max-file-bytes=1024",
                        "agent.upload.max-total-bytes=2048")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentAttachmentIntake.class);
                    AgentAttachmentIntake configured = context.getBean(AgentAttachmentIntake.class);
                    MultipartFile hwp = new MockMultipartFile(
                            "files", "문서.hwp", "application/octet-stream", new byte[]{0x01});
                    assertThat(configured.intake(List.of(hwp)))
                            .singleElement()
                            .satisfies(attached ->
                                    assertThat(attached.contentType()).isEqualTo("application/x-hwp"));
                });
    }

    // 설정 오타로 "왜 안 올라가지"를 런타임에 디버깅하게 두지 않는다.
    @Test
    void failsFastWhenConfiguredExtensionIsUnknown() {
        assertThatThrownBy(() -> new AgentAttachmentIntake(new String[]{"txt", "md"}, 3, 1024, 2048))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("md");
    }

    private MultipartFile file(String filename, String content) {
        return new MockMultipartFile("files", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
