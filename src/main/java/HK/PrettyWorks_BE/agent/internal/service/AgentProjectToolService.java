package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.agent.internal.dto.req.AgentExpenseCreateRequest;
import HK.PrettyWorks_BE.agent.internal.dto.req.AgentMilestoneStatusRequest;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentBudgetSummaryResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentExpenseListResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentMemberListResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentMilestoneListResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentProjectSearchResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentWriteResults;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import HK.PrettyWorks_BE.project.finance.dto.res.BudgetSummaryResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseListResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseResponse;
import HK.PrettyWorks_BE.project.finance.service.ExpenseService;
import HK.PrettyWorks_BE.project.member.dto.res.ProjectMemberDetailResponse;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneListResponse;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneStatusResponse;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneSummary;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectSearchResult;
import HK.PrettyWorks_BE.project.project.service.MilestoneService;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

// project.search · project.members · milestone.list · budget.summary · expense.list.
//
// 전부 기존 도메인 서비스를 그대로 부른다. 검증·권한 판정은 그쪽에 있고,
// 여기서는 상한을 걸고 응답을 에이전트가 읽기 좋은 최소 필드로 좁힌다.
@Service
@RequiredArgsConstructor
public class AgentProjectToolService {

    private static final String SORT_AMOUNT_DESC = "AMOUNT_DESC";

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final MilestoneService milestoneService;
    private final ExpenseService expenseService;

    public AgentProjectSearchResponse search(Long userId, String status,
                                             String keyword, int size) {
        int validatedSize = AgentPage.validateSize(size);
        Page<ProjectSearchResult> page = projectService.searchMyProjects(
                userId, status, keyword, PageRequest.of(0, validatedSize));

        List<AgentProjectSearchResponse.AgentProject> projects = page.getContent().stream()
                .map(project -> AgentProjectSearchResponse.AgentProject.builder()
                        .projectId(project.projectId())
                        .name(project.name())
                        .status(project.status().name())
                        .startDate(project.startDate())
                        .targetDate(project.targetDate())
                        .myRole(project.myRole())
                        .isOwner(project.isOwner())
                        .targetBudget(project.targetBudget())
                        .isOpenForContent(project.isOpenForContent())
                        .build())
                .toList();

        return AgentProjectSearchResponse.builder()
                .projects(projects)
                .totalCount(projects.size())
                .truncated(page.getTotalElements() > projects.size())
                .build();
    }

    public AgentMemberListResponse members(Long userId, Long projectId, String name, int size) {
        int validatedSize = AgentPage.validateSize(size);

        // 상한보다 하나 더 받아 본다. 딱 상한만큼 받으면 "마침 그만큼"인지 "잘렸는지" 구분할 수 없다.
        List<ProjectMemberDetailResponse> rows =
                projectMemberService.getActiveMembers(projectId, userId, name, validatedSize + 1);
        boolean truncated = rows.size() > validatedSize;

        List<AgentMemberListResponse.AgentMember> members = rows.stream()
                .limit(validatedSize)
                .map(row -> AgentMemberListResponse.AgentMember.builder()
                        .userId(row.userId())
                        .name(row.name())
                        .department(row.department().getDescription())
                        .position(row.position().getDescription())
                        .role(row.role())
                        .isOwner(row.isOwner())
                        .isMe(userId.equals(row.userId()))
                        .build())
                .toList();

        return AgentMemberListResponse.builder()
                .projectId(projectId)
                .members(members)
                .totalCount(members.size())
                .truncated(truncated)
                .build();
    }

    public AgentMilestoneListResponse milestones(Long userId, Long projectId) {
        MilestoneListResponse source = milestoneService.getMilestones(projectId, userId);

        LocalDate today = LocalDate.now();
        Long nextId = source.nextMilestone() == null ? null : source.nextMilestone().milestoneId();

        List<AgentMilestoneListResponse.AgentMilestone> milestones = source.milestones().stream()
                .map(milestone -> AgentMilestoneListResponse.AgentMilestone.builder()
                        .milestoneId(milestone.milestoneId())
                        .goal(milestone.goal())
                        .targetDate(milestone.targetDate())
                        .completed(milestone.done())
                        .isOverdue(isOverdue(milestone, today))
                        .isNext(milestone.milestoneId().equals(nextId))
                        // 순서 판정은 서버(MilestonePolicy)가 이미 했다. 그대로 넘긴다.
                        .toggleable(milestone.toggleable())
                        .build())
                .toList();

        int overdueCount = (int) source.milestones().stream()
                .filter(milestone -> isOverdue(milestone, today))
                .count();

        return AgentMilestoneListResponse.builder()
                .projectId(projectId)
                .milestones(milestones)
                .summary(AgentMilestoneListResponse.Summary.builder()
                        .total(source.totalCount())
                        .completed(source.completedCount())
                        .completionRate(source.completionRate())
                        .overdueCount(overdueCount)
                        .build())
                .totalCount(milestones.size())
                // 마일스톤은 프로젝트당 최대 50개라 상한을 두지 않는다.
                .truncated(false)
                .build();
    }

    public AgentBudgetSummaryResponse budget(Long userId, Long projectId) {
        BudgetSummaryResponse source = expenseService.getBudget(projectId, userId);

        // 예산 0 = 제한 없음. 이때 잔여·집행률은 뜻이 없으므로 숫자 대신 null을 준다 —
        // 0을 내려보내면 에이전트가 "예산을 다 썼다"로 읽는다.
        boolean unlimited = source.totalBudget() == null || source.totalBudget() == 0L;

        return AgentBudgetSummaryResponse.builder()
                .projectId(projectId)
                .targetBudget(source.totalBudget())
                .spentAmount(source.executed())
                .remainingAmount(unlimited ? null : source.remaining())
                .executionRate(unlimited ? null : source.executionRate())
                .elapsedRate(source.elapsedRate())
                .expenseCount((int) source.expenseCount())
                .byCategory(source.byCategory().stream()
                        .map(category -> AgentBudgetSummaryResponse.CategoryAmount.builder()
                                .category(category.category().name())
                                .categoryLabel(category.category().getDescription())
                                .amount(category.amount())
                                .share(category.ratio())
                                .build())
                        .toList())
                .build();
    }

    public AgentExpenseListResponse expenses(Long userId, Long projectId, LocalDate from, LocalDate to,
                                             String category, boolean onlyMine, String sort, int size) {
        int validatedSize = AgentPage.validateSize(size);
        ExpenseCategory categoryFilter = parseCategory(category);

        PageResponse<ExpenseListResponse> page = expenseService.getExpenses(
                projectId, userId, from, to, categoryFilter,
                onlyMine ? userId : null,
                PageRequest.of(0, validatedSize, sortOf(sort)));

        List<AgentExpenseListResponse.AgentExpense> expenses = page.content().stream()
                .map(expense -> AgentExpenseListResponse.AgentExpense.builder()
                        .expenseId(expense.expenseId())
                        .expenseDate(expense.expenseDate())
                        .category(expense.category().name())
                        .categoryLabel(expense.category().getDescription())
                        .merchant(expense.merchant())
                        .purpose(expense.purpose())
                        .amount(expense.amount())
                        .spenderName(expense.spender().name())
                        .canEdit(userId.equals(expense.spender().userId()))
                        .build())
                .toList();

        return AgentExpenseListResponse.builder()
                .expenses(expenses)
                .totalCount(expenses.size())
                .truncated(page.totalElements() > expenses.size())
                .build();
    }

    // ================================= 쓰기 =================================

    public AgentWriteResults.ExpenseCreated createExpense(Long userId, AgentExpenseCreateRequest request,
                                                          String idempotencyKey) {
        Long projectId = request.projectId();
        ExpenseResponse created =
                expenseService.create(projectId, userId, idempotencyKey, request.toDomain());

        // 등록 직후 예산 영향을 바로 답변할 수 있게 집계를 다시 읽는다.
        // 여기서 더하기로 계산하면 미래 날짜 지출이 '사용'이 아닌 '사용 예정'으로 잡히는 규칙을 놓친다.
        BudgetSummaryResponse budget = expenseService.getBudget(projectId, userId);
        boolean unlimited = budget.totalBudget() == null || budget.totalBudget() == 0L;

        return AgentWriteResults.ExpenseCreated.builder()
                .expenseId(created.expenseId())
                .projectId(projectId)
                .expenseDate(request.expenseDate())
                .amount(request.amount())
                .spentAmountAfter(budget.executed())
                .executionRateAfter(unlimited ? null : budget.executionRate())
                .build();
    }

    public AgentWriteResults.MilestoneStatusChanged toggleMilestone(Long userId,
                                                                    AgentMilestoneStatusRequest request) {
        MilestoneStatusResponse changed = milestoneService.toggleStatus(
                request.projectId(), request.milestoneId(), userId, request.completed());

        // 변경 후 완료율은 여기서 따로 읽는다. 토글 서비스에 넣으면 화면이 쓰지도 않는 숫자를 위해
        // 모든 완료 체크에 조회가 하나씩 붙는다. 같은 트랜잭션이라 방금 바꾼 값이 반영된다.
        int completionRateAfter = milestoneService
                .getMilestones(request.projectId(), userId)
                .completionRate();

        return AgentWriteResults.MilestoneStatusChanged.builder()
                .milestoneId(changed.milestoneId())
                .goal(changed.goal())
                .completed(changed.completed())
                .completedAt(changed.completedAt())
                .changed(changed.changed())
                .completionRateAfter(completionRateAfter)
                .build();
    }

    // ================================= 내부 헬퍼 =================================

    // 목표일이 지났는데 아직 완료가 아니면 지연. 목표일 당일은 아직 지연이 아니다.
    private boolean isOverdue(MilestoneSummary milestone, LocalDate today) {
        return !milestone.done() && milestone.targetDate().isBefore(today);
    }

    // "제일 큰 지출이 뭐야"를 서버 정렬로 처리한다. 상한이 걸린 목록을 받아 에이전트가 다시 정렬하면
    // 상한 밖에 있는 진짜 최대 지출을 놓친다.
    // 보조 정렬(id)이 없으면 같은 날짜·금액 행의 순서가 매번 달라진다.
    private Sort sortOf(String sort) {
        if (SORT_AMOUNT_DESC.equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("id"));
        }
        return Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"));
    }

    private ExpenseCategory parseCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        try {
            return ExpenseCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException unknownCategory) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }
}
