package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;

// 프로젝트 참여자 한 명의 조회 결과.
//
// 명단 화면과 참여자 추가 자동완성이 같은 projection을 쓴다. 두 조회는 필터(본인 제외·검색어)만
// 다를 뿐 필요한 컬럼이 같아서, 따로 두면 한쪽에만 필드가 추가되어 명단과 후보가 갈린다.
public interface ProjectMemberRow {
    Long getUserId();

    String getName();

    DepartmentType getDepartment();

    PositionType getPosition();

    // 재직 상태. 자동완성에서 휴직자를 표시할 때 쓴다(퇴사자는 조회 자체에서 빠진다).
    StatusType getStatus();

    // 프로젝트 내 직무 역할(자유 문자열). 회사 직급인 position과 다르다.
    String getRole();

    boolean getIsOwner();
}
