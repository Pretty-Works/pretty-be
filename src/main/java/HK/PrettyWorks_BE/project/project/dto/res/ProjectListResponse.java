package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import lombok.Builder;

import java.time.LocalDate;

// 프로젝트 목록 조회 응답 한 건.
// 홈의 '진행 중 프로젝트' 패널과 프로젝트 선택 팝업이 함께 사용하며, 팝업은 projectId·name만 쓴다.
@Builder
public record ProjectListResponse(
        Long projectId,
        String name,
        // 상태 코드로 내려주고 라벨 매핑은 화면단이 한다. (전체 조회 시 상태 구분 표시용)
        ProjectStatus status,
        // 목록의 정렬 근거라 함께 내려준다. 필요하면 화면에서 D-day를 계산할 수 있다.
        LocalDate targetDate,
        // 기간 경과율(%). 작업 진척이 아니라 "시작일~목표일 중 오늘이 어디인지"이며 저장하지 않는 파생값이다.
        int progress
) {
}
