package HK.PrettyWorks_BE.project.finance.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.finance.domain.ExpenseEntity;
import HK.PrettyWorks_BE.project.finance.dto.req.ExpenseRequest;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseResponse;
import HK.PrettyWorks_BE.project.finance.exception.ExpenseErrorCode;
import HK.PrettyWorks_BE.project.finance.policy.ExpensePolicy;
import HK.PrettyWorks_BE.project.finance.repository.ExpenseRepository;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseResponse create(Long projectId, Long spenderId, ExpenseRequest request) {
        // 1) 프로젝트 존재 (EXPENSE_001) — 기간 검증에 필요해 엔티티 로드
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ExpenseErrorCode.PROJECT_NOT_FOUND));

        // 2) 작성 권한 — 참여중(ACTIVE) 멤버만 (EXPENSE_002). 오너 특권 없음, 직급·부서 무관.
        if (!projectMemberService.isActiveMember(projectId, spenderId)) {
            throw BaseException.type(ExpenseErrorCode.NO_EXPENSE_PERMISSION);
        }

        // 3) 지출자(=호출자) 활성 검증 (USER_001). 토큰은 유효한데 유저가 없으면 인증을 신뢰 못 해 UNAUTHORIZED.
        //    TODO(consolidate): "현재 유저 로드 + 활성 검증"은 공용 CurrentUserService로 이동 예정 (docs/code-review.md 0-3)
        UserEntity spender = userRepository.findById(spenderId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
        if (spender.getStatus() != StatusType.ACTIVE) {
            throw BaseException.type(UserErrorCode.INACTIVE_USER);
        }

        // 4) 사용일이 프로젝트 기간 내인지 (EXPENSE_003). 미래 날짜도 기간 내면 허용(조회 시 PLANNED로 파생).
        if (!ProjectPolicy.isWithinPeriod(project, request.expenseDate())) {
            throw BaseException.type(ExpenseErrorCode.EXPENSE_DATE_OUT_OF_RANGE);
        }

        // 5) 저장 — spenderId는 토큰값(대리 등록 없음). 프로젝트 상태(완료/보관)는 검증하지 않는다(종료돼도 지출 기록 허용).
        ExpenseEntity expense = ExpenseEntity.builder()
                .projectId(projectId)
                .spenderId(spenderId)
                .expenseDate(request.expenseDate())
                .category(request.category())
                .merchant(request.merchant())
                .purpose(request.purpose())
                .amount(request.amount())
                .build();
        expenseRepository.save(expense);

        return new ExpenseResponse(expense.getId());
    }

    @Transactional
    public ExpenseResponse update(Long projectId, Long expenseId, Long userId, ExpenseRequest request) {
        // 1) 지출 존재 + 프로젝트 소속 (EXPENSE_004) — 삭제된 건도 포함 조회(006 구분 위해)
        ExpenseEntity expense = expenseRepository.findByIdAndProjectId(expenseId, projectId)
                .orElseThrow(() -> BaseException.type(ExpenseErrorCode.EXPENSE_NOT_FOUND));

        // 2) 본인이 등록한 지출만 (EXPENSE_005) — 프로젝트 오너라도 타인 건 불가
        if (!ExpensePolicy.canModify(expense, userId)) {
            throw BaseException.type(ExpenseErrorCode.NO_EXPENSE_EDIT_PERMISSION);
        }

        // 3) 이미 삭제된 지출은 수정 불가 (EXPENSE_006)
        if (expense.getDeletedAt() != null) {
            throw BaseException.type(ExpenseErrorCode.ALREADY_DELETED_EXPENSE);
        }

        // 4) 호출자 활성 검증 (USER_001) — 등록과 동일 기준.
        //    TODO(consolidate): "현재 유저 로드 + 활성 검증"은 공용 CurrentUserService로 이동 예정 (docs/code-review.md 0-3)
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
        if (user.getStatus() != StatusType.ACTIVE) {
            throw BaseException.type(UserErrorCode.INACTIVE_USER);
        }

        // 5) 사용일이 프로젝트 기간 내인지 (EXPENSE_003)
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ExpenseErrorCode.EXPENSE_NOT_FOUND));
        if (!ProjectPolicy.isWithinPeriod(project, request.expenseDate())) {
            throw BaseException.type(ExpenseErrorCode.EXPENSE_DATE_OUT_OF_RANGE);
        }

        // 6) 갱신 (dirty checking) — projectId/spenderId 불변(바디에 와도 무시)
        expense.update(request.expenseDate(), request.category(),
                request.merchant(), request.purpose(), request.amount());

        return new ExpenseResponse(expense.getId());
    }

    @Transactional
    public void delete(Long projectId, Long expenseId, Long userId) {
        // 1) 지출 존재 + 프로젝트 소속 (EXPENSE_004) — 삭제된 건도 포함 조회(멱등 판단 위해)
        ExpenseEntity expense = expenseRepository.findByIdAndProjectId(expenseId, projectId)
                .orElseThrow(() -> BaseException.type(ExpenseErrorCode.EXPENSE_NOT_FOUND));

        // 2) 본인이 등록한 지출만 (EXPENSE_005) — 프로젝트 오너라도 타인 건 불가
        if (!ExpensePolicy.canModify(expense, userId)) {
            throw BaseException.type(ExpenseErrorCode.NO_EXPENSE_EDIT_PERMISSION);
        }

        // 3) 호출자 활성 검증 (USER_001) — 수정과 동일 기준.
        //    TODO(consolidate): "현재 유저 로드 + 활성 검증"은 공용 CurrentUserService로 이동 예정 (docs/code-review.md 0-3)
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
        if (user.getStatus() != StatusType.ACTIVE) {
            throw BaseException.type(UserErrorCode.INACTIVE_USER);
        }

        // 4) 이미 삭제됐으면 멱등 성공(no-op), 살아있으면 소프트 삭제(dirty checking으로 UPDATE)
        if (expense.getDeletedAt() != null) {
            return;
        }
        expense.softDelete(userId);
    }
}
