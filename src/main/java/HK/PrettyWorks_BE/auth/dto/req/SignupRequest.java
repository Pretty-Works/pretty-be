package HK.PrettyWorks_BE.auth.dto.req;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.GenderType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record SignupRequest(
        @NotBlank(message = "사번을 입력해주세요.")
        @Size(max = 20, message = "사번은 최대 20자까지 입력 가능합니다.")
        String employeeNo,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
        String password,

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 20, message = "이름은 최대 20자까지 입력 가능합니다.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 최대 100자까지 입력 가능합니다.")
        String email,

        @NotBlank(message = "전화번호를 입력해주세요.")
        @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
        @Size(max = 20, message = "전화번호는 최대 20자까지 입력 가능합니다.")
        String phoneNumber,

        @NotNull(message = "생년월일을 입력해주세요.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,

        @NotNull(message = "성별을 선택해주세요.")
        GenderType gender,

        @NotNull(message = "부서를 선택해주세요.")
        DepartmentType department,

        @NotNull(message = "직책을 선택해주세요.")
        PositionType position,

        @NotNull(message = "입사일을 입력해주세요.")
        LocalDate hireDate

) {
}
