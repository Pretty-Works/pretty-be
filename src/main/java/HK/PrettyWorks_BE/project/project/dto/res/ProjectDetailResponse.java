package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// 프로젝트 상세 조회(수정 화면 진입용) 응답.
// version은 낙관적 락용 — 클라이언트가 보관했다가 수정(PUT) 시 그대로 실어 보낸다.
// progress는 저장하지 않고 조회 시점 날짜로 계산한 파생값이다.
@Builder
public record ProjectDetailResponse(
        Long projectId,
        Long version,
        String name,
        LocalDate startDate,
        // 엔티티의 targetDate. 생성·수정 요청이 endDate라 계약을 맞춘다.
        LocalDate endDate,
        // 원 단위 정수. 0은 '예산 제한 없음'.
        Long budget,
        String description,
        ProjectStatus status,
        int progress,
        Owner owner,
        List<Member> members,
        // 목표일 오름차순. 마일스톤 목록 조회와 같은 모양(MilestoneSummary)을 쓴다.
        List<MilestoneSummary> milestones
) {

    // 오너는 참여자 목록과 분리해 내려준다. ownerRole은 수정 요청의 ownerRole과 형태를 맞춘 값.
    //
    // department·position은 사용자 검색(UserSearchResponse)과 같은 코드 값이다. 수정 화면의 참여자 카드가
    // "이름 · 부서"로 표시하는데, 기존 참여자와 방금 검색해 추가한 참여자의 출처가 달라
    // 여기에 없으면 같은 목록 안에서 표시가 갈린다.
    @Builder
    public record Owner(
            Long userId,
            String name,
            DepartmentType department,
            PositionType position,
            // 재직 상태. 화면이 휴직 뱃지를 붙이는 데 쓴다 (퇴사자는 참여자로 남지 않는다)
            StatusType status,
            String ownerRole
    ) {
    }

    // 참여중(ACTIVE)인 참여자만. 역할 미지정이면 role은 null.
    @Builder
    public record Member(
            Long userId,
            String name,
            DepartmentType department,
            PositionType position,
            StatusType status,
            String role
    ) {
    }

}
