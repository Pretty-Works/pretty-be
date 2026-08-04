package HK.PrettyWorks_BE.user.repository;

import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmployeeNo(String employeeNo);

    // 회원가입 시 사번/이메일 중복 여부를 미리 확인합니다.
    boolean existsByEmployeeNo(String employeeNo);

    boolean existsByEmail(String email);

    // 사용자 선택 자동완성. 성을 제외한 이름 일부로도 찾을 수 있도록 부분 일치 검색한다.
    @Query("""
            select u from UserEntity u
            where u.status in :employedStatuses
              and u.id <> :requesterId
              and u.name like concat('%', :keyword, '%')
            order by u.name asc, u.id asc
            """)
    List<UserEntity> searchByName(@Param("requesterId") Long requesterId,
                                  @Param("keyword") String keyword,
                                  @Param("employedStatuses") Collection<StatusType> employedStatuses,
                                  Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);
}
