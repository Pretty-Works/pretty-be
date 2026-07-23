package HK.PrettyWorks_BE.auth.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank(message = "사번을 입력해주세요.")
        String employeeNo,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
