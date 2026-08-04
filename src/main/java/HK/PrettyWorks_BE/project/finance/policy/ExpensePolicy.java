package HK.PrettyWorks_BE.project.finance.policy;

import HK.PrettyWorks_BE.project.finance.domain.ExpenseEntity;

// 지출 관련 권한 규칙. 등록은 멤버십으로 판정하지만, 수정·삭제는 "본인(소유자)"으로 판정한다.
public final class ExpensePolicy {

    private ExpensePolicy() {
    }

    // 수정·삭제 공통: 본인이 등록한 지출만 가능(spender_id == 호출자). 프로젝트 오너라도 타인 건은 불가.
    public static boolean canModify(ExpenseEntity expense, Long userId) {
        return expense.getSpenderId().equals(userId);
    }
}
