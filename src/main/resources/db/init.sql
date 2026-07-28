-- PrettyWorks MVP — 초기 DDL 스크립트
-- 새 MySQL 데이터베이스(prettyworks_test)에서 한 번 실행하면 모든 테이블이 만들어집니다.
-- ddl-auto: validate 정책이므로 엔티티가 바뀌면 이 파일도 같이 갱신해야 합니다.
-- 로드(한글 안전, 자세한 절차는 같은 폴더 README.md):
--   docker cp src\main\resources\db\init.sql <컨테이너>:/tmp/init.sql
--   docker exec -i <컨테이너> sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test < /tmp/init.sql"
-- ※ Get-Content ... | docker ... (PowerShell 파이프)와 docker compose cp 는 한글 깨짐/파일 누락으로 금지.


-- =============================================================================
-- users : 사용자 테이블
--   - employee_no, email은 unique
--   - 재직중인 인원 검색이 자주 쓰이는 관계로 status, name 복합인덱스
-- =============================================================================
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_no   VARCHAR(20)  NOT NULL              COMMENT '사번 (로그인 ID)',
    password_hash VARCHAR(255) NOT NULL              COMMENT '비밀번호 해시 (BCrypt)',
    name          VARCHAR(20)  NOT NULL              COMMENT '이름',
    email         VARCHAR(100) NOT NULL              COMMENT '이메일',
    phone_number  VARCHAR(20)  NOT NULL              COMMENT '전화번호',
    birth_date    DATE         NOT NULL              COMMENT '생년월일',
    gender        VARCHAR(10)  NOT NULL              COMMENT '성별',
    department    VARCHAR(30)  NOT NULL              COMMENT '부서',
    position      VARCHAR(30)  NOT NULL              COMMENT '직책',
    status        VARCHAR(20)  NOT NULL              COMMENT '재직 상태',
    hire_date     DATE         NOT NULL              COMMENT '입사일',
    created_at    DATETIME(6)  NULL                  COMMENT '생성 시각',
    modified_at   DATETIME(6)  NULL                  COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_employee_no (employee_no),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status_name (status, name)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '사용자(임직원)';


-- =============================================================================
-- refresh_tokens : 로그인 세션별 refresh 토큰 (RTR + family 방식)
--   - 유저당 여러 행 = 기기별 동시 로그인. user_id는 PK가 아니라 인덱스만 갖는다.
--   - session_id(family): 한 번의 로그인을 식별한다. 토큰이 회전해도 유지되며 access token의 sid claim과 같다.
--   - 회전 시 이전 행을 지우지 않고 revoked_at만 남긴다. 그래야 "폐기된 토큰의 재사용 = 도난"을 탐지할 수 있다.
--       · 재사용이 감지되면 그 session_id의 행 전체를 무효화한다.
--   - token_hash: 원문을 저장하지 않는다. DB가 유출돼도 그대로 쓸 수 없어야 한다.
--   - 만료·폐기된 행은 정리 배치가 삭제한다(expires_at 인덱스 사용).
-- =============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    session_id   CHAR(36)     NOT NULL           COMMENT '세션(family) 식별자 — access token의 sid claim',
    user_id      BIGINT       NOT NULL           COMMENT '사용자 FK',
    token_hash   CHAR(64)     NOT NULL           COMMENT 'refresh token의 SHA-256 (원문 저장 금지)',
    expires_at   DATETIME(6)  NOT NULL           COMMENT '만료 시각 (정리 배치 기준)',
    revoked_at   DATETIME(6)  NULL               COMMENT '폐기 시각 (회전/로그아웃/도난 무효화). NULL이면 유효',
    revoke_reason VARCHAR(20) NULL               COMMENT '폐기 사유 (ROTATED/LOGOUT/THEFT/SESSION_LIMIT/NOT_EMPLOYED) — 재사용 시 도난 판정 기준',
    user_agent   VARCHAR(255) NULL               COMMENT '로그인 기기 정보 (로그인 기기 목록 표시용)',
    last_used_at DATETIME(6)  NULL               COMMENT '마지막 재발급 시각',
    created_at   DATETIME(6)  NULL               COMMENT '생성(로그인) 시각',
    modified_at  DATETIME(6)  NULL               COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_session (session_id),
    -- 로그인마다 "이 사용자의 살아 있는 세션"을 찾습니다. 회전 때문에 폐기된 행이 계속 쌓이므로
    -- user_id만으로는 스캔량이 커집니다. revoked_at을 붙여 활성 행만 바로 걸러냅니다.
    KEY idx_refresh_tokens_user_active (user_id, revoked_at),
    KEY idx_refresh_tokens_expires (expires_at),
    CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '로그인 세션(refresh token)';


-- =============================================================================
-- projects : 프로젝트
--   - 삭제는 하드 딜리트 대신 status = ARCHIVED(보관)로 처리
--   - 진행률은 저장하지 않고 계산(시작~목표일)
-- =============================================================================
CREATE TABLE IF NOT EXISTS projects (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)  NOT NULL          COMMENT '프로젝트명',
    status        VARCHAR(20)   NOT NULL          COMMENT '상태 (ONGOING / HOLDING / DROPPED / COMPLETED / ARCHIVED)',
    start_date    DATE          NOT NULL          COMMENT '시작일',
    target_date   DATE          NOT NULL          COMMENT '목표일',
    target_budget BIGINT NOT NULL                 COMMENT '목표 예산 원 단위 (0 = 제한 없음). expenses.amount와 동일 타입',
    description   VARCHAR(500)  NULL              COMMENT '설명',
    version       BIGINT        NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전 (동시 수정 시 나중 요청 차단)',
    created_at    DATETIME(6)   NULL              COMMENT '생성 시각',
    modified_at   DATETIME(6)   NULL              COMMENT '수정 시각',
    PRIMARY KEY (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '프로젝트';


-- =============================================================================
-- project_members : 프로젝트 멤버 (users ↔ projects 다대다 중간 테이블)
--   - 한 프로젝트에 한 사용자는 1번만 → (user_id, project_id) 복합 UNIQUE
--   - 탈퇴 시 하드 딜리트 대신 status = LEFT + left_at 기록, 재참여 시 행 재활성화
-- =============================================================================
CREATE TABLE IF NOT EXISTS project_members (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    project_id  BIGINT      NOT NULL              COMMENT '프로젝트 FK',
    user_id     BIGINT      NOT NULL              COMMENT '사용자 FK',
    is_owner    BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '오너 여부 (생성자 true / 참여자 false)',
    role        VARCHAR(20) NULL                  COMMENT '역할',
    status      VARCHAR(20) NOT NULL              COMMENT '참여 상태 (ACTIVE / LEFT)',
    left_at     DATETIME(6) NULL                  COMMENT '탈퇴 시각 (참여중이면 NULL)',
    created_at  DATETIME(6) NULL                  COMMENT '생성(참여) 시각',
    modified_at DATETIME(6) NULL                  COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_members_user_project (user_id, project_id),
    KEY idx_project_members_project_status (project_id, status),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user    FOREIGN KEY (user_id)    REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '프로젝트 멤버';


-- =============================================================================
-- milestones : 프로젝트 마일스톤 (시기별 목표)
--   - target_date 는 프로젝트 기간(projects.start_date ~ target_date) 내여야 함 (앱 레벨 검증)
--   - 목록은 target_date 오름차순 조회
--   - completed_at 은 목표일과 무관하다. 목표일이 지나도 자동 완료되지 않으며, 사람이 체크해야 채워진다.
--     날짜로 완료를 파생하면 지연된 마일스톤이 완료로 잡혀 완료율이 실제 진척을 왜곡한다.
-- =============================================================================
CREATE TABLE IF NOT EXISTS milestones (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    project_id   BIGINT       NOT NULL          COMMENT '프로젝트 FK',
    target_date  DATE         NOT NULL          COMMENT '목표일',
    goal         VARCHAR(200) NOT NULL          COMMENT '목표 내용',
    completed_at DATETIME(6)  NULL              COMMENT '완료 시각. NULL이면 미완료(대기)',
    created_at   DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at  DATETIME(6)  NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_milestones_project_target (project_id, target_date),
    CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '프로젝트 마일스톤';


-- =============================================================================
-- tasks : 할 일 (프로젝트별 · 개인 할 일 포함)
--   - content 한 줄만 저장(본문 없음)
--   - project_id NULL 이면 개인 할 일 (어느 프로젝트에도 안 묶임)
--   - 담당자는 현재 작성자 본인. 추후 타인 배정 확장 시 author_id 분리
--   - 홈: 담당자별 프로젝트 그룹 + 마감일 정렬 / 상세: 프로젝트별
-- =============================================================================
CREATE TABLE IF NOT EXISTS tasks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NULL                   COMMENT '프로젝트 FK (개인 할 일이면 NULL)',
    assignee_id BIGINT       NOT NULL               COMMENT '담당자 (users FK, 현재는 작성자 본인)',
    content     VARCHAR(100) NOT NULL               COMMENT '할 일 내용 (한 줄)',
    completed_at DATETIME(6) NULL                   COMMENT '완료 시각 (null이면 미완료)',
    due_date    DATE         NOT NULL               COMMENT '마감일',
    created_at  DATETIME(6)  NULL                   COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL                   COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_tasks_assignee_project_due (assignee_id, project_id, due_date),
    KEY idx_tasks_project_due (project_id, due_date),
    CONSTRAINT fk_tasks_project  FOREIGN KEY (project_id)  REFERENCES projects (id),
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '할 일';


-- =============================================================================
-- project_posts : 프로젝트 게시판 (제목 + 내용만 있는 자유 게시판)
-- =============================================================================
CREATE TABLE IF NOT EXISTS project_posts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL          COMMENT '프로젝트 FK',
    author_id   BIGINT       NOT NULL          COMMENT '작성자 (users FK)',
    title       VARCHAR(200) NOT NULL          COMMENT '제목',
    content     TEXT         NOT NULL          COMMENT '내용',
    created_at  DATETIME(6)  NULL              COMMENT '작성 시각',
    modified_at DATETIME(6)  NULL              COMMENT '수정 시각',
    deleted_at    DATETIME(6)  NULL            COMMENT '삭제 시각',
    PRIMARY KEY (id),
    KEY idx_project_posts_project_created (project_id, created_at),
    CONSTRAINT fk_project_posts_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_posts_author  FOREIGN KEY (author_id)  REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '프로젝트 게시판';


-- =============================================================================
-- meetings : 회의록 (기록 문서)
--   - project_id 는 NULL 허용 (프로젝트에 안 묶인 회의록도 있음)
--   - 목록은 문서번호/제목만, 상세에서 content/follow_up(TEXT) 조회
-- =============================================================================
CREATE TABLE IF NOT EXISTS meetings (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NULL              COMMENT '프로젝트 FK (없을 수 있음)',
    document_no   VARCHAR(30)  NOT NULL          COMMENT '문서번호',
    title         VARCHAR(200) NOT NULL          COMMENT '회의명',
    author_id     BIGINT       NOT NULL          COMMENT '작성자 (users FK)',
    meeting_date  DATE         NOT NULL          COMMENT '일자',
    location      VARCHAR(100) NULL              COMMENT '장소',
    purpose       VARCHAR(500) NULL              COMMENT '회의 목적',
    content       TEXT         NULL              COMMENT '주요 내용',
    follow_up     TEXT         NULL              COMMENT '후속 조치',
    recording     VARCHAR(500) NULL              COMMENT '녹취 파일 URL (GCS)',
    created_at    DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at   DATETIME(6)  NULL              COMMENT '수정 시각',
    deleted_at    DATETIME(6)  NULL              COMMENT '삭제 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meetings_document_no (document_no),
    CONSTRAINT fk_meetings_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_meetings_author  FOREIGN KEY (author_id)  REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '회의록';


-- =============================================================================
-- meeting_attendees : 회의록 참석자 (작성자 포함, role 로 구분)
--   - 한 회의에 한 사람 1번 → (user_id, meeting_id) 복합 UNIQUE
--   - 이름·부서는 작성 시점 스냅샷(이름/부서 변경·퇴사에도 보존)
-- =============================================================================
CREATE TABLE IF NOT EXISTS meeting_attendees (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    meeting_id          BIGINT      NOT NULL           COMMENT '회의록 FK',
    user_id             BIGINT      NOT NULL           COMMENT '사용자 FK',
    attendee_name       VARCHAR(20) NOT NULL           COMMENT '참석자 이름 (작성 시점 스냅샷)',
    attendee_department VARCHAR(30) NOT NULL           COMMENT '참석자 부서 (작성 시점 스냅샷)',
    role                VARCHAR(20) NOT NULL           COMMENT '역할 (WRITER 작성자 / ATTENDEE 참석자)',
    created_at          DATETIME(6) NULL               COMMENT '생성 시각',
    modified_at         DATETIME(6) NULL               COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meeting_attendees_user_meeting (user_id, meeting_id),
    KEY idx_meeting_attendees_name (attendee_name, meeting_id),
    CONSTRAINT fk_meeting_attendees_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_meeting_attendees_user    FOREIGN KEY (user_id)    REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '회의록 참석자';


-- =============================================================================
-- schedules : 일정 + 휴가 공통 캘린더 행
--   - 휴가도 여기에 행으로 저장, 휴가 상세는 schedule_leaves 로 1:1 확장
--   - "휴가 여부" = schedule_leaves 조인 매칭 여부
--   - 회의/외근/개인은 구분 컬럼 없이 색 통일
-- =============================================================================
CREATE TABLE IF NOT EXISTS schedules (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL           COMMENT '작성자 (users FK)',
    title       VARCHAR(200) NOT NULL           COMMENT '일정 제목',
    start_at    DATETIME(6)  NOT NULL           COMMENT '시작일시',
    end_at      DATETIME(6)  NOT NULL           COMMENT '종료일시',
    all_day     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '종일 여부',
    type        VARCHAR(20)  NOT NULL DEFAULT 'PERSONAL' COMMENT '유형 (MEETING 회의 / FIELDWORK 외근 / PERSONAL 개인)',
    created_at  DATETIME(6)  NULL               COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL               COMMENT '수정 시각',
    PRIMARY KEY (id),
    -- 겹침 조회 최적화: start_at <= :toEnd AND end_at >= :fromStart, ORDER BY start_at ASC
    -- start_at 범위+정렬을 인덱스로 처리, end_at 은 인덱스 조건 푸시다운(ICP) 필터로 활용
    KEY idx_schedules_start_end (start_at, end_at),
    CONSTRAINT fk_schedules_user FOREIGN KEY (user_id) REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '일정';


-- =============================================================================
-- schedule_leaves : 휴가 상세 (schedules 와 1:1 확장)
--   - 휴가도 schedules 에 행으로 저장 → 캘린더는 schedules 만 조회(union 불필요)
--   - 승인 없음(결재 도메인과 분리) → 상태 컬럼 없음, 취소 = 일정 삭제(CASCADE)
--   - surrogate id + UNIQUE(schedule_id) 로 1:1 (approval_details/approval_leaves 와 동일 패턴)
-- =============================================================================
CREATE TABLE IF NOT EXISTS schedule_leaves (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT       NOT NULL          COMMENT '일정 FK (1:1)',
    leave_type  VARCHAR(20)  NOT NULL          COMMENT '휴가 유형 (ANNUAL 연차 / EXCUSED 공가)',
    reason      VARCHAR(255) NULL              COMMENT '사유',
    days        INT          NOT NULL          COMMENT '일수 (연차 사용/잔여 계산용)',
    created_at  DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_leaves_schedule (schedule_id),
    CONSTRAINT fk_schedule_leaves_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '휴가 상세';


-- =============================================================================
-- schedule_participants : 일정 참가자 (작성자 포함, is_writer 로 구분)
--   - UNIQUE(user_id, schedule_id) : 중복 참가 방지 + "내 일정" 조회 인덱스 겸용
--   - 일정 삭제 시 참가자 행 정리 → schedule FK 는 ON DELETE CASCADE
--   - user FK 는 CASCADE 금지 (기록 보관) — users 는 soft delete 라 어차피 안 지워짐
-- =============================================================================
CREATE TABLE IF NOT EXISTS schedule_participants (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT      NOT NULL           COMMENT '일정 FK',
    user_id     BIGINT      NOT NULL           COMMENT '참가자 (users FK)',
    is_writer   BOOLEAN     NOT NULL           COMMENT '작성자 여부 (1: WRITER 작성자 / 0: PARTICIPANT 참가자)',
    left_at     DATETIME(6) NULL               COMMENT '나간 시각 (참여중이면 NULL = 활성). 나가기 = soft delete',
    created_at  DATETIME(6) NULL               COMMENT '생성 시각',
    modified_at DATETIME(6) NULL               COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_participants_user (user_id, schedule_id),
    CONSTRAINT fk_schedule_participants_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_participants_user     FOREIGN KEY (user_id)     REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '일정 참가자';


-- =============================================================================
-- leave_balances : 연차 현황 (사용자별 · 연도별 부여일수)
--   - 부여일수만 저장, 사용/잔여는 승인된 approval_leaves 에서 계산
--   - UNIQUE(user_id, year) : 매년 1/1 부여 배치의 멱등 키 겸용 (중복 부여 방지)
-- =============================================================================
CREATE TABLE IF NOT EXISTS leave_balances (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL          COMMENT '사용자 FK',
    year         SMALLINT    NOT NULL          COMMENT '연도',
    granted_days INT         NOT NULL          COMMENT '부여일수 (그 해 총 부여)',
    created_at   DATETIME(6) NULL              COMMENT '생성 시각',
    modified_at  DATETIME(6) NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_balances_user_year (user_id, year),
    CONSTRAINT fk_leave_balances_user FOREIGN KEY (user_id) REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '연차 현황';


-- =============================================================================
-- expenses : 프로젝트 지출 (재무 - 지출 내역)
--   - spender_id 는 토큰 userId (대리 등록 없음). 목록 '사용자' 컬럼 · 부서별 집계 근거라 NOT NULL
--   - status(사용/사용예정)·집행률·잔여는 저장하지 않고 조회 시점 계산(파생)
--   - 소프트 삭제: 모든 조회 · 집계는 deleted_at IS NULL 을 기본 조건으로 포함
--   - 집계 쿼리가 (project_id, deleted_at, expense_date)를 함께 타므로 복합 인덱스
-- =============================================================================
CREATE TABLE IF NOT EXISTS expenses (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    project_id   BIGINT       NOT NULL           COMMENT '프로젝트 FK (Path Variable)',
    spender_id   BIGINT       NOT NULL           COMMENT '사용자 FK (토큰 userId) — 사용자 컬럼·부서별 집계 근거',
    expense_date DATE         NOT NULL           COMMENT '사용일',
    category     VARCHAR(20)  NOT NULL           COMMENT '지출 유형 (ExpenseCategory, STRING)',
    merchant     VARCHAR(100) NOT NULL           COMMENT '사용처',
    purpose      VARCHAR(255) NOT NULL           COMMENT '사용 목적',
    amount       BIGINT       NOT NULL           COMMENT '금액(원, 1 이상)',
    deleted_at   DATETIME(6)  NULL               COMMENT '소프트 삭제 시각 (조회·집계에서 IS NULL 제외)',
    deleted_by   BIGINT       NULL               COMMENT '삭제 실행자 (본인만 삭제 → spender_id와 동일)',
    created_at   DATETIME(6)  NULL               COMMENT '생성 시각',
    modified_at  DATETIME(6)  NULL               COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_expenses_project_deleted_date (project_id, deleted_at, expense_date),
    KEY idx_expenses_merchant (merchant),
    KEY idx_expenses_purpose (purpose),
    CONSTRAINT fk_expenses_project    FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_expenses_spender    FOREIGN KEY (spender_id) REFERENCES users (id),
    CONSTRAINT fk_expenses_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '프로젝트 지출';


-- =============================================================================
-- idempotency_keys : 멱등 키 (생성 API 중복 요청 방어 — 버튼 연타/재시도)
--   - idempotency_key UNIQUE 인덱스가 보장의 핵심. 본 리소스 INSERT와 같은 트랜잭션에서 기록.
--   - request_hash: 메서드+실제경로(projectId 포함)+본문 SHA-256 → 같은 키 다른 내용(409) 판별.
--   - resource_id: 생성된 리소스 ID. 재시도 시 첫 응답을 재생.
--   - 24시간 보관 후 정리 배치로 삭제(별도). INSERT 성격 생성 API에만 적용.
-- =============================================================================
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(64)  NOT NULL           COMMENT '클라이언트 발급 UUID — 이 UNIQUE가 멱등 보장의 핵심',
    endpoint        VARCHAR(200) NOT NULL           COMMENT '대상 엔드포인트 (예: POST /api/v1/projects/{projectId}/expenses)',
    user_id         BIGINT       NOT NULL           COMMENT '요청자 (users FK)',
    request_hash    VARCHAR(64)  NOT NULL           COMMENT '요청 지문 SHA-256 (메서드+실제경로+본문)',
    resource_id     BIGINT       NOT NULL           COMMENT '생성된 리소스 ID (예: expenseId) — 재시도 시 재생',
    created_at      DATETIME(6)  NULL               COMMENT '생성 시각 (정리 배치 기준)',
    modified_at     DATETIME(6)  NULL               COMMENT '수정 시각 (불변이라 미사용, 컨벤션 일관)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    -- 정리 배치가 created_at 기준으로 지웁니다. 없으면 매번 풀스캔합니다.
    KEY idx_idempotency_created (created_at),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '멱등 키';