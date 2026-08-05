package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;

// 참여자 한 명의 전체 정보. 자동완성용 ProjectMemberSearchRow와 달리 오너 여부까지 포함하고
// 요청자 본인도 결과에 남긴다 — 권한 판정(오너·PM)과 "나를 제외한 참석자 명단" 구성에 둘 다 필요하다.
public interface ProjectMemberDetailRow {
    Long getUserId();

    String getName();

    DepartmentType getDepartment();

    PositionType getPosition();

    String getRole();

    boolean getIsOwner();
}
