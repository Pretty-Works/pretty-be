package HK.PrettyWorks_BE.project.finance.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import HK.PrettyWorks_BE.project.finance.constant.ExpenseStatus;
import HK.PrettyWorks_BE.project.finance.domain.ExpenseEntity;
import HK.PrettyWorks_BE.project.finance.dto.req.ExpenseRequest;
import HK.PrettyWorks_BE.project.finance.dto.res.BudgetSummaryResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseListResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseResponse;
import HK.PrettyWorks_BE.project.finance.exception.ExpenseErrorCode;
import HK.PrettyWorks_BE.project.finance.policy.ExpensePolicy;
import HK.PrettyWorks_BE.project.finance.repository.BudgetTotalRow;
import HK.PrettyWorks_BE.project.finance.repository.ExpenseRepository;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

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
        // 1) 프로젝트 존재 (PROJECT_004) — 기간 검증에 필요해 엔티티 로드
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 작성 권한 — 참여중(ACTIVE) 멤버만 (MEMBER_001). 오너 특권 없음, 직급·부서 무관.
        projectMemberService.validateActiveMember(projectId, spenderId);

        // 3) 지출자(=호출자) 재직 검증 (USER_003) — 휴직자도 등록 가능, 퇴사자만 차단
        currentUserService.getEmployedUser(spenderId);

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

        // 4) 호출자 재직 검증 (USER_003)
        currentUserService.getEmployedUser(userId);

        // 5) 사용일이 프로젝트 기간 내인지 (EXPENSE_003)
        // 지출이 이미 조회됐고 expenses.project_id가 FK라 프로젝트는 반드시 존재한다(도달 불가 분기).
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));
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

        // 3) 호출자 재직 검증 (USER_003)
        currentUserService.getEmployedUser(userId);

        // 4) 이미 삭제됐으면 멱등 성공(no-op), 살아있으면 소프트 삭제(dirty checking으로 UPDATE)
        if (expense.getDeletedAt() != null) {
            return;
        }
        expense.softDelete(userId);
    }

    // 지출 목록 조회. 재무 화면 '지출 내역' 테이블을 채운다.
    // 등록·수정·삭제와 달리 사용자 상태 검증을 걸지 않는다 — 참여중 멤버면 읽기는 허용한다.
    @Transactional(readOnly = true)
    public PageResponse<ExpenseListResponse> getExpenses(Long projectId, Long userId, ExpenseStatus status,
                                                         String keyword, Pageable pageable) {
        // 1) 프로젝트 존재(PROJECT_004) + 조회 권한(MEMBER_001).
        //    본인 건뿐 아니라 팀 전체 지출을 본다. 기간·상태 검증은 조회에 불필요해 프로젝트를 로드하지 않는다.
        projectMemberService.validateAccess(projectId, userId);

        // 2) 검색어 정리 — 공백만 들어오면 검색하지 않은 것으로 본다
        String searchKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        // 3) 탭을 사용일 경계로 변환. 오늘을 기준으로 사용 내역(이전)과 예정(이후)이 갈린다.
        LocalDate today = LocalDate.now();
        LocalDate from = (status == ExpenseStatus.PLANNED) ? today.plusDays(1) : null;
        LocalDate to = (status == ExpenseStatus.EXECUTED) ? today : null;

        Page<ExpenseEntity> expenses =
                expenseRepository.findExpenses(projectId, from, to, searchKeyword, withSort(pageable, status));

        // 4) 지출자 이름은 한 번에 조회 (행마다 조회하면 N+1)
        Set<Long> spenderIds = expenses.getContent().stream()
                .map(ExpenseEntity::getSpenderId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = userService.getNameMap(spenderIds);

        return PageResponse.from(expenses.map(expense -> ExpenseListResponse.builder()
                .expenseId(expense.getId())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .merchant(expense.getMerchant())
                .purpose(expense.getPurpose())
                .spender(ExpenseListResponse.Spender.builder()
                        .userId(expense.getSpenderId())
                        .name(nameMap.get(expense.getSpenderId()))
                        .build())
                .amount(expense.getAmount())
                .build()));
    }

    // 예산 현황 조회. 재무 화면 상단 '예산 현황' 카드를 채운다.
    // 목록 조회와 마찬가지로 사용자 상태는 보지 않고 프로젝트 참여 여부만 확인한다.
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getBudget(Long projectId, Long userId) {
        // 1) 프로젝트 존재(PROJECT_004) + 조회 권한(MEMBER_001).
        //    할당 예산이 필요해 여기서는 엔티티를 직접 로드한다.
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));
        projectMemberService.validateActiveMember(projectId, userId);

        // 2) 사용·사용 예정 합계. 지출이 0건이면 SUM 자체가 null이라 여기서 0으로 보정한다.
        LocalDate today = LocalDate.now();
        BudgetTotalRow total = expenseRepository.sumByProject(projectId, today);
        long executed = zeroIfNull(total.executed());
        long planned = zeroIfNull(total.planned());

        // 3) 잔여는 음수가 될 수 있다(예산 초과를 차단하지 않는다). 집행률은 사용 예정을 빼고 '사용'만 본다.
        long totalBudget = project.getTargetBudget();
        long remaining = totalBudget - executed - planned;

        return BudgetSummaryResponse.builder()
                .totalBudget(totalBudget)
                .executed(executed)
                .planned(planned)
                .remaining(remaining)
                .executionRate(rate(executed, totalBudget))
                .byCategory(expenseRepository.sumByCategory(projectId, today).stream()
                        .map(row -> BudgetSummaryResponse.CategoryAmount.builder()
                                .category(ExpenseCategory.valueOf(row.key()))
                                .amount(row.amount())
                                .ratio(rate(row.amount(), executed))
                                .build())
                        .toList())
                .byDepartment(expenseRepository.sumByDepartment(projectId, today).stream()
                        .map(row -> BudgetSummaryResponse.DepartmentAmount.builder()
                                .department(DepartmentType.valueOf(row.key()))
                                .amount(row.amount())
                                .ratio(rate(row.amount(), executed))
                                .build())
                        .toList())
                .build();
    }

    // 비율(%) 내림. 분모가 0이면 나눌 수 없으므로 0으로 가드한다.
    // 집행률에서 분모 0은 '예산 제한 없음'을, 항목 비율에서는 '사용 내역 없음'을 뜻하며 둘 다 0이 자연스럽다.
    private int rate(long part, long whole) {
        return whole == 0 ? 0 : (int) (part * 100 / whole);
    }

    // 집계 대상 행이 0건이면 SUM은 0이 아니라 null을 반환한다.
    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    // 목록 정렬은 서버가 고정한다(화면에 정렬 UI가 없다).
    //   사용 내역: 사용일 내림차순 — 최근에 쓴 것부터
    //   예정:      사용일 오름차순 — 곧 나갈 것부터
    // id 보조 정렬은 필수다. 같은 날짜 지출이 흔한데 보조 정렬이 없으면 DB가 매 요청 순서를 바꿀 수 있어
    // 같은 항목이 두 페이지에 나오거나 아예 빠진다.
    private Pageable withSort(Pageable pageable, ExpenseStatus status) {
        Sort sort = (status == ExpenseStatus.PLANNED)
                ? Sort.by(Sort.Order.asc("expenseDate"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"));

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
