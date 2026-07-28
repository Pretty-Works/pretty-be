package HK.PrettyWorks_BE.project.finance.constant;

// 지출 집행 구분. 재무 화면의 '사용 내역 / 예정' 탭에 대응한다.
//
// DB에 저장하지 않는 파생값이다. 사용일과 오늘을 비교해 조회 시점에 결정하므로
// (expenseDate <= today → EXECUTED, 아니면 PLANNED) 날짜가 지나면 배치 없이도 저절로 넘어간다.
// 컬럼으로 두면 매일 갱신하는 스케줄러가 필요하고, 놓치는 순간 예산 집계가 어긋난다.
public enum ExpenseStatus {
    EXECUTED("사용 내역"),
    PLANNED("사용 예정");

    private final String description;

    ExpenseStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
