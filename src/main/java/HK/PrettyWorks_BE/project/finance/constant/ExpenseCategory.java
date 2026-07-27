package HK.PrettyWorks_BE.project.finance.constant;

// 지출 유형. @Enumerated(EnumType.STRING)으로 저장하며 ORDINAL 저장은 금지.
public enum ExpenseCategory {
    TRANSPORT("교통비"),
    MEAL("식대"),
    SOFTWARE("소프트웨어"),
    OFFICE_SUPPLY("사무용품"),
    EDUCATION("교육·세미나"),
    LABOR("인건비"),
    OUTSOURCING("외주"),
    INFRA("인프라"),
    ETC("기타");

    private final String description;

    ExpenseCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
