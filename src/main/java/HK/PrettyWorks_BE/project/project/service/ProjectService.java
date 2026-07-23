package HK.PrettyWorks_BE.project.project.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRepository;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectCreateRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectCreateRequest.MemberRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectCreateRequest.MilestoneRequest;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectCreateResponse;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.MilestoneRepository;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MilestoneRepository milestoneRepository;

    @Transactional
    public ProjectCreateResponse create(Long ownerId, ProjectCreateRequest request) {
        // 1) 오너 조회 + 생성 권한 (PROJECT_001)
        //    토큰의 userId로 조회한다. 토큰은 유효한데 유저가 없으면 인증 자체를 신뢰할 수 없으므로 UNAUTHORIZED.
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
        if (!ProjectPolicy.canCreate(owner)) {
            throw BaseException.type(ProjectErrorCode.NO_CREATE_PERMISSION);
        }

        // 2) 기간 검증 (PROJECT_003): 종료일이 시작일보다 빠르면 차단 (같은 날은 허용)
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        if (endDate.isBefore(startDate)) {
            throw BaseException.type(ProjectErrorCode.INVALID_PERIOD);
        }

        // 3) 마일스톤 검증 (없으면 빈 목록)
        List<MilestoneRequest> milestones = request.milestones() == null ? List.of() : request.milestones();
        for (MilestoneRequest m : milestones) {
            // 3-1) 목표일·목표 내용 중 한쪽만 입력하면 차단 (PROJECT_016)
            if (m.targetDate() == null || !StringUtils.hasText(m.goal())) {
                throw BaseException.type(ProjectErrorCode.MILESTONE_INCOMPLETE);
            }
            // 3-2) 목표일이 프로젝트 기간(startDate ~ endDate)을 벗어나면 차단 (PROJECT_015)
            if (m.targetDate().isBefore(startDate) || m.targetDate().isAfter(endDate)) {
                throw BaseException.type(ProjectErrorCode.MILESTONE_OUT_OF_RANGE);
            }
        }

        // 4) 참여자 검증 — 오너는 제외하고(아래서 is_owner=true로 따로 등록), userId 중복은 제거(입력 순서 유지)
        List<MemberRequest> members = request.members() == null ? List.of() : request.members();
        Map<Long, MemberRequest> participants = new LinkedHashMap<>();
        for (MemberRequest m : members) {
            if (m.userId().equals(ownerId)) {
                continue;
            }
            participants.putIfAbsent(m.userId(), m);
        }
        // 4-1) 참여자가 실제로 존재하는지 (PROJECT_002)
        List<UserEntity> foundUsers = userRepository.findAllById(participants.keySet());
        if (foundUsers.size() != participants.size()) {
            throw BaseException.type(ProjectErrorCode.MEMBER_NOT_FOUND);
        }
        // 4-2) 참여자가 재직중(ACTIVE)인지 — 휴직·퇴사는 차단 (USER_001)
        for (UserEntity u : foundUsers) {
            if (u.getStatus() != StatusType.ACTIVE) {
                throw BaseException.type(UserErrorCode.INACTIVE_USER);
            }
        }

        // 5) 저장 (project → members → milestones, 모두 이 트랜잭션 안에서)
        // 5-1) project: 상태는 ONGOING 고정, 예산 미입력(null)은 0(제한 없음)으로 저장
        BigDecimal budget = request.budget() == null ? BigDecimal.ZERO : request.budget();
        ProjectEntity project = ProjectEntity.builder()
                .name(request.name())
                .status(ProjectStatus.ONGOING)
                .startDate(startDate)
                .targetDate(endDate)
                .targetBudget(budget)
                .description(request.description())
                .build();
        projectRepository.save(project);

        // 5-2) members: 오너(is_owner=true, role=ownerRole) + 참여자(is_owner=false)
        List<ProjectMemberEntity> memberEntities = new ArrayList<>();
        memberEntities.add(ProjectMemberEntity.builder()
                .projectId(project.getId())
                .userId(ownerId)
                .isOwner(true)
                .role(request.ownerRole())
                .status(ProjectMemberStatus.ACTIVE)
                .build());
        for (MemberRequest m : participants.values()) {
            memberEntities.add(ProjectMemberEntity.builder()
                    .projectId(project.getId())
                    .userId(m.userId())
                    .isOwner(false)
                    .role(m.role())
                    .status(ProjectMemberStatus.ACTIVE)
                    .build());
        }
        projectMemberRepository.saveAll(memberEntities);

        // 5-3) milestones
        List<MilestoneEntity> milestoneEntities = new ArrayList<>();
        for (MilestoneRequest m : milestones) {
            milestoneEntities.add(MilestoneEntity.builder()
                    .projectId(project.getId())
                    .targetDate(m.targetDate())
                    .goal(m.goal())
                    .build());
        }
        milestoneRepository.saveAll(milestoneEntities);

        // 6) 생성된 프로젝트 id 반환
        return ProjectCreateResponse.builder()
                .projectId(project.getId())
                .build();
    }
}
