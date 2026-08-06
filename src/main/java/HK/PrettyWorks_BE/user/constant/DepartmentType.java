package HK.PrettyWorks_BE.user.constant;

public enum DepartmentType {
    MANAGEMENT_SUPPORT("경영지원"),
    HR("인사"),
    FINANCE("재무"),
    SALES("영업"),
    PLANNING("사업기획"),
    CONSULTING("컨설팅"),
    PM("PM"),
    FRONTEND("FE"),
    BACKEND("BE"),
    DEVOPS("DevOps"),
    DATA("Data"),
    INFRA("Infra"),
    SECURITY("Security"),
    QA("품질보증");

    private String description;
    DepartmentType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
