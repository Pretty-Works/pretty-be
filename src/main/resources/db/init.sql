-- PrettyWorks MVP — 초기 DDL 스크립트
-- 새 MySQL 데이터베이스(prettyworks_test)에서 한 번 실행하면 모든 테이블이 만들어집니다.
-- ddl-auto: validate 정책이므로 엔티티가 바뀌면 이 파일도 같이 갱신해야 합니다.
-- Local 실행: mysql -uroot -p prettyworks_test < src/main/resources/db/init.sql
-- Docker 실행 : Get-Content src\main\resources\db\init.sql | docker compose exec -T mysql mysql -uroot -p1234 prettyworks_test


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
    target_budget DECIMAL(15,2) NOT NULL          COMMENT '목표 예산 (0 = 제한 없음)',
    description   VARCHAR(500)  NULL              COMMENT '설명',
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
-- =============================================================================
CREATE TABLE IF NOT EXISTS milestones (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL          COMMENT '프로젝트 FK',
    target_date DATE         NOT NULL          COMMENT '목표일',
    goal        VARCHAR(200) NOT NULL          COMMENT '목표 내용',
    created_at  DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_milestones_project_target (project_id, target_date),
    CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '프로젝트 마일스톤';


-- =============================================================================
-- todos : 할일 (프로젝트별 · 담당자별)
--   - 제목만 저장(짧은 VARCHAR), 본문 없음
--   - 홈: 담당자별 프로젝트 그룹 + 마감일 정렬 / 상세: 프로젝트별
-- =============================================================================
CREATE TABLE IF NOT EXISTS todos (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL             COMMENT '프로젝트 FK',
    assignee_id BIGINT       NOT NULL             COMMENT '담당자 (users FK)',
    title       VARCHAR(100) NOT NULL             COMMENT '할일 제목',
    status      VARCHAR(20)  NOT NULL             COMMENT '상태 (IN_PROGRESS / DONE)',
    due_date    DATE         NULL                 COMMENT '마감일',
    created_at  DATETIME(6)  NULL                 COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL                 COMMENT '수정 시각',
    PRIMARY KEY (id),
    KEY idx_todos_assignee_project (assignee_id, project_id, due_date),
    KEY idx_todos_project_due (project_id, due_date),
    CONSTRAINT fk_todos_project  FOREIGN KEY (project_id)  REFERENCES projects (id),
    CONSTRAINT fk_todos_assignee FOREIGN KEY (assignee_id) REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '할일';


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
    recording_url VARCHAR(500) NULL              COMMENT '녹취 파일 URL (GCS)',
    created_at    DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at   DATETIME(6)  NULL              COMMENT '수정 시각',
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
    CONSTRAINT fk_meeting_attendees_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_meeting_attendees_user    FOREIGN KEY (user_id)    REFERENCES users (id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '회의록 참석자';


/* 결재라인 수정 예정!!
-- =============================================================================
-- approvals : 결재 문서 (척추)
--   - status(PENDING/APPROVED/REJECTED)는 라인의 롤업 캐시, 정본은 approval_lines
--   - version : 낙관적 락 (@Version)
-- =============================================================================
CREATE TABLE IF NOT EXISTS approvals (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    document_no        VARCHAR(30)  NOT NULL          COMMENT '문서번호',
    title              VARCHAR(200) NOT NULL          COMMENT '제목',
    doc_type           VARCHAR(20)  NOT NULL          COMMENT '문서종류 (DRAFT 품의 / LEAVE 휴가 / ...)',
    drafted_at         DATE         NOT NULL          COMMENT '기안일',
    status             VARCHAR(20)  NOT NULL          COMMENT '상태 (PENDING / APPROVED / REJECTED)',
    drafter_id         BIGINT       NOT NULL          COMMENT '상신자 (users FK)',
    drafter_name       VARCHAR(20)  NOT NULL          COMMENT '상신자 이름 (기안 시점 스냅샷)',
    drafter_department VARCHAR(30)  NOT NULL          COMMENT '상신자 부서 (스냅샷)',
    drafter_position   VARCHAR(30)  NOT NULL          COMMENT '상신자 직책 (스냅샷)',
    version            BIGINT       NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
    created_at         DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at        DATETIME(6)  NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approvals_document_no (document_no),
    CONSTRAINT fk_approvals_drafter FOREIGN KEY (drafter_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '결재 문서';


-- =============================================================================
-- approval_details : 결재 기본 상세 (품의 등) — approval 과 1:1
-- =============================================================================
CREATE TABLE IF NOT EXISTS approval_details (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    approval_id BIGINT NOT NULL              COMMENT '결재 FK (1:1)',
    project_id  BIGINT NULL                  COMMENT '프로젝트 FK (없을 수 있음)',
    content     TEXT   NULL                  COMMENT '내용',
    created_at  DATETIME(6) NULL             COMMENT '생성 시각',
    modified_at DATETIME(6) NULL             COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_details_approval (approval_id),
    CONSTRAINT fk_approval_details_approval FOREIGN KEY (approval_id) REFERENCES approvals (id),
    CONSTRAINT fk_approval_details_project  FOREIGN KEY (project_id)  REFERENCES projects (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '결재 기본 상세';


-- =============================================================================
-- approval_leaves : 결재 휴가 상세 — approval 과 1:1
--   - days : 잔여 연차 계산용 (승인된 것만 합산)
--   - 생일 휴가(생일 ±7일) 제약은 앱 레벨 검증
-- =============================================================================
CREATE TABLE IF NOT EXISTS approval_leaves (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    approval_id BIGINT      NOT NULL          COMMENT '결재 FK (1:1)',
    start_date  DATE        NOT NULL          COMMENT '시작일',
    end_date    DATE        NOT NULL          COMMENT '마감일',
    reason      VARCHAR(255) NULL             COMMENT '사유',
    leave_type  VARCHAR(20) NOT NULL          COMMENT '휴가종류 (ANNUAL 연차 / BIRTHDAY 생일 / ...)',
    days        INT         NOT NULL          COMMENT '일수 (잔여 계산용)',
    created_at  DATETIME(6) NULL              COMMENT '생성 시각',
    modified_at DATETIME(6) NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_leaves_approval (approval_id),
    CONSTRAINT fk_approval_leaves_approval FOREIGN KEY (approval_id) REFERENCES approvals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '결재 휴가 상세';


-- =============================================================================
-- approval_lines : 결재선 (결재자만, 상신자 제외)
--   - UNIQUE(approval_id, step)    : 순서 중복 방지
--   - UNIQUE(approval_id, user_id) : 같은 사람 중복 방지
--   - 현재 차례 = 미승인(status<>APPROVED) 중 step 최소인 사람
-- =============================================================================
CREATE TABLE IF NOT EXISTS approval_lines (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    approval_id         BIGINT      NOT NULL          COMMENT '결재 FK',
    user_id             BIGINT      NOT NULL          COMMENT '결재자 (users FK)',
    step                INT         NOT NULL          COMMENT '결재순서',
    status              VARCHAR(20) NOT NULL          COMMENT '승인상태 (PENDING / APPROVED / REJECTED)',
    approver_name       VARCHAR(20) NOT NULL          COMMENT '결재자 이름 (스냅샷)',
    approver_department VARCHAR(30) NOT NULL          COMMENT '결재자 부서 (스냅샷)',
    approver_position   VARCHAR(30) NOT NULL          COMMENT '결재자 직책 (스냅샷)',
    reject_reason       VARCHAR(255) NULL             COMMENT '반려 사유 (반려 시)',
    processed_at        DATETIME(6) NULL              COMMENT '처리일시 (미처리면 NULL)',
    created_at          DATETIME(6) NULL              COMMENT '생성 시각',
    modified_at         DATETIME(6) NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_lines_step (approval_id, step),
    UNIQUE KEY uk_approval_lines_user (approval_id, user_id),
    CONSTRAINT fk_approval_lines_approval FOREIGN KEY (approval_id) REFERENCES approvals (id),
    CONSTRAINT fk_approval_lines_user     FOREIGN KEY (user_id)     REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '결재선';


-- =============================================================================
-- approval_references : 결재 참조자
-- =============================================================================
CREATE TABLE IF NOT EXISTS approval_references (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    approval_id BIGINT      NOT NULL          COMMENT '결재 FK',
    user_id     BIGINT      NOT NULL          COMMENT '참조자 (users FK)',
    created_at  DATETIME(6) NULL              COMMENT '생성 시각',
    modified_at DATETIME(6) NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_references_user (approval_id, user_id),
    CONSTRAINT fk_approval_references_approval FOREIGN KEY (approval_id) REFERENCES approvals (id),
    CONSTRAINT fk_approval_references_user     FOREIGN KEY (user_id)     REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '결재 참조';
 */


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
    created_at  DATETIME(6)  NULL               COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL               COMMENT '수정 시각',
    PRIMARY KEY (id),
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
    leave_type  VARCHAR(20)  NOT NULL          COMMENT '휴가 유형 (ANNUAL 연차 / SICK 병가)',
    reason      VARCHAR(255) NULL              COMMENT '사유',
    days        INT          NOT NULL          COMMENT '일수 (연차 사용/잔여 계산용)',
    created_at  DATETIME(6)  NULL              COMMENT '생성 시각',
    modified_at DATETIME(6)  NULL              COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_leaves_schedule (schedule_id),
    CONSTRAINT fk_schedule_leaves_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '휴가 상세';

-- =============================================================================
-- schedule_participants : 일정 참가자 (작성자 포함, role 로 구분)
--   - UNIQUE(user_id, schedule_id) : 중복 참가 방지 + "내 일정" 조회 인덱스 겸용
--   - 일정 삭제 시 참가자 행 정리 → schedule FK 는 ON DELETE CASCADE
--   - user FK 는 CASCADE 금지 (기록 보관) — users 는 soft delete 라 어차피 안 지워짐
-- =============================================================================
CREATE TABLE IF NOT EXISTS schedule_participants (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT      NOT NULL           COMMENT '일정 FK',
    user_id     BIGINT      NOT NULL           COMMENT '참가자 (users FK)',
    role        VARCHAR(20) NOT NULL           COMMENT '역할 (WRITER 작성자 / PARTICIPANT 참가자)',
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