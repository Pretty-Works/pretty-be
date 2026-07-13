-- PrettyWorks MVP — 초기 DDL 스크립트
-- 새 MySQL 데이터베이스(prettyworks_test)에서 한 번 실행하면 모든 테이블이 만들어집니다.
-- ddl-auto: validate 정책이므로 엔티티가 바뀌면 이 파일도 같이 갱신해야 합니다.
-- Local 실행: mysql -uroot -p prettyworks_test < src/main/resources/db/init.sql
-- Docker 실행 : Get-Content src\main\resources\db\init.sql | docker compose exec -T mysql mysql -uroot -p1234 prettyworks_test


-- =============================================================================
-- users : 회원(거래처) 테이블
--   - email, business_number는 unique
-- =============================================================================
CREATE TABLE IF NOT EXISTS users (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    employee_no               VARCHAR(20)  NOT NULL,
    name                      VARCHAR(20)  NOT NULL,
    email                     VARCHAR(100) NOT NULL,
    phone                     VARCHAR(20)  NOT NULL,
    password_hash             VARCHAR(255) NOT NULL,
    department                VARCHAR(30)  NOT NULL,
    position                  VARCHAR(30)  NOT NULL,
    status                    VARCHAR(20)         NOT NULL,
    hire_date                 DATE         NOT NULL,
    created_at                DATETIME(6)  NULL,
    modified_at               DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_employee_no (employee_no),
    UNIQUE KEY uk_users_email (email)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- =============================================================================
-- refresh_tokens : 사용자별 refresh 토큰 1:1 테이블 (RTR 정책)
--   - user_id가 PK 겸 FK
--   - 로그아웃 / 회원탈퇴 시 ON DELETE CASCADE로 자동 정리
-- =============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(512) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NULL,
    modified_at DATETIME(6)  NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
