package HK.PrettyWorks_BE.user.repository;

import HK.PrettyWorks_BE.user.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmployeeNo(String employeeNo);
}
