package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
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
        List<Milestone> milestones
) {

    // 오너는 참여자 목록과 분리해 내려준다. ownerRole은 수정 요청의 ownerRole과 형태를 맞춘 값.
    @Builder
    public record Owner(
            Long userId,
            String name,
            String ownerRole
    ) {
    }

    // 참여중(ACTIVE)인 참여자만. 역할 미지정이면 role은 null.
    @Builder
    public record Member(
            Long userId,
            String name,
            String role
    ) {
    }

    // 목표일 오름차순. 수정 시 마일스톤이 전체 교체되므로 milestoneId는 조회 시점 식별자일 뿐 영구 키가 아니다.
    @Builder
    public record Milestone(
            Long milestoneId,
            LocalDate targetDate,
            String goal
    ) {
    }
}
