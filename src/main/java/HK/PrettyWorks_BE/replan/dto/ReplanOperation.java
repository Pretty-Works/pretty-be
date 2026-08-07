package HK.PrettyWorks_BE.replan.dto;

import HK.PrettyWorks_BE.replan.constant.ReplanOperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 변경 한 건. 저장 시에는 요청 본문의 항목이고, 적용 시에는 DB에 보관된 JSON을 되읽은 값이다.
 *
 * <p>종류마다 쓰는 필드가 다른데도 하나의 평면 레코드로 둔 이유는, operation이 7종뿐이고 taskId·from·to처럼
 * 겹치는 필드가 많아서다. 종류별 타입으로 쪼개면 다형 역직렬화 설정이 붙고, 에이전트가 만든 JSON이
 * 조금만 어긋나도 원인을 알기 어려운 파싱 오류가 난다. 어느 필드가 필수인지는
 * {@code ReplanApplyService}가 종류별로 검증하고 {@code REPLAN_005}로 알린다.
 *
 * <p>{@code from} 계열은 "계획을 세울 당시의 값"이다. 적용 직전에 현재 값과 대조해, 그 사이 누가 먼저
 * 바꿨다면 덮어쓰지 않고 {@code REPLAN_004}로 멈춘다. 할 일·마일스톤에는 낙관적 락(@Version)이 없어
 * 이 대조가 유일한 방어다.
 */
@Builder
public record ReplanOperation(

        @NotNull(message = "변경 종류를 입력해 주세요.")
        @Schema(description = "변경 종류", example = "TASK_DUE_DATE_CHANGE")
        ReplanOperationType operation,

        @Schema(description = "대상 할 일 ID (TASK_DUE_DATE_CHANGE · TASK_DELETE)", example = "101")
        Long taskId,

        @Schema(description = "대상 마일스톤 ID (MILESTONE_TARGET_DATE_CHANGE)", example = "3")
        Long milestoneId,

        @Schema(description = "추가할 참여자 ID (PROJECT_MEMBER_ADD)", example = "15")
        Long memberId,

        @Schema(description = "참여자의 프로젝트 내 역할 (PROJECT_MEMBER_ADD, 선택)", example = "개발")
        String role,

        @Schema(description = "계획 당시의 날짜. 현재 값과 다르면 적용을 거부한다(날짜 계열)", example = "2026-08-20")
        LocalDate from,

        @Schema(description = "바꿀 날짜. TASK_CREATE에서는 새 할 일의 마감일", example = "2026-08-23")
        LocalDate to,

        @Schema(description = "새 할 일의 담당자 (TASK_CREATE, 선택). 비우면 적용한 사람이 담당한다", example = "15")
        Long toAssigneeId,

        @Schema(description = "새 할 일의 내용 (TASK_CREATE)", example = "API 명세 검토")
        String content,

        @Schema(description = "계획 당시의 할 일 내용. 현재 값과 다르면 적용을 거부한다 (TASK_DELETE)",
                example = "구버전 마이그레이션 스크립트 작성")
        String expectedContent
) {
}
