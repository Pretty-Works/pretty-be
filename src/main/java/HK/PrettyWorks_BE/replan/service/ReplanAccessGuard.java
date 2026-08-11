package HK.PrettyWorks_BE.replan.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.replan.exception.ReplanErrorCode;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 재계획 저장·적용이 공통으로 요구하는 관문.
//
// 저장에도 적용과 같은 권한을 요구한다. 적용할 수 없는 사람이 계획만 쌓아 둘 이유가 없고,
// 저장 API가 열려 있으면 남의 프로젝트에 임의의 계획을 심어 둔 뒤 권한 있는 사람이 무심코 승인하기를
// 노릴 수 있다 — 저장은 프로젝트 데이터를 바꾸지 않지만 그렇다고 공격면이 아닌 것은 아니다.
@Component
@RequiredArgsConstructor
public class ReplanAccessGuard {

    private final ProjectMemberService projectMemberService;
    private final CurrentUserService currentUserService;

    /**
     * 프로젝트 존재(PROJECT_004)·참여 여부(MEMBER_001)·관리 권한(REPLAN_003)·호출자 재직(USER_003)을 확인한다.
     *
     * <p>관리 권한 기준은 프로젝트 수정과 같다(ProjectPolicy.canUpdate) — 오너이거나 부서가 PM인 참여자.
     * 재계획은 곧 프로젝트 계획을 다시 짜는 일이라, 기준이 갈리면
     * "계획은 바꿀 수 있는데 그 계획대로는 못 바꾸는" 상태가 생긴다.
     */
    @Transactional(readOnly = true)
    public void validateManager(Long projectId, Long actorId) {
        // 없는 프로젝트를 403으로 답하지 않도록 존재 확인이 먼저다(ProjectMemberService.validateAccess의 순서 규칙).
        projectMemberService.validateAccess(projectId, actorId);

        // 재직 검증 없이 읽는다 — 퇴사자에게 USER_003보다 403이 먼저 나가야 한다.
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, actorId)
                .orElseThrow(() -> BaseException.type(ReplanErrorCode.NO_APPLY_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller, currentUserService.getCurrentUser(actorId))) {
            throw BaseException.type(ReplanErrorCode.NO_APPLY_PERMISSION);
        }

        // 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단. 만료 전 토큰으로 우회하는 길을 막는다.
        currentUserService.getEmployedUser(actorId);
    }
}
