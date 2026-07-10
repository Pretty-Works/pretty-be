package HK.PrettyWorks_BE.auth.repository;

import HK.PrettyWorks_BE.auth.domain.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
}