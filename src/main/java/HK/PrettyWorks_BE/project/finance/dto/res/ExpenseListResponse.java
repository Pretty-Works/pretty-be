package HK.PrettyWorks_BE.project.finance.dto.res;

import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import lombok.Builder;

import java.time.LocalDate;

// 지출 목록 조회 응답 한 건. 재무 화면 '지출 내역' 테이블의 한 행에 대응한다.
//
// 수정 폼에 필요한 5개 필드(사용일·유형·사용처·목적·금액)를 모두 포함한다.
// 행을 클릭해 여는 수정 팝업이 이 값을 그대로 초기값으로 쓰므로 별도 상세 조회 API가 없다.
@Builder
public record ExpenseListResponse(
        Long expenseId,
        LocalDate expenseDate,
        // 유형 코드로 내려주고 라벨 매핑은 화면단이 한다.
        ExpenseCategory category,
        String merchant,
        String purpose,
        Spender spender,
        Long amount
) {
    // 지출자. userId는 화면이 수정·삭제 버튼 노출을 판단하는 데 쓴다(본인 건만 수정 가능).
    @Builder
    public record Spender(
            Long userId,
            String name
    ) {
    }
}
