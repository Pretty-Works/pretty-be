package HK.PrettyWorks_BE.auth.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record LoginRequest(
        @Schema(example = "EMP001", description = "사번")
        @NotBlank(message = "사번을 입력해주세요.")
        String employeeNo,

        @Schema(example = "Test1234!", description = "비밀번호")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
