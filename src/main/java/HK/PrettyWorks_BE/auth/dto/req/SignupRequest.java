package HK.PrettyWorks_BE.auth.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SignupRequest(
        @NotBlank(message = "회사명을 입력해주세요.")
        @Size(max = 100, message = "회사명은 최대 100자까지 입력 가능합니다.")
        String companyName,

        @NotBlank(message = "업종을 입력해주세요.")
        @Size(max = 20, message = "업종은 최대 20자까지 입력 가능합니다.")
        String businessType,

        @NotBlank(message = "사업자번호를 입력해주세요.")
        @Size(max = 20, message = "사업자번호는 최대 20자까지 입력 가능합니다.")
        String businessNumber,

        @NotBlank(message = "대표자명을 입력해주세요.")
        @Size(max = 20, message = "대표자명은 최대 20자까지 입력 가능합니다.")
        String ceoName,

        @NotBlank(message = "계좌번호를 입력해주세요.")
        @Size(max = 20, message = "계좌번호는 최대 20자까지 입력 가능합니다.")
        String account,

        @NotBlank(message = "은행명을 입력해주세요.")
        @Size(max = 20, message = "은행명은 최대 20자까지 입력 가능합니다.")
        String bank,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 최대 100자까지 입력 가능합니다.")
        String email,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(max = 200, message = "주소는 최대 200자까지 입력 가능합니다.")
        String address,

        @NotBlank(message = "전화번호를 입력해주세요.")
        @Size(max = 20, message = "전화번호는 최대 20자까지 입력 가능합니다.")
        String phone,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
        String password

) {
}
