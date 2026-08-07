package HK.PrettyWorks_BE.project.project.dto.req;

import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
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

    /**
     * 상세 조회 결과를 그대로 수정 요청으로 옮긴 빌더를 돌려줍니다. 호출자는 바꿀 필드만 덮어쓰면 됩니다.
     *
     * <p>수정 API가 전체 교체이기 때문에 필요합니다 — 목표일 하나 미루려 해도 참여자·마일스톤까지
     * 되보내야 하고, 빠뜨린 목록은 "없애라"로 해석되어 지워집니다.
     * 화면은 폼에 전체가 채워져 있어 문제가 없지만, 일부만 고치는 경로(재계획 적용)는 여기를 씁니다.
     *
     * <p><b>⚠️ 이 record에 필드를 추가하면 여기도 반드시 함께 채워야 합니다.</b>
     * 빠뜨리면 부분 수정 경로에서 그 값이 매번 조용히 비워집니다.
     *
     * <p>참여자는 참여중인 사람만, 마일스톤은 전부 담깁니다. 마일스톤은 id를 함께 실어야 완료 상태가 보존됩니다.
     */
    public static ProjectRequestBuilder from(ProjectDetailResponse detail) {
        return ProjectRequest.builder()
                .name(detail.name())
                .startDate(detail.startDate())
                .endDate(detail.endDate())
                .budget(detail.budget())
                .description(detail.description())
                .ownerRole(detail.owner().ownerRole())
                .members(detail.members().stream()
                        .map(member -> MemberRequest.builder()
                                .userId(member.userId())
                                .role(member.role())
                                .build())
                        .toList())
                .milestones(detail.milestones().stream()
                        .map(milestone -> MilestoneRequest.builder()
                                .milestoneId(milestone.milestoneId())
                                .targetDate(milestone.targetDate())
                                .goal(milestone.goal())
                                .build())
                        .toList());
    }

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
            // 기존 마일스톤이면 상세 조회에서 받은 값을 그대로 넣고, 신규면 비웁니다.
            // id를 실어 보내야 목표일·내용을 고쳐도 완료 상태가 보존됩니다. 생성 API에서는 무시됩니다.
            Long milestoneId,

            // targetDate·goal 둘 다 있어야 함(한쪽만 입력 차단). 판정은 ProjectPolicy가 갖고 PROJECT_016으로 거부합니다.
            LocalDate targetDate,

            @Size(max = 200, message = "마일스톤 목표 내용은 최대 200자까지 입력 가능합니다.")
            String goal
    ) {
    }
}
