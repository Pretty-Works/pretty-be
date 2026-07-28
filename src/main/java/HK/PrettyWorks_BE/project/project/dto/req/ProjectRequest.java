package HK.PrettyWorks_BE.project.project.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// 프로젝트 생성/수정 공용 요청 DTO (두 API의 body 구조가 동일).
@Builder
public record ProjectRequest(
        @NotBlank(message = "프로젝트명을 입력해주세요.")
        @Size(max = 100, message = "프로젝트명은 최대 100자까지 입력 가능합니다.")
        String name,

        @NotNull(message = "시작일을 입력해주세요.")
        LocalDate startDate,

        @NotNull(message = "종료일을 입력해주세요.")
        LocalDate endDate,

        // 예산은 선택 입력. 미입력(null/빈칸)이거나 0이면 '예산 제한 없음'을 의미하며, 서버에서 null은 0으로 저장합니다.
        // 원 단위 정수 — 소수점이 붙어 오면 Jackson이 400으로 거른다.
        @PositiveOrZero(message = "예산은 0 이상이어야 합니다.")
        Long budget,

        @Size(max = 500, message = "설명은 최대 500자까지 입력 가능합니다.")
        String description,

        // 오너의 직무 역할. 미지정(null) 가능.
        @Size(max = 20, message = "역할은 최대 20자까지 입력 가능합니다.")
        String ownerRole,

        // 크기 상한은 대량 요청으로 조회·저장이 폭주하는 것을 막기 위한 방어값입니다.
        @Valid
        @Size(max = 100, message = "참여자는 최대 100명까지 등록할 수 있습니다.")
        List<MemberRequest> members,

        @Valid
        @Size(max = 50, message = "마일스톤은 최대 50개까지 등록할 수 있습니다.")
        List<MilestoneRequest> milestones
) {

    @Builder
    public record MemberRequest(
            @NotNull(message = "참여자 userId를 입력해주세요.")
            Long userId,

            // 참여자의 직무 역할. 미지정(null) 가능.
            @Size(max = 20, message = "역할은 최대 20자까지 입력 가능합니다.")
            String role
    ) {
    }

    @Builder
    public record MilestoneRequest(
            // targetDate·goal 둘 다 있어야 함(한쪽만 입력 차단). 이 검증은 서비스에서 PROJECT_016으로 처리합니다.
            LocalDate targetDate,

            @Size(max = 200, message = "마일스톤 목표 내용은 최대 200자까지 입력 가능합니다.")
            String goal
    ) {
    }
}
