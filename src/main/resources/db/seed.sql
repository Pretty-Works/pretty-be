-- PrettyWorks 통합 시드 데이터
-- 빈(방금 init.sql 로 생성된) DB 기준으로 한 번 실행 → 전 테이블에 데이터가 채워집니다.
-- id 는 AUTO_INCREMENT 라 "빈 테이블에 아래 순서대로" 넣으면 users 1~10, projects 1~5 … 로 부여됩니다.
-- 전 사용자 비밀번호: Test1234!  (BCrypt 해시)
--
-- 날짜는 전부 CURDATE()/NOW() 기준 상대값입니다. 절대 날짜로 두면 시간이 지날수록 데이터가 과거로 밀려
-- "이번 주 할 일", "사용 예정 지출" 같은 파생값이 의미를 잃기 때문입니다. 언제 로드해도 동일한 상황이 만들어집니다.
--
-- 로드 (한글 안전, 자세한 절차는 같은 폴더 README.md):
--   docker cp src\main\resources\db\seed.sql <컨테이너>:/tmp/seed.sql
--   docker exec -i <컨테이너> sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test < /tmp/seed.sql"
-- ※ Get-Content ... | docker ... (PowerShell 파이프)와 docker compose cp 는 한글 깨짐/파일 누락으로 금지.
--
-- ※ refresh_tokens 는 시드하지 않습니다. 로그인할 때 실제 토큰의 해시로 만들어지는 런타임 데이터라,
--   미리 넣어봐야 어떤 토큰과도 대응되지 않아 쓸모가 없습니다.

SET NAMES utf8mb4;

-- 전원 공통 비밀번호 해시 (Test1234!)
SET @pw = '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC';


-- =============================================================================
-- 1) users  (id 1~10)
--   1 김피엠   PM        TEAM_LEADER  ACTIVE    | 6 강지우   DATA      STAFF        ACTIVE
--   2 이하늘   BACKEND   SENIOR       ACTIVE    | 7 윤하은   FRONTEND  STAFF        ACTIVE
--   3 박도윤   BACKEND   STAFF        ACTIVE    | 8 임도현   DEVOPS    SENIOR       ACTIVE
--   4 최서아   FRONTEND  SENIOR       ACTIVE    | 9 한퇴사   QA        STAFF        RESIGNED (로그인 차단 테스트)
--   5 정민준   PLANNING  PART_LEADER  ACTIVE    |10 오휴직   SALES     STAFF        ON_LEAVE (로그인 허용, 지출 차단)
-- =============================================================================
INSERT INTO users
    (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at)
VALUES
    ('EMP001', @pw, '김피엠', 'emp001@test.com', '010-1000-0001', '1985-03-10', 'MALE',   'PM',       'TEAM_LEADER', 'ACTIVE',   '2015-01-02', NOW(6), NOW(6)),
    ('EMP002', @pw, '이하늘', 'emp002@test.com', '010-1000-0002', '1988-07-21', 'FEMALE', 'BACKEND',  'SENIOR',      'ACTIVE',   '2017-03-04', NOW(6), NOW(6)),
    ('EMP003', @pw, '박도윤', 'emp003@test.com', '010-1000-0003', '1995-11-05', 'MALE',   'BACKEND',  'STAFF',       'ACTIVE',   '2022-06-01', NOW(6), NOW(6)),
    ('EMP004', @pw, '최서아', 'emp004@test.com', '010-1000-0004', '1990-01-18', 'FEMALE', 'FRONTEND', 'SENIOR',      'ACTIVE',   '2019-09-16', NOW(6), NOW(6)),
    ('EMP005', @pw, '정민준', 'emp005@test.com', '010-1000-0005', '1986-05-30', 'MALE',   'PLANNING', 'PART_LEADER', 'ACTIVE',   '2016-02-13', NOW(6), NOW(6)),
    ('EMP006', @pw, '강지우', 'emp006@test.com', '010-1000-0006', '1996-08-24', 'FEMALE', 'DATA',     'STAFF',       'ACTIVE',   '2023-04-06', NOW(6), NOW(6)),
    ('EMP007', @pw, '윤하은', 'emp007@test.com', '010-1000-0007', '1997-12-12', 'FEMALE', 'FRONTEND', 'STAFF',       'ACTIVE',   '2023-07-19', NOW(6), NOW(6)),
    ('EMP008', @pw, '임도현', 'emp008@test.com', '010-1000-0008', '1989-04-02', 'MALE',   'DEVOPS',   'SENIOR',      'ACTIVE',   '2018-11-05', NOW(6), NOW(6)),
    ('EMP009', @pw, '한퇴사', 'emp009@test.com', '010-1000-0009', '1993-08-24', 'FEMALE', 'QA',       'STAFF',       'RESIGNED', '2020-04-06', NOW(6), NOW(6)),
    ('EMP010', @pw, '오휴직', 'emp010@test.com', '010-1000-0010', '1992-12-12', 'MALE',   'SALES',    'STAFF',       'ON_LEAVE', '2021-07-19', NOW(6), NOW(6));


-- =============================================================================
-- 2) projects  (id 1~5)  — version 은 DEFAULT 0 으로 들어갑니다(낙관적 락 초기값).
--   기간은 오늘 기준 상대값이라, 아래 하위 데이터(할 일·지출·회의록)도 항상 기간 안에 들어옵니다.
-- =============================================================================
INSERT INTO projects
    (name, status, start_date, target_date, target_budget, description, created_at, modified_at)
VALUES
    -- 진행 중(중반) — 기간 -60 ~ +60
    ('AI 검색 고도화',        'ONGOING',   DATE_SUB(CURDATE(), INTERVAL 60 DAY),  DATE_ADD(CURDATE(), INTERVAL 60 DAY),  50000000, '사내 검색 품질 개선 및 임베딩 기반 랭킹 도입', NOW(6), NOW(6)),
    -- 진행 중(초반) — 기간 -30 ~ +150
    ('사내 그룹웨어 리뉴얼',  'ONGOING',   DATE_SUB(CURDATE(), INTERVAL 30 DAY),  DATE_ADD(CURDATE(), INTERVAL 150 DAY), 80000000, '레거시 그룹웨어 UI/UX 전면 개편',            NOW(6), NOW(6)),
    -- 보류 — 기간 -90 ~ +30
    ('데이터 파이프라인 구축','HOLDING',   DATE_SUB(CURDATE(), INTERVAL 90 DAY),  DATE_ADD(CURDATE(), INTERVAL 30 DAY),  30000000, '수집~적재 자동화 파이프라인 (일시 보류)',     NOW(6), NOW(6)),
    -- 완료 — 기간 -210 ~ -30 (수정 차단 PROJECT_020 테스트용)
    ('레거시 마이그레이션',   'COMPLETED', DATE_SUB(CURDATE(), INTERVAL 210 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY),  20000000, '온프레미스 → 클라우드 이관 (완료)',           NOW(6), NOW(6)),
    -- 보관(소프트 삭제) — 기간 -520 ~ -210
    ('구 사내포털',           'ARCHIVED',  DATE_SUB(CURDATE(), INTERVAL 520 DAY), DATE_SUB(CURDATE(), INTERVAL 210 DAY), 10000000, '구버전 사내포털 (보관 처리)',                 NOW(6), NOW(6));


-- =============================================================================
-- 3) project_members
--   오너(is_owner=TRUE)는 수정 API에서 제외 대상이라 members 요청에 넣어도 무시됩니다.
--   P2 의 user3 은 LEFT 상태 — 탈퇴 멤버가 조회·권한에서 빠지는지 확인용.
--   role='PM' 인 참여자는 오너가 아니어도 프로젝트 수정·상태변경이 가능합니다.
-- =============================================================================
INSERT INTO project_members
    (project_id, user_id, is_owner, role, status, left_at, created_at, modified_at)
VALUES
    -- P1 (owner 김피엠) — 이하늘이 PM 역할이라 오너 부재 시 대체 가능
    (1, 1, TRUE,  'PM',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 2, FALSE, 'PM',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 3, FALSE, 'BE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 4, FALSE, 'FE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 6, FALSE, NULL,      'ACTIVE', NULL, NOW(6), NOW(6)),
    -- P2 (owner 김피엠), user3 은 탈퇴(LEFT)
    (2, 1, TRUE,  'PM',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 4, FALSE, 'FE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 5, FALSE, 'PLANNER', 'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 7, FALSE, 'FE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 8, FALSE, 'DEVOPS',  'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 3, FALSE, 'BE',      'LEFT',   DATE_SUB(NOW(6), INTERVAL 18 DAY), NOW(6), NOW(6)),
    -- P3 (owner 정민준)
    (3, 5, TRUE,  'PM',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (3, 2, FALSE, 'BE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (3, 6, FALSE, 'DATA',    'ACTIVE', NULL, NOW(6), NOW(6)),
    -- P4 (owner 김피엠, 완료 프로젝트)
    (4, 1, TRUE,  'PM',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (4, 2, FALSE, 'BE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    (4, 4, FALSE, 'FE',      'ACTIVE', NULL, NOW(6), NOW(6)),
    -- P5 (owner 김피엠, 보관 프로젝트)
    (5, 1, TRUE,  'PM',      'ACTIVE', NULL, NOW(6), NOW(6));


-- =============================================================================
-- 4) milestones  (목표일은 각 프로젝트 기간 안)
--   completed_at 은 목표일과 무관합니다. 사람이 체크해야 채워지므로 아래처럼 섞어 둡니다.
--     P1: 3개 중 1개 완료 → 완료율 33%
--     P2: 2개 중 0개 완료 → 완료율 0% (도넛이 빈 경우)
--     P3: 2개 중 1개 완료, 단 미완료 항목의 목표일이 이미 지남 → '지연' 상태 확인용
--         (날짜로 파생했다면 100%가 나왔을 케이스라, 완료율이 날짜와 무관함을 검증합니다)
--     P4: 2개 모두 완료 → 완료율 100%, 목표 마일스톤 null
-- =============================================================================
INSERT INTO milestones
    (project_id, target_date, goal, completed_at, created_at, modified_at)
VALUES
    (1, DATE_SUB(CURDATE(), INTERVAL 20 DAY), '1차 검색 품질 벤치마크', DATE_SUB(NOW(6), INTERVAL 18 DAY), NOW(6), NOW(6)),
    (1, DATE_ADD(CURDATE(), INTERVAL 15 DAY), '임베딩 파이프라인 완료', NULL,                              NOW(6), NOW(6)),
    (1, DATE_ADD(CURDATE(), INTERVAL 55 DAY), '정식 배포',              NULL,                              NOW(6), NOW(6)),
    (2, DATE_ADD(CURDATE(), INTERVAL 30 DAY), '디자인 시스템 확정',     NULL,                              NOW(6), NOW(6)),
    (2, DATE_ADD(CURDATE(), INTERVAL 120 DAY),'전 기능 QA 완료',        NULL,                              NOW(6), NOW(6)),
    (3, DATE_SUB(CURDATE(), INTERVAL 40 DAY), '수집 스키마 설계',       DATE_SUB(NOW(6), INTERVAL 38 DAY), NOW(6), NOW(6)),
    (3, DATE_SUB(CURDATE(), INTERVAL 10 DAY), '적재 자동화',            NULL,                              NOW(6), NOW(6)),
    (4, DATE_SUB(CURDATE(), INTERVAL 120 DAY),'스키마 이관',            DATE_SUB(NOW(6), INTERVAL 118 DAY),NOW(6), NOW(6)),
    (4, DATE_SUB(CURDATE(), INTERVAL 45 DAY), '컷오버',                 DATE_SUB(NOW(6), INTERVAL 44 DAY), NOW(6), NOW(6));


-- =============================================================================
-- 5) tasks  (49건) — 담당자는 해당 프로젝트의 ACTIVE 멤버.
--   completed_at NULL = 미완료 / 값 있으면 완료.
--   마감일을 오늘 기준으로 흩어 놓아, 어느 날 실행해도 아래가 모두 나옵니다.
--     · 과거 + 미완료  → 지연(overdue) · 프로젝트 보드의 carry-over
--     · 최근 완료       → 홈 조회의 "완료 3일 이내"
--     · 오늘/근미래     → 이번 주 보드
-- =============================================================================
INSERT INTO tasks
    (project_id, assignee_id, content, completed_at, due_date, created_at, modified_at)
VALUES
    -- P1 (AI 검색 고도화)
    (1, 1, '스프린트 리뷰 안건 취합',  NULL,                                  DATE_ADD(CURDATE(), INTERVAL 1 DAY),  NOW(6), NOW(6)),
    (1, 1, '검색 로드맵 문서화',       NULL,                                  DATE_ADD(CURDATE(), INTERVAL 4 DAY),  NOW(6), NOW(6)),
    (1, 1, '분기 예산 재조정',         DATE_SUB(NOW(6), INTERVAL 1 DAY),      DATE_SUB(CURDATE(), INTERVAL 1 DAY),  NOW(6), NOW(6)),
    (1, 2, '검색 API 인덱싱 개선',     DATE_SUB(NOW(6), INTERVAL 2 DAY),      DATE_SUB(CURDATE(), INTERVAL 2 DAY),  NOW(6), NOW(6)),
    (1, 2, '랭킹 알고리즘 튜닝',       NULL,                                  DATE_ADD(CURDATE(), INTERVAL 2 DAY),  NOW(6), NOW(6)),
    (1, 2, '캐시 레이어 도입',         NULL,                                  DATE_SUB(CURDATE(), INTERVAL 9 DAY),  NOW(6), NOW(6)),  -- 지연
    (1, 2, '벤치마크 리포트 작성',     DATE_SUB(NOW(6), INTERVAL 8 DAY),      DATE_SUB(CURDATE(), INTERVAL 8 DAY),  NOW(6), NOW(6)),
    (1, 3, '쿼리 파서 리팩터링',       NULL,                                  CURDATE(),                            NOW(6), NOW(6)),
    (1, 3, '로그 수집 배치',           DATE_SUB(NOW(6), INTERVAL 3 DAY),      DATE_SUB(CURDATE(), INTERVAL 3 DAY),  NOW(6), NOW(6)),
    (1, 3, '색인 재구축 스크립트',     NULL,                                  DATE_ADD(CURDATE(), INTERVAL 6 DAY),  NOW(6), NOW(6)),
    (1, 4, '검색 결과 UI 개선',        NULL,                                  DATE_ADD(CURDATE(), INTERVAL 1 DAY),  NOW(6), NOW(6)),
    (1, 4, '자동완성 컴포넌트',        NULL,                                  DATE_ADD(CURDATE(), INTERVAL 5 DAY),  NOW(6), NOW(6)),
    (1, 4, '접근성 점검',              NULL,                                  DATE_SUB(CURDATE(), INTERVAL 12 DAY), NOW(6), NOW(6)),  -- 지연
    (1, 6, '임베딩 파이프라인 구축',   NULL,                                  DATE_ADD(CURDATE(), INTERVAL 3 DAY),  NOW(6), NOW(6)),
    (1, 6, '데이터 라벨링 QA',         DATE_SUB(NOW(6), INTERVAL 4 DAY),      DATE_SUB(CURDATE(), INTERVAL 4 DAY),  NOW(6), NOW(6)),
    (1, 6, '피처 스토어 스키마',       NULL,                                  DATE_ADD(CURDATE(), INTERVAL 10 DAY), NOW(6), NOW(6)),
    -- P2 (사내 그룹웨어 리뉴얼)
    (2, 1, '그룹웨어 요구사항 정리',   DATE_SUB(NOW(6), INTERVAL 2 DAY),      DATE_SUB(CURDATE(), INTERVAL 2 DAY),  NOW(6), NOW(6)),
    (2, 1, '마일스톤 재조정',          NULL,                                  DATE_ADD(CURDATE(), INTERVAL 4 DAY),  NOW(6), NOW(6)),
    (2, 4, '디자인 시스템 토큰화',     NULL,                                  DATE_ADD(CURDATE(), INTERVAL 1 DAY),  NOW(6), NOW(6)),
    (2, 4, '공통 레이아웃 마크업',     DATE_SUB(NOW(6), INTERVAL 1 DAY),      DATE_SUB(CURDATE(), INTERVAL 3 DAY),  NOW(6), NOW(6)),
    (2, 4, '반응형 QA',                NULL,                                  DATE_SUB(CURDATE(), INTERVAL 7 DAY),  NOW(6), NOW(6)),  -- 지연
    (2, 5, '정보구조(IA) 설계',        NULL,                                  CURDATE(),                            NOW(6), NOW(6)),
    (2, 5, '사용자 시나리오 작성',     NULL,                                  DATE_SUB(CURDATE(), INTERVAL 6 DAY),  NOW(6), NOW(6)),  -- 지연
    (2, 5, '릴리즈 노트 템플릿',       NULL,                                  DATE_ADD(CURDATE(), INTERVAL 4 DAY),  NOW(6), NOW(6)),
    (2, 7, '알림 센터 UI',             NULL,                                  DATE_ADD(CURDATE(), INTERVAL 5 DAY),  NOW(6), NOW(6)),
    (2, 7, '다크모드 대응',            NULL,                                  DATE_SUB(CURDATE(), INTERVAL 13 DAY), NOW(6), NOW(6)),  -- 지연
    (2, 7, '접근성 개선',              NULL,                                  DATE_ADD(CURDATE(), INTERVAL 8 DAY),  NOW(6), NOW(6)),
    (2, 8, 'CI 파이프라인 구성',       DATE_SUB(NOW(6), INTERVAL 2 DAY),      DATE_SUB(CURDATE(), INTERVAL 2 DAY),  NOW(6), NOW(6)),
    (2, 8, '스테이징 인프라 세팅',     NULL,                                  CURDATE(),                            NOW(6), NOW(6)),
    (2, 8, '모니터링 대시보드',        NULL,                                  DATE_ADD(CURDATE(), INTERVAL 9 DAY),  NOW(6), NOW(6)),
    -- P3 (데이터 파이프라인 구축 — 보류 상태)
    (3, 5, '수집 요건 정의',           DATE_SUB(NOW(6), INTERVAL 30 DAY),     DATE_SUB(CURDATE(), INTERVAL 30 DAY), NOW(6), NOW(6)),
    (3, 2, '커넥터 개발',              NULL,                                  DATE_ADD(CURDATE(), INTERVAL 3 DAY),  NOW(6), NOW(6)),
    (3, 2, '스키마 마이그레이션',      NULL,                                  DATE_SUB(CURDATE(), INTERVAL 10 DAY), NOW(6), NOW(6)),  -- 지연
    (3, 2, '증분 적재 PoC',            NULL,                                  DATE_ADD(CURDATE(), INTERVAL 7 DAY),  NOW(6), NOW(6)),
    (3, 6, '적재 배치 설계',           NULL,                                  CURDATE(),                            NOW(6), NOW(6)),
    (3, 6, '데이터 품질 룰',           NULL,                                  DATE_ADD(CURDATE(), INTERVAL 12 DAY), NOW(6), NOW(6)),
    (3, 6, '파티셔닝 전략',            DATE_SUB(NOW(6), INTERVAL 15 DAY),     DATE_SUB(CURDATE(), INTERVAL 15 DAY), NOW(6), NOW(6)),
    -- P4 (레거시 마이그레이션 — 완료 프로젝트, 전부 과거)
    (4, 1, '이관 계획 수립',           DATE_SUB(NOW(6), INTERVAL 170 DAY),    DATE_SUB(CURDATE(), INTERVAL 170 DAY), NOW(6), NOW(6)),
    (4, 2, '스키마 이관 스크립트',     DATE_SUB(NOW(6), INTERVAL 120 DAY),    DATE_SUB(CURDATE(), INTERVAL 120 DAY), NOW(6), NOW(6)),
    (4, 2, '데이터 검증',              DATE_SUB(NOW(6), INTERVAL 90 DAY),     DATE_SUB(CURDATE(), INTERVAL 90 DAY),  NOW(6), NOW(6)),
    (4, 4, '레거시 화면 대체',         DATE_SUB(NOW(6), INTERVAL 60 DAY),     DATE_SUB(CURDATE(), INTERVAL 60 DAY),  NOW(6), NOW(6)),
    (4, 1, '컷오버 리허설',            DATE_SUB(NOW(6), INTERVAL 40 DAY),     DATE_SUB(CURDATE(), INTERVAL 40 DAY),  NOW(6), NOW(6)),
    -- 개인 할 일 (project_id NULL — 프로젝트 검증을 건너뜁니다)
    (NULL, 1, '주간 업무 보고 작성',   NULL,                                  DATE_ADD(CURDATE(), INTERVAL 3 DAY),  NOW(6), NOW(6)),
    (NULL, 2, '기술 블로그 초안',      NULL,                                  DATE_ADD(CURDATE(), INTERVAL 5 DAY),  NOW(6), NOW(6)),
    (NULL, 2, '도서 DDIA 5장 정리',    NULL,                                  DATE_SUB(CURDATE(), INTERVAL 5 DAY),  NOW(6), NOW(6)),  -- 지연(개인)
    (NULL, 3, '사내 교육 수강',        DATE_SUB(NOW(6), INTERVAL 1 DAY),      DATE_ADD(CURDATE(), INTERVAL 2 DAY),  NOW(6), NOW(6)),
    (NULL, 4, '포트폴리오 정리',       NULL,                                  DATE_ADD(CURDATE(), INTERVAL 11 DAY), NOW(6), NOW(6)),
    (NULL, 5, '경비 정산',             DATE_SUB(NOW(6), INTERVAL 2 DAY),      DATE_SUB(CURDATE(), INTERVAL 2 DAY),  NOW(6), NOW(6)),
    (NULL, 6, '자격증 신청',           NULL,                                  CURDATE(),                            NOW(6), NOW(6)),
    (NULL, 7, '건강검진 예약',         NULL,                                  DATE_ADD(CURDATE(), INTERVAL 14 DAY), NOW(6), NOW(6)),
    (NULL, 8, '온콜 인수인계 문서',    NULL,                                  DATE_SUB(CURDATE(), INTERVAL 4 DAY),  NOW(6), NOW(6));  -- 지연(개인)


-- =============================================================================
-- 6) project_posts  (프로젝트 게시판)
-- =============================================================================
INSERT INTO project_posts
    (project_id, author_id, title, content, created_at, modified_at)
VALUES
    (1, 1, '킥오프 회의록 공유',      '검색 고도화 프로젝트 킥오프 내용 정리했습니다. 확인 부탁드려요.', DATE_SUB(NOW(6), INTERVAL 55 DAY), DATE_SUB(NOW(6), INTERVAL 55 DAY)),
    (1, 2, '인덱싱 개선 논의',        '역색인 구조를 이렇게 바꾸면 어떨지 의견 주세요.',                DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 20 DAY)),
    (1, 6, '임베딩 모델 비교표',      '후보 모델 3종의 성능·비용 비교표입니다.',                        DATE_SUB(NOW(6), INTERVAL 6 DAY),  DATE_SUB(NOW(6), INTERVAL 6 DAY)),
    (2, 1, '리뉴얼 범위 공지',        '이번 스프린트 리뉴얼 범위와 우선순위 공유합니다.',              DATE_SUB(NOW(6), INTERVAL 25 DAY), DATE_SUB(NOW(6), INTERVAL 25 DAY)),
    (2, 8, '배포 파이프라인 안내',    'CI/CD 파이프라인 사용법 문서 링크 첨부합니다.',                 DATE_SUB(NOW(6), INTERVAL 10 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY)),
    (2, 4, '디자인 시스템 리뷰 요청', '토큰화한 디자인 시스템 리뷰 부탁드립니다.',                     DATE_SUB(NOW(6), INTERVAL 2 DAY),  DATE_SUB(NOW(6), INTERVAL 2 DAY)),
    (3, 5, '수집 대상 확정',          '1차 수집 대상 소스 목록 확정했습니다.',                         DATE_SUB(NOW(6), INTERVAL 70 DAY), DATE_SUB(NOW(6), INTERVAL 70 DAY));


-- =============================================================================
-- 7) meetings  (회의록, document_no UNIQUE)
--   MTG-2026-005 는 소프트 삭제 상태 — @SQLRestriction("deleted_at IS NULL") 로 목록·상세에서 빠져야 합니다.
-- =============================================================================
INSERT INTO meetings
    (project_id, document_no, title, author_id, meeting_date, location, purpose, content, follow_up, recording, deleted_at, created_at, modified_at)
VALUES
    (1,    'MTG-2026-001', '검색 고도화 킥오프',   1, DATE_SUB(CURDATE(), INTERVAL 55 DAY), '본사 3층 회의실 A', '프로젝트 범위·일정 합의', '범위, 역할, 마일스톤 확정',   '주간 스프린트 리뷰 운영', 'recordings/mtg-2026-001.mp4', NULL, NOW(6), NOW(6)),
    (2,    'MTG-2026-002', '그룹웨어 리뉴얼 착수', 1, DATE_SUB(CURDATE(), INTERVAL 25 DAY), '본사 5층 회의실 B', '리뉴얼 착수 및 요구사항', '요구사항 우선순위 정리',     '디자인 시스템 선행',      NULL,                          NULL, NOW(6), NOW(6)),
    (NULL, 'MTG-2026-003', '전사 기획 정기회의',   5, DATE_SUB(CURDATE(), INTERVAL 14 DAY), '온라인(Zoom)',      '월간 기획 공유',           '부서별 진행상황 공유',       '차월 목표 수립',          'recordings/mtg-2026-003.mp4', NULL, NOW(6), NOW(6)),
    (1,    'MTG-2026-004', '검색 스프린트 리뷰',   2, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '본사 3층 회의실 A', '스프린트 결과 리뷰',       '인덱싱/랭킹 개선 데모',      '벤치마크 리포트 공유',    NULL,                          NULL, NOW(6), NOW(6)),
    (2,    'MTG-2026-005', '취소된 중간 점검',     1, DATE_SUB(CURDATE(), INTERVAL 20 DAY), '본사 5층 회의실 B', '중간 점검(취소)',         '일정 사유로 취소됨',         '재소집 예정',             NULL,   DATE_SUB(NOW(6), INTERVAL 18 DAY), NOW(6), NOW(6));


-- =============================================================================
-- 8) meeting_attendees  (회의별 WRITER 1명 + ATTENDEE, 이름·부서는 작성 시점 스냅샷)
-- =============================================================================
INSERT INTO meeting_attendees
    (meeting_id, user_id, attendee_name, attendee_department, role, created_at, modified_at)
VALUES
    (1, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6)),
    (1, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (1, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    (1, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    (2, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6)),
    (2, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    (2, 5, '정민준', 'PLANNING', 'ATTENDEE', NOW(6), NOW(6)),
    (2, 8, '임도현', 'DEVOPS',   'ATTENDEE', NOW(6), NOW(6)),
    (3, 5, '정민준', 'PLANNING', 'WRITER',   NOW(6), NOW(6)),
    (3, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6)),
    (4, 2, '이하늘', 'BACKEND',  'WRITER',   NOW(6), NOW(6)),
    (4, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (4, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6)),
    (5, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6));


-- =============================================================================
-- 9) schedules  (일정, id 1~8) — 7·8 은 휴가로 schedule_leaves 확장
--   type: MEETING 회의 / FIELDWORK 외근 / PERSONAL 개인 (휴가는 type이 아니라 leaves 존재로 구분)
-- =============================================================================
INSERT INTO schedules
    (user_id, title, start_at, end_at, all_day, type, created_at, modified_at)
VALUES
    (1, '주간 팀 미팅',         TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:00:00'), FALSE, 'MEETING',   NOW(6), NOW(6)),
    (1, '검색팀 스프린트 리뷰', TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:30:00'), FALSE, 'MEETING',   NOW(6), NOW(6)),
    (2, '기술 세미나',          TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '17:00:00'), FALSE, 'MEETING',   NOW(6), NOW(6)),
    (4, '디자인 리뷰',          TIMESTAMP(CURDATE(), '11:00:00'),                           TIMESTAMP(CURDATE(), '12:00:00'),                           FALSE, 'MEETING',   NOW(6), NOW(6)),
    (5, '전사 워크숍',          TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '00:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '23:59:59'), TRUE,  'MEETING',   NOW(6), NOW(6)),
    (8, '배포 점검',            TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '20:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '22:00:00'), FALSE, 'FIELDWORK', NOW(6), NOW(6)),
    (3, '연차 휴가',            TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '00:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '23:59:59'), TRUE,  'PERSONAL',  NOW(6), NOW(6)),
    (6, '공가',                 TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '23:59:59'), TRUE,  'PERSONAL',  NOW(6), NOW(6));


-- =============================================================================
-- 10) schedule_leaves  (휴가 상세, schedule_id 7·8 과 1:1)
-- =============================================================================
INSERT INTO schedule_leaves
    (schedule_id, leave_type, reason, days, created_at, modified_at)
VALUES
    (7, 'ANNUAL', '개인 연차', 1, NOW(6), NOW(6)),
    (8, 'EXCUSED', '몸살',    1, NOW(6), NOW(6));


-- =============================================================================
-- 11) schedule_participants  (일정별 작성자 is_writer=TRUE + 참가자 FALSE)
-- =============================================================================
INSERT INTO schedule_participants
    (schedule_id, user_id, is_writer, created_at, modified_at)
VALUES
    (1, 1, TRUE,  NOW(6), NOW(6)),
    (1, 2, FALSE, NOW(6), NOW(6)),
    (1, 4, FALSE, NOW(6), NOW(6)),
    (1, 6, FALSE, NOW(6), NOW(6)),
    (2, 1, TRUE,  NOW(6), NOW(6)),
    (2, 2, FALSE, NOW(6), NOW(6)),
    (2, 3, FALSE, NOW(6), NOW(6)),
    (3, 2, TRUE,  NOW(6), NOW(6)),
    (3, 3, FALSE, NOW(6), NOW(6)),
    (4, 4, TRUE,  NOW(6), NOW(6)),
    (4, 7, FALSE, NOW(6), NOW(6)),
    (5, 5, TRUE,  NOW(6), NOW(6)),
    (5, 1, FALSE, NOW(6), NOW(6)),
    (5, 8, FALSE, NOW(6), NOW(6)),
    (6, 8, TRUE,  NOW(6), NOW(6)),
    (7, 3, TRUE,  NOW(6), NOW(6)),
    (8, 6, TRUE,  NOW(6), NOW(6));


-- =============================================================================
-- 12) leave_balances  (연차 현황 — 올해 기준으로 부여)
-- =============================================================================
INSERT INTO leave_balances
    (user_id, year, granted_days, created_at, modified_at)
VALUES
    (1,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (2,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (3,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (4,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (5,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (6,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (7,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (8,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (9,  YEAR(CURDATE()), 15, NOW(6), NOW(6)),
    (10, YEAR(CURDATE()), 15, NOW(6), NOW(6));


-- =============================================================================
-- 13) expenses  (26건)
--   spender 는 해당 프로젝트의 ACTIVE 멤버, expense_date 는 프로젝트 기간 안(등록 API 검증과 동일).
--   지출 구분은 저장하지 않고 조회 시 파생: expense_date <= 오늘 → EXECUTED(사용), 이후 → PLANNED(사용 예정).
--   deleted_at 이 채워진 2건은 소프트 삭제 확인용 — 모든 집계에서 빠져야 합니다.
--   카테고리·부서를 섞어 예산 현황의 '항목별 / 부서별' 집계를 확인할 수 있게 구성했습니다.
-- =============================================================================
INSERT INTO expenses
    (project_id, spender_id, expense_date, category, merchant, purpose, amount, deleted_at, deleted_by, created_at, modified_at)
VALUES
    -- P1 AI 검색 고도화
    (1, 1, DATE_SUB(CURDATE(), INTERVAL 55 DAY), 'TRANSPORT',     '코레일',        '킥오프 출장',            48000,   NULL, NULL, NOW(6), NOW(6)),
    (1, 2, DATE_SUB(CURDATE(), INTERVAL 46 DAY), 'SOFTWARE',      'JetBrains',     '개발 IDE 라이선스',      720000,  NULL, NULL, NOW(6), NOW(6)),
    (1, 6, DATE_SUB(CURDATE(), INTERVAL 38 DAY), 'INFRA',         'AWS',           'GPU 인스턴스 비용',      1850000, NULL, NULL, NOW(6), NOW(6)),
    (1, 4, DATE_SUB(CURDATE(), INTERVAL 26 DAY), 'SOFTWARE',      'Figma',         '디자인 협업 툴',         180000,  NULL, NULL, NOW(6), NOW(6)),
    (1, 3, DATE_SUB(CURDATE(), INTERVAL 18 DAY), 'MEAL',          '본죽',          '야근 식대',              32000,   NULL, NULL, NOW(6), NOW(6)),
    (1, 2, DATE_SUB(CURDATE(), INTERVAL 13 DAY), 'EDUCATION',     '패스트캠퍼스',  '검색엔진 세미나',        350000,  NULL, NULL, NOW(6), NOW(6)),
    (1, 1, DATE_SUB(CURDATE(), INTERVAL 6 DAY),  'MEAL',          '스타벅스',      '스프린트 리뷰 다과',     46000,   NULL, NULL, NOW(6), NOW(6)),
    (1, 6, DATE_ADD(CURDATE(), INTERVAL 8 DAY),  'INFRA',         'AWS',           '임베딩 서버 증설',       2400000, NULL, NULL, NOW(6), NOW(6)),  -- 사용 예정
    (1, 4, DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'OFFICE_SUPPLY', '오피스디포',    '모니터 암',              220000,  NULL, NULL, NOW(6), NOW(6)),  -- 사용 예정
    (1, 3, DATE_SUB(CURDATE(), INTERVAL 20 DAY), 'MEAL',          '김밥천국',      '오기입 - 개인 식사',     9000,    DATE_SUB(NOW(6), INTERVAL 19 DAY), 3, NOW(6), NOW(6)),  -- 소프트 삭제
    -- P2 사내 그룹웨어 리뉴얼
    (2, 1, DATE_SUB(CURDATE(), INTERVAL 25 DAY), 'MEAL',          '한식당 명가',   '착수 회의 식대',         96000,   NULL, NULL, NOW(6), NOW(6)),
    (2, 8, DATE_SUB(CURDATE(), INTERVAL 20 DAY), 'INFRA',         'GitHub',        'Actions 추가 러너',      480000,  NULL, NULL, NOW(6), NOW(6)),
    (2, 4, DATE_SUB(CURDATE(), INTERVAL 14 DAY), 'SOFTWARE',      'Figma',         '디자인 시스템 플랜',     360000,  NULL, NULL, NOW(6), NOW(6)),
    (2, 5, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 'OFFICE_SUPPLY', '오피스디포',    '워크숍 문구류',          85000,   NULL, NULL, NOW(6), NOW(6)),
    -- 같은 날 같은 사용처의 정상 지출 2건 (내용 기준으로 중복을 막으면 안 되는 사례)
    (2, 7, DATE_SUB(CURDATE(), INTERVAL 7 DAY),  'TRANSPORT',     '카카오T',       '고객사 미팅 이동',       18500,   NULL, NULL, NOW(6), NOW(6)),
    (2, 8, DATE_SUB(CURDATE(), INTERVAL 7 DAY),  'TRANSPORT',     '카카오T',       '심야 귀가',              23000,   NULL, NULL, NOW(6), NOW(6)),
    (2, 1, DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'OUTSOURCING',   '디자인스튜디오','일러스트 외주',          3200000, NULL, NULL, NOW(6), NOW(6)),  -- 사용 예정
    (2, 5, DATE_ADD(CURDATE(), INTERVAL 35 DAY), 'EDUCATION',     '인프런',        'UX 교육',                240000,  NULL, NULL, NOW(6), NOW(6)),  -- 사용 예정
    (2, 4, DATE_SUB(CURDATE(), INTERVAL 12 DAY), 'SOFTWARE',      'Adobe',         '중복 결제 오기입',       290000,  DATE_SUB(NOW(6), INTERVAL 11 DAY), 4, NOW(6), NOW(6)),  -- 소프트 삭제
    -- P3 데이터 파이프라인 구축 (보류)
    (3, 5, DATE_SUB(CURDATE(), INTERVAL 70 DAY), 'TRANSPORT',     '코레일',        '수집 요건 협의 출장',    56000,   NULL, NULL, NOW(6), NOW(6)),
    (3, 6, DATE_SUB(CURDATE(), INTERVAL 45 DAY), 'INFRA',         'GCP',           'BigQuery 쿼리 비용',     940000,  NULL, NULL, NOW(6), NOW(6)),
    (3, 2, DATE_SUB(CURDATE(), INTERVAL 22 DAY), 'SOFTWARE',      'Airbyte',       '커넥터 라이선스',        550000,  NULL, NULL, NOW(6), NOW(6)),
    (3, 6, DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'LABOR',         '외부 컨설턴트', '데이터 품질 자문',       1500000, NULL, NULL, NOW(6), NOW(6)),  -- 사용 예정
    -- P4 레거시 마이그레이션 (완료 프로젝트 — 지출은 프로젝트 상태와 무관하게 기록 가능)
    (4, 1, DATE_SUB(CURDATE(), INTERVAL 180 DAY),'OUTSOURCING',   '클라우드파트너스', '이관 컨설팅',         5000000, NULL, NULL, NOW(6), NOW(6)),
    (4, 2, DATE_SUB(CURDATE(), INTERVAL 110 DAY),'INFRA',         'AWS',           '이관 기간 이중 운영',    3200000, NULL, NULL, NOW(6), NOW(6)),
    (4, 4, DATE_SUB(CURDATE(), INTERVAL 45 DAY), 'MEAL',          '고깃집 대성',   '컷오버 회식',            340000,  NULL, NULL, NOW(6), NOW(6));


-- =============================================================================
-- 14) idempotency_keys  (멱등 키 — 생성 API 중복 요청 방어 기록)
--   실제 요청으로 쌓이는 운영 데이터라 형태 확인용 소량만 넣습니다.
--   request_hash 는 실제 SHA-256 값이 아닌 더미(64자 hex)입니다. 같은 키로 재요청하면 해시 불일치로 409가 나므로
--   테스트할 때는 여기 없는 새 UUID를 사용하세요.
--   3번째 행은 보관 기간(24시간)이 지나 정리 배치의 삭제 대상입니다.
-- =============================================================================
INSERT INTO idempotency_keys
    (idempotency_key, endpoint, user_id, request_hash, resource_id, created_at, modified_at)
VALUES
    ('11111111-1111-4111-8111-111111111111', 'POST /api/v1/projects', 1,
     '0000000000000000000000000000000000000000000000000000000000000001', 1, NOW(6), NOW(6)),
    ('22222222-2222-4222-8222-222222222222', 'POST /api/v1/projects/{projectId}/expenses', 2,
     '0000000000000000000000000000000000000000000000000000000000000002', 2, NOW(6), NOW(6)),
    ('33333333-3333-4333-8333-333333333333', 'POST /api/v1/projects/{projectId}/expenses', 8,
     '0000000000000000000000000000000000000000000000000000000000000003', 12,
     DATE_SUB(NOW(6), INTERVAL 3 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY));
