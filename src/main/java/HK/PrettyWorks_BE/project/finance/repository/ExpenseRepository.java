package HK.PrettyWorks_BE.project.finance.repository;

import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import HK.PrettyWorks_BE.project.finance.domain.ExpenseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    // 수정·삭제: 지출 조회(프로젝트 소속 확인 포함). 삭제된 건도 반환해 EXPENSE_006(이미 삭제)을 구분한다.
    Optional<ExpenseEntity> findByIdAndProjectId(Long id, Long projectId);

    // 목록 조회: 소프트 삭제되지 않은 지출을 사용일 구간·검색어로 걸러 페이지 단위로 가져온다.
    //
    // 사용 내역/예정 탭을 status 분기 대신 날짜 경계(from~to)로 받는다. 두 탭이 결국 "오늘 기준 이전/이후"라
    // 경계값만 바꾸면 같은 쿼리로 처리되고, 나중에 기간 필터가 붙어도 이 쿼리를 그대로 쓸 수 있다.
    //   EXECUTED → from=null, to=오늘 / PLANNED → from=내일, to=null
    //
    // order by를 넣지 않는다. 탭마다 정렬 방향이 반대라 서비스가 Pageable의 Sort로 넘긴다.
    // (Sort 속성은 엔티티 필드명 기준: expenseDate, id)
    // category·spenderId는 에이전트 도구(expense.list)가 쓰는 추가 필터다. 화면은 둘 다 null로 부른다.
    // 같은 조건을 쿼리 두 벌로 나누면 소프트 삭제 제외 같은 규칙이 한쪽에만 반영되어 갈라진다.
    @Query(value = """
            select e from ExpenseEntity e
            where e.projectId = :projectId
              and e.deletedAt is null
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
              and (:category is null or e.category = :category)
              and (:spenderId is null or e.spenderId = :spenderId)
              and (:keyword is null
                   or e.merchant like concat('%', :keyword, '%')
                   or e.purpose like concat('%', :keyword, '%'))
            """,
            countQuery = """
            select count(e) from ExpenseEntity e
            where e.projectId = :projectId
              and e.deletedAt is null
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
              and (:category is null or e.category = :category)
              and (:spenderId is null or e.spenderId = :spenderId)
              and (:keyword is null
                   or e.merchant like concat('%', :keyword, '%')
                   or e.purpose like concat('%', :keyword, '%'))
            """)
    Page<ExpenseEntity> findExpenses(@Param("projectId") Long projectId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("category") ExpenseCategory category,
                                     @Param("spenderId") Long spenderId,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

    // 예산 현황 ①: 사용(오늘까지)·사용 예정(내일부터) 합계.
    // 같은 행 집합을 사용일로만 나눈 것이라 CASE로 한 번에 집계한다. 지출이 없으면 두 값 모두 null.
    @Query("""
            select new HK.PrettyWorks_BE.project.finance.repository.BudgetTotalRow(
                       sum(case when e.expenseDate <= :today then e.amount else 0 end),
                       sum(case when e.expenseDate >  :today then e.amount else 0 end),
                       count(e))
            from ExpenseEntity e
            where e.projectId = :projectId and e.deletedAt is null
            """)
    BudgetTotalRow sumByProject(@Param("projectId") Long projectId, @Param("today") LocalDate today);

    // 예산 현황 ②: 항목(카테고리)별 사용 합계. 도넛 차트가 '사용' 기준이라 사용 예정은 제외한다.
    // 사용액이 0인 카테고리는 group by 특성상 애초에 행이 생기지 않는다. 금액 내림차순 고정.
    @Query("""
            select new HK.PrettyWorks_BE.project.finance.repository.CategorySumRow(
                       e.category, sum(e.amount))
            from ExpenseEntity e
            where e.projectId = :projectId and e.deletedAt is null
              and e.expenseDate <= :today
            group by e.category
            order by sum(e.amount) desc
            """)
    List<CategorySumRow> sumByCategory(@Param("projectId") Long projectId, @Param("today") LocalDate today);

    // 예산 현황 ③: 부서별 사용 합계. 부서는 지출에 저장하지 않고 지출자의 현재 소속에서 파생한다
    // (사람의 소속은 바뀌며, 집계는 항상 현재 조직 기준이면 충분하다).
    @Query("""
            select new HK.PrettyWorks_BE.project.finance.repository.DepartmentSumRow(
                       u.department, sum(e.amount))
            from ExpenseEntity e
            join UserEntity u on u.id = e.spenderId
            where e.projectId = :projectId and e.deletedAt is null
              and e.expenseDate <= :today
            group by u.department
            order by sum(e.amount) desc
            """)
    List<DepartmentSumRow> sumByDepartment(@Param("projectId") Long projectId, @Param("today") LocalDate today);

    // 프로젝트 기간 축소 검증: 새 기간을 벗어나는 사용일이 남는지 확인한다. (소프트 삭제된 건은 제외)
    @Query("select count(e) > 0 from ExpenseEntity e " +
            "where e.projectId = :projectId and e.deletedAt is null " +
            "and (e.expenseDate < :startDate or e.expenseDate > :endDate)")
    boolean existsOutsidePeriod(@Param("projectId") Long projectId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);
}
