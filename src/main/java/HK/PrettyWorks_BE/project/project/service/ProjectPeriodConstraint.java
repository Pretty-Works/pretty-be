package HK.PrettyWorks_BE.project.project.service;

import java.time.LocalDate;

// 프로젝트 기간을 줄일 때 "새 기간을 벗어나 남게 되는 데이터"가 있는지 각 도메인에 묻는 계약입니다.
//
// 계약을 프로젝트 쪽에 두고 각 도메인이 구현하는 이유(의존성 역전):
// 프로젝트는 할 일·지출·회의록이 공통으로 의존하는 코어라, 프로젝트가 그들을 되참조하면 방향이 뒤집힙니다.
// 이렇게 두면 ProjectService는 구현체를 몰라도 되고(Spring이 전부 주입), 새 도메인이 생겨도 구현만 추가하면 됩니다.
public interface ProjectPeriodConstraint {

    // 프로젝트 기간이 [startDate, endDate]로 바뀌면 이 도메인에 기간 밖 데이터가 남는지 여부.
    // 삭제된(소프트 삭제) 데이터는 제외한다.
    boolean hasDataOutsidePeriod(Long projectId, LocalDate startDate, LocalDate endDate);
}
