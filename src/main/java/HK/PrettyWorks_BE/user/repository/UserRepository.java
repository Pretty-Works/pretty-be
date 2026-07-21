package HK.PrettyWorks_BE.user.repository;

import HK.PrettyWorks_BE.user.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmployeeNo(String employeeNo);

    // 회원가입 시 사번/이메일 중복 여부를 미리 확인합니다.
    boolean existsByEmployeeNo(String employeeNo);

    boolean existsByEmail(String email);
}
