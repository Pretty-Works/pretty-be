package HK.PrettyWorks_BE.user.constant;

public enum DepartmentType {
    MANAGEMENT_SUPPORT("경영지원"),
    HR("인사"),
    FINANCE("재무회계"),
    SALES("영업"),
    PLANNING("사업기획"),
    CONSULTING("컨설팅"),
    PM("프로젝트관리"),
    FRONTEND("프론트엔드개발"),
    BACKEND("백엔드개발"),
    DEVOPS("데브옵스"),
    DATA("데이터관리"),
    INFRA("인프라운영"),
    SECURITY("정보보안"),
    QA("품질보증");

    private String description;
    DepartmentType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
