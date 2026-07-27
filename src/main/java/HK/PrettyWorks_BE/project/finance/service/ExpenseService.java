package HK.PrettyWorks_BE.project.finance.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
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
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserService currentUserService;

    // 지출 등록. 멱등 키가 있으면 중복 요청을 방어한다(같은 키·같은 요청은 첫 응답 재생, 다른 내용은 409).
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않는다.
    public ExpenseResponse create(Long projectId, Long spenderId, String idempotencyKey, ExpenseRequest request) {
        Supplier<Long> creator = () -> doInsert(projectId, spenderId, request);

        // 도메인 조각만 준비: 무엇을 저장할지(creator) + 무엇으로 중복 판정할지(fingerprint).
        // 키 유무 분기·트랜잭션·해싱·409는 IdempotencyService.run이 담당.
        String endpoint = "POST /api/v1/projects/{projectId}/expenses";
        String fingerprint = idempotencyService.fingerprint(
                "POST", "/api/v1/projects/" + projectId + "/expenses", request);

        return new ExpenseResponse(
                idempotencyService.run(idempotencyKey, endpoint, spenderId, fingerprint, creator));
    }

    // 검증(존재·멤버·활성·기간) + 저장 후 생성된 id 반환. 트랜잭션은 IdempotencyService의 TransactionTemplate이 제공.
    // (자체 @Transactional을 붙이지 않는다 — self-invocation 프록시 함정 회피)
    private Long doInsert(Long projectId, Long spenderId, ExpenseRequest request) {
        // 1) 프로젝트 존재 (EXPENSE_001) — 기간 검증에 필요해 엔티티 로드
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ExpenseErrorCode.PROJECT_NOT_FOUND));

        // 2) 작성 권한 — 참여중(ACTIVE) 멤버만 (MEMBER_001). 오너 특권 없음, 직급·부서 무관.
        projectMemberService.validateActiveMember(projectId, spenderId);

        // 3) 지출자(=호출자) 활성 검증 (USER_001)
        currentUserService.getActiveUser(spenderId);

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

        return expense.getId();
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

        // 4) 호출자 활성 검증 (USER_001)
        currentUserService.getActiveUser(userId);

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

        // 3) 호출자 활성 검증 (USER_001)
        currentUserService.getActiveUser(userId);

        // 4) 이미 삭제됐으면 멱등 성공(no-op), 살아있으면 소프트 삭제(dirty checking으로 UPDATE)
        if (expense.getDeletedAt() != null) {
            return;
        }
        expense.softDelete(userId);
    }
}
