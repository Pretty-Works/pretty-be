package HK.PrettyWorks_BE.project.finance.dto.req;

import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// 지출 등록·수정 공통 요청 (5필드). projectId(Path)·spenderId(토큰)는 바디에 없으며 와도 무시한다.
public record ExpenseRequest(
        @NotNull(message = "사용일은 필수입니다.")
        LocalDate expenseDate,

        @NotNull(message = "지출 유형은 필수입니다.")
        ExpenseCategory category,

        @NotBlank(message = "사용처는 필수입니다.")
        @Size(max = 100, message = "사용처는 100자 이하여야 합니다.")
        String merchant,

        @NotBlank(message = "사용 목적은 필수입니다.")
        @Size(max = 255, message = "사용 목적은 255자 이하여야 합니다.")
        String purpose,

        @NotNull(message = "금액은 필수입니다.")
        @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
        Long amount
) {
}
