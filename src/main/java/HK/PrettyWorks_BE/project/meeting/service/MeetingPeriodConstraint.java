package HK.PrettyWorks_BE.project.meeting.service;

import HK.PrettyWorks_BE.project.meeting.repository.MeetingRepository;
import HK.PrettyWorks_BE.project.project.service.ProjectPeriodConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 프로젝트 기간 축소 시, 새 기간을 벗어나는 회의 일자의 회의록이 남는지 확인한다. (소프트 삭제된 건은 제외)
@Component
@RequiredArgsConstructor
public class MeetingPeriodConstraint implements ProjectPeriodConstraint {

    private final MeetingRepository meetingRepository;

    @Override
    public boolean hasDataOutsidePeriod(Long projectId, LocalDate startDate, LocalDate endDate) {
        return meetingRepository.existsOutsidePeriod(projectId, startDate, endDate);
    }
}
