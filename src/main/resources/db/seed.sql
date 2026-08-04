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
-- 6) project_posts  (프로젝트 게시판, 23건 중 활성 21건)
--   목록 정렬은 created_at DESC, id DESC — 같은 시각(-1일) 2건으로 id 보조 정렬을 확인합니다.
--   P1 은 활성 12건이라 기본 size=10 에서 2페이지가 나옵니다(페이징 확인용).
--   '리뷰' 키워드(제목 부분 검색)와 priority(HIGH/MID/LOW) 필터를 섞어 두었고,
--   deleted_at 이 채워진 2건은 소프트 삭제 확인용 — @SQLRestriction 으로 목록·상세에서 빠져야 합니다.
--   작성자는 모두 해당 프로젝트의 ACTIVE 멤버, P4(완료)의 글은 완료 전에 작성된 기록입니다.
-- =============================================================================
INSERT INTO project_posts
    (project_id, author_id, title, priority, content, deleted_at, created_at, modified_at)
    (project_id, author_id, title, content, created_at, modified_at)
VALUES
    -- P1 AI 검색 고도화 (활성 12건 + 삭제 1건)
    (1, 1, '킥오프 결과 공유',              'HIGH', '킥오프에서 확정한 범위와 역할 분담, 스프린트 운영 방식을 정리했습니다. 회의록과 함께 확인해 주세요.',       NULL, DATE_SUB(NOW(6), INTERVAL 55 DAY), DATE_SUB(NOW(6), INTERVAL 55 DAY)),
    (1, 2, '검색 인덱싱 구조 개선 제안',    'MID',  '현재 역색인 구조의 병목을 정리하고 세그먼트 병합 주기 조정안을 제안합니다. 의견 부탁드립니다.',             NULL, DATE_SUB(NOW(6), INTERVAL 46 DAY), DATE_SUB(NOW(6), INTERVAL 40 DAY)),  -- 수정 이력 있는 글
    (1, 3, '쿼리 파서 리팩터링 계획',       'MID',  '파서 모듈을 토크나이저와 분리하는 리팩터링 계획입니다. 다음 스프린트에 반영 예정입니다.',                   NULL, DATE_SUB(NOW(6), INTERVAL 38 DAY), DATE_SUB(NOW(6), INTERVAL 38 DAY)),
    (1, 6, '임베딩 모델 후보 비교',         'LOW',  '후보 모델 3종의 정확도·지연시간·비용 비교표입니다. 종합적으로는 B안이 우세합니다.',                         NULL, DATE_SUB(NOW(6), INTERVAL 31 DAY), DATE_SUB(NOW(6), INTERVAL 31 DAY)),
    (1, 1, '스프린트 운영 규칙 공지',       'HIGH', '데일리 스탠드업은 10시, 스프린트 리뷰는 격주 금요일로 고정합니다. 부재 시 스레드로 공유해 주세요.',         NULL, DATE_SUB(NOW(6), INTERVAL 24 DAY), DATE_SUB(NOW(6), INTERVAL 24 DAY)),
    (1, 4, '검색 결과 UI 시안 리뷰 요청',   'MID',  '검색 결과 카드형/리스트형 시안 2종입니다. 금요일까지 코멘트 부탁드립니다.',                                 NULL, DATE_SUB(NOW(6), INTERVAL 17 DAY), DATE_SUB(NOW(6), INTERVAL 17 DAY)),
    (1, 2, '캐시 레이어 도입 검토',         'LOW',  '인기 쿼리 상위 5%가 전체 트래픽의 40%를 차지합니다. 결과 캐시 도입 시 예상 절감 효과를 정리했습니다.',     NULL, DATE_SUB(NOW(6), INTERVAL 11 DAY), DATE_SUB(NOW(6), INTERVAL 11 DAY)),
    (1, 6, '데이터 라벨링 가이드 공유',     'LOW',  '관련성 라벨링 기준(4단계)과 예시 모음입니다. 라벨러 온보딩에 활용해 주세요.',                               NULL, DATE_SUB(NOW(6), INTERVAL 8 DAY),  DATE_SUB(NOW(6), INTERVAL 8 DAY)),
    (1, 3, '색인 재구축 스크립트 사용법',   'MID',  '전체 재색인 스크립트 옵션과 주의사항(피크 시간대 실행 금지)을 정리했습니다.',                               NULL, DATE_SUB(NOW(6), INTERVAL 6 DAY),  DATE_SUB(NOW(6), INTERVAL 6 DAY)),
    (1, 1, '중간 점검 일정 공지',           'HIGH', '다음 주 화요일 14시에 중간 점검을 진행합니다. 파트별 진행 현황을 준비해 주세요.',                           NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY),  DATE_SUB(NOW(6), INTERVAL 2 DAY)),
    -- 아래 2건은 created_at 이 같아 id DESC 보조 정렬을 확인합니다 (같은 INSERT 문이라 NOW(6)가 동일)
    (1, 4, '접근성 점검 결과 공유',         'MID',  '스크린리더 호환성 점검 결과 12건의 이슈를 발견했습니다. 심각도별로 정리했습니다.',                          NULL, DATE_SUB(NOW(6), INTERVAL 1 DAY),  DATE_SUB(NOW(6), INTERVAL 1 DAY)),
    (1, 2, '벤치마크 리포트 초안 리뷰 요청','HIGH', '스프린트 2까지의 성능 개선 수치를 정리한 리포트 초안입니다. 수치 검증 부탁드립니다.',                       NULL, DATE_SUB(NOW(6), INTERVAL 1 DAY),  DATE_SUB(NOW(6), INTERVAL 1 DAY)),
    (1, 3, '잘못 올린 글',                  'LOW',  '다른 프로젝트에 올릴 글을 잘못 게시해 삭제했습니다.',                                                       DATE_SUB(NOW(6), INTERVAL 29 DAY), DATE_SUB(NOW(6), INTERVAL 30 DAY), DATE_SUB(NOW(6), INTERVAL 30 DAY)),  -- 소프트 삭제
    -- P2 사내 그룹웨어 리뉴얼 (활성 6건 + 삭제 1건)
    (2, 1, '리뉴얼 범위 및 우선순위 공지',  'HIGH', '이번 리뉴얼 1차 범위를 메일·결재·게시판으로 확정했습니다. 상세 우선순위는 본문을 참고해 주세요.',           NULL, DATE_SUB(NOW(6), INTERVAL 25 DAY), DATE_SUB(NOW(6), INTERVAL 25 DAY)),
    (2, 5, '정보구조(IA) 초안 공유',        'MID',  '메뉴 구조와 화면 흐름 초안입니다. 부서별 사용 시나리오 관점에서 검토 부탁드립니다.',                        NULL, DATE_SUB(NOW(6), INTERVAL 18 DAY), DATE_SUB(NOW(6), INTERVAL 18 DAY)),
    (2, 4, '디자인 토큰 명명 규칙 제안',    'MID',  '컬러·타이포·간격 토큰의 명명 규칙 제안입니다. 확정되면 컴포넌트 작업을 시작합니다.',                        NULL, DATE_SUB(NOW(6), INTERVAL 14 DAY), DATE_SUB(NOW(6), INTERVAL 12 DAY)),  -- 수정 이력 있는 글
    (2, 8, 'CI 파이프라인 사용 안내',       'LOW',  'PR 생성 시 자동 빌드·테스트가 돌아갑니다. 실패 시 재실행 방법과 캐시 초기화 절차를 정리했습니다.',          NULL, DATE_SUB(NOW(6), INTERVAL 9 DAY),  DATE_SUB(NOW(6), INTERVAL 9 DAY)),
    (2, 7, '알림 센터 화면 시안 리뷰 요청', 'MID',  '알림 센터 목록/설정 화면 시안입니다. 다크모드 대응안도 포함했습니다.',                                      NULL, DATE_SUB(NOW(6), INTERVAL 4 DAY),  DATE_SUB(NOW(6), INTERVAL 4 DAY)),
    (2, 1, '주간 진행 현황 공유',           'HIGH', '이번 주 파트별 진행 현황과 다음 주 계획입니다. 지연 항목은 따로 표시했습니다.',                             NULL, DATE_SUB(NOW(6), INTERVAL 1 DAY),  DATE_SUB(NOW(6), INTERVAL 1 DAY)),
    (2, 4, '중복 작성 글',                  'MID',  '같은 내용이 두 번 게시되어 삭제했습니다.',                                                                  DATE_SUB(NOW(6), INTERVAL 13 DAY), DATE_SUB(NOW(6), INTERVAL 13 DAY), DATE_SUB(NOW(6), INTERVAL 13 DAY)),  -- 소프트 삭제
    -- P3 데이터 파이프라인 구축 (보류 — 기존 글 조회는 가능)
    (3, 5, '수집 대상 소스 확정 공지',      'HIGH', '1차 수집 대상 8개 소스를 확정했습니다. 소스별 담당자는 본문 표를 참고해 주세요.',                           NULL, DATE_SUB(NOW(6), INTERVAL 70 DAY), DATE_SUB(NOW(6), INTERVAL 70 DAY)),
    (3, 6, 'BigQuery 비용 절감 아이디어',   'LOW',  '파티셔닝과 구체화 뷰 적용 시 쿼리 비용을 30% 이상 줄일 수 있을 것으로 보입니다.',                           NULL, DATE_SUB(NOW(6), INTERVAL 44 DAY), DATE_SUB(NOW(6), INTERVAL 44 DAY)),
    -- P4 레거시 마이그레이션 (완료 — 새 글 작성은 차단되지만 기존 글은 조회 가능)
    (4, 1, '이관 완료 회고 정리',           'MID',  '컷오버 과정에서 잘한 점과 아쉬운 점을 회고로 정리했습니다. 다음 이관 프로젝트에 참고해 주세요.',             NULL, DATE_SUB(NOW(6), INTERVAL 35 DAY), DATE_SUB(NOW(6), INTERVAL 35 DAY));
    (1, 1, '킥오프 회의록 공유',      '검색 고도화 프로젝트 킥오프 내용 정리했습니다. 확인 부탁드려요.', DATE_SUB(NOW(6), INTERVAL 55 DAY), DATE_SUB(NOW(6), INTERVAL 55 DAY)),
    (1, 2, '인덱싱 개선 논의',        '역색인 구조를 이렇게 바꾸면 어떨지 의견 주세요.',                DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 20 DAY)),
    (1, 6, '임베딩 모델 비교표',      '후보 모델 3종의 성능·비용 비교표입니다.',                        DATE_SUB(NOW(6), INTERVAL 6 DAY),  DATE_SUB(NOW(6), INTERVAL 6 DAY)),
    (2, 1, '리뉴얼 범위 공지',        '이번 스프린트 리뉴얼 범위와 우선순위 공유합니다.',              DATE_SUB(NOW(6), INTERVAL 25 DAY), DATE_SUB(NOW(6), INTERVAL 25 DAY)),
    (2, 8, '배포 파이프라인 안내',    'CI/CD 파이프라인 사용법 문서 링크 첨부합니다.',                 DATE_SUB(NOW(6), INTERVAL 10 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY)),
    (2, 4, '디자인 시스템 리뷰 요청', '토큰화한 디자인 시스템 리뷰 부탁드립니다.',                     DATE_SUB(NOW(6), INTERVAL 2 DAY),  DATE_SUB(NOW(6), INTERVAL 2 DAY)),
    (3, 5, '수집 대상 확정',          '1차 수집 대상 소스 목록 확정했습니다.',                         DATE_SUB(NOW(6), INTERVAL 70 DAY), DATE_SUB(NOW(6), INTERVAL 70 DAY));


-- =============================================================================
-- 7) meetings  (회의록 13건 중 활성 12건, id 1~13)
--   document_no 는 서비스 생성 규칙과 같은 MTG-{회의일자}-{id} 형식으로 만듭니다.
--   회의 일자가 상대값이라 날짜 부분은 CONCAT 으로 계산하고, id 부분은 "빈 테이블에 순서대로 삽입 → id 1~13" 전제로 고정합니다.
--   회의 일자는 모두 각 프로젝트 기간(startDate~targetDate) 안 — 생성/수정 API 검증과 같은 조건입니다.
--   목록 정렬은 meeting_date DESC, id DESC — 같은 날짜(-5일) 2건으로 보조 정렬을 확인합니다.
--   13번(취소된 중간 점검)은 소프트 삭제 — @SQLRestriction("deleted_at IS NULL") 로 목록·상세에서 빠져야 합니다.
-- =============================================================================
INSERT INTO meetings
    (project_id, document_no, title, author_id, meeting_date, location, purpose, content, follow_up, recording, deleted_at, created_at, modified_at)
VALUES
    -- P1 AI 검색 고도화 (id 1~6, 기간 -60 ~ +60)
    (1, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 55 DAY), '-1'),  '검색 고도화 킥오프',        1, DATE_SUB(CURDATE(), INTERVAL 55 DAY), '본사 3층 회의실 A',   '프로젝트 범위·일정·역할 합의',        '프로젝트 배경과 목표 지표(검색 CTR +15%)를 공유하고, 범위를 1차(랭킹 개선)와 2차(임베딩 도입)로 나눠 확정했다. 스프린트 주기는 2주로 합의.', '스프린트 보드 개설(김피엠), 벤치마크 데이터셋 준비(강지우)', 'recordings/mtg-p1-kickoff.mp4',   NULL, DATE_SUB(NOW(6), INTERVAL 55 DAY), DATE_SUB(NOW(6), INTERVAL 55 DAY)),
    (1, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 48 DAY), '-2'),  '검색 요구사항 정리 워크숍', 1, DATE_SUB(CURDATE(), INTERVAL 48 DAY), '본사 3층 회의실 A',   '검색 품질 요구사항 수집·우선순위화',  '부서별 검색 불만 사례 32건을 수집해 유형화했다. 동의어 처리와 오타 교정을 1순위, 문서 최신성 반영을 2순위로 결정.',                         '요구사항 명세서 초안 작성(박도윤)',                          NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 48 DAY), DATE_SUB(NOW(6), INTERVAL 48 DAY)),
    (1, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 40 DAY), '-3'),  '1차 스프린트 리뷰',         2, DATE_SUB(CURDATE(), INTERVAL 40 DAY), '온라인(Google Meet)', '스프린트 1 결과 공유',                '역색인 파라미터 튜닝으로 검색 지연시간을 평균 340ms에서 180ms로 단축했다. 동의어 사전 1차본을 적용하고 미커버 케이스는 백로그로 이동.',     '동의어 사전 보완(박도윤), 랭킹 A/B 실험 설계(강지우)',       'recordings/mtg-p1-sprint1.mp4',   NULL, DATE_SUB(NOW(6), INTERVAL 40 DAY), DATE_SUB(NOW(6), INTERVAL 40 DAY)),
    (1, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 20 DAY), '-4'),  '랭킹 알고리즘 개선 회의',   2, DATE_SUB(CURDATE(), INTERVAL 20 DAY), '본사 3층 회의실 A',   '임베딩 기반 랭킹 반영 비율 결정',     'BM25와 임베딩 유사도의 하이브리드 가중치를 실험 결과에 따라 0.7:0.3으로 확정했다. 재색인 주기는 일 1회로 결정.',                            'A/B 테스트 트래픽 10% 적용(강지우)',                         NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 20 DAY)),
    (1, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '-5'),  '2차 스프린트 리뷰',         2, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '본사 3층 회의실 A',   '스프린트 2 결과 공유·데모',           '하이브리드 랭킹 A/B 결과 CTR +9.2%를 확인했다. 검색 결과 UI 개선안 데모를 진행하고 피드백을 다음 스프린트에 반영하기로 했다.',              '벤치마크 리포트 전사 공유(이하늘)',                          NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 5 DAY),  DATE_SUB(NOW(6), INTERVAL 5 DAY)),
    (1, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '-6'),  '임베딩 파이프라인 점검',    6, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '온라인(Google Meet)', '배치 파이프라인 병목 점검',           '배치 임베딩 처리량이 목표(10만 건/시간) 대비 60% 수준이다. GPU 배치 크기 조정과 전처리 병렬화로 개선하기로 했다.',                          'GPU 인스턴스 증설 품의(강지우)',                             'recordings/mtg-p1-embedding.mp4', NULL, DATE_SUB(NOW(6), INTERVAL 5 DAY),  DATE_SUB(NOW(6), INTERVAL 5 DAY)),
    -- P2 사내 그룹웨어 리뉴얼 (id 7~9, 기간 -30 ~ +150)
    (2, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 25 DAY), '-7'),  '그룹웨어 리뉴얼 착수 회의', 1, DATE_SUB(CURDATE(), INTERVAL 25 DAY), '본사 5층 회의실 B',   '리뉴얼 배경 공유·요구사항 우선순위',  '레거시 그룹웨어의 페인포인트(느린 로딩, 모바일 미지원)를 공유하고 1차 범위를 메일·결재·게시판으로 확정했다.',                               '정보구조 초안 작성(정민준), 디자인 시스템 선행 착수(최서아)', 'recordings/mtg-p2-kickoff.mp4',   NULL, DATE_SUB(NOW(6), INTERVAL 25 DAY), DATE_SUB(NOW(6), INTERVAL 25 DAY)),
    (2, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 12 DAY), '-8'),  '디자인 시스템 리뷰',        4, DATE_SUB(CURDATE(), INTERVAL 12 DAY), '본사 5층 회의실 B',   '토큰·공통 컴포넌트 1차본 리뷰',       '컬러·타이포·간격 토큰 명명 규칙을 확정하고 공통 컴포넌트 12종의 우선 구현 순서를 정했다.',                                                  '토큰 문서화(윤하은), 다크모드 팔레트 검토(최서아)',          NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 12 DAY), DATE_SUB(NOW(6), INTERVAL 12 DAY)),
    (2, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '-9'),  '인프라 구성 협의',          8, DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '온라인(Zoom)',        '스테이징 환경·배포 파이프라인 협의',  '스테이징을 운영과 동일 스펙으로 구성하기로 하고, 배포는 GitHub Actions 기반 블루/그린 방식으로 결정했다.',                                  '스테이징 IaC 작성(임도현)',                                  NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 3 DAY),  DATE_SUB(NOW(6), INTERVAL 3 DAY)),
    -- P3 데이터 파이프라인 구축 (id 10~11, 기간 -90 ~ +30, 보류 — 기존 회의록 조회는 가능)
    (3, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 70 DAY), '-10'), '수집 요건 정의 회의',       5, DATE_SUB(CURDATE(), INTERVAL 70 DAY), '본사 4층 회의실 C',   '수집 대상 소스·주기 확정',            '1차 수집 대상 8개 소스와 수집 주기(일 배치 6종, 준실시간 2종)를 확정했다. 개인정보 포함 소스는 마스킹 후 적재하기로 했다.',                 '소스별 스키마 정의(강지우), 커넥터 PoC(이하늘)',             NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 70 DAY), DATE_SUB(NOW(6), INTERVAL 70 DAY)),
    (3, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 35 DAY), '-11'), '파이프라인 일시 보류 결정', 5, DATE_SUB(CURDATE(), INTERVAL 35 DAY), '온라인(Zoom)',        '전사 우선순위 조정에 따른 보류 협의', '전사 우선순위 조정으로 파이프라인 구축을 일시 보류하기로 했다. 재개 시점은 다음 분기 초로 잠정 합의.',                                      '진행 산출물 위키 정리(강지우)',                              'recordings/mtg-p3-hold.mp4',      NULL, DATE_SUB(NOW(6), INTERVAL 35 DAY), DATE_SUB(NOW(6), INTERVAL 35 DAY)),
    -- P4 레거시 마이그레이션 (id 12, 기간 -210 ~ -30, 완료 — 새 작성은 차단되지만 기존 회의록 조회는 가능)
    (4, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 45 DAY), '-12'), '컷오버 최종 점검',          1, DATE_SUB(CURDATE(), INTERVAL 45 DAY), '본사 5층 회의실 B',   '이관 컷오버 체크리스트 점검',         '컷오버 리허설 결과를 검토하고 롤백 기준(오류율 1% 초과)을 확정했다. 당일 비상 연락 체계를 공유했다.',                                       '컷오버 당일 상황실 운영(김피엠)',                            NULL,                              NULL, DATE_SUB(NOW(6), INTERVAL 45 DAY), DATE_SUB(NOW(6), INTERVAL 45 DAY)),
    -- 소프트 삭제 (id 13)
    (2, CONCAT('MTG-', DATE_SUB(CURDATE(), INTERVAL 10 DAY), '-13'), '취소된 중간 점검',          1, DATE_SUB(CURDATE(), INTERVAL 10 DAY), '본사 5층 회의실 B',   '중간 점검(작성 후 취소)',             '일정 사유로 회의가 취소되어 회의록을 삭제 처리했다.',                                                                                       '재소집 예정',                                                NULL,                              DATE_SUB(NOW(6), INTERVAL 8 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY));


-- =============================================================================
-- 8) meeting_attendees  (회의별 WRITER 1명 + ATTENDEE, 이름·부서는 작성 시점 스냅샷)
--   전원 해당 프로젝트의 ACTIVE 멤버(생성 API의 참석자 검증과 같은 조건)이고, 작성자는 ATTENDEE 로 중복 등록하지 않습니다.
--   목록의 attendeeName 검색은 정확 일치 — 예: '김피엠' 검색 시 역할과 무관하게 참석한 회의가 모두 나옵니다.
--   13번(소프트 삭제된 회의)의 행은 남아 있어도 회의 자체가 조회에서 빠지므로 노출되지 않습니다.
-- =============================================================================
INSERT INTO meeting_attendees
    (meeting_id, user_id, attendee_name, attendee_department, role, created_at, modified_at)
VALUES
    -- M1 검색 고도화 킥오프
    (1, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6)),
    (1, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (1, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    (1, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    -- M2 검색 요구사항 정리 워크숍
    (2, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6)),
    (2, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (2, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (2, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    -- M3 1차 스프린트 리뷰
    (3, 2, '이하늘', 'BACKEND',  'WRITER',   NOW(6), NOW(6)),
    (3, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6)),
    (3, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (3, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    -- M4 랭킹 알고리즘 개선 회의
    (4, 2, '이하늘', 'BACKEND',  'WRITER',   NOW(6), NOW(6)),
    (4, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (4, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    -- M5 2차 스프린트 리뷰
    (5, 2, '이하늘', 'BACKEND',  'WRITER',   NOW(6), NOW(6)),
    (5, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6)),
    (5, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (5, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    (5, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    -- M6 임베딩 파이프라인 점검
    (6, 6, '강지우', 'DATA',     'WRITER',   NOW(6), NOW(6)),
    (6, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (6, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    -- M7 그룹웨어 리뉴얼 착수 회의
    (7, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6)),
    (7, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    (7, 5, '정민준', 'PLANNING', 'ATTENDEE', NOW(6), NOW(6)),
    (7, 7, '윤하은', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    (7, 8, '임도현', 'DEVOPS',   'ATTENDEE', NOW(6), NOW(6)),
    -- M8 디자인 시스템 리뷰
    (8, 4, '최서아', 'FRONTEND', 'WRITER',   NOW(6), NOW(6)),
    (8, 5, '정민준', 'PLANNING', 'ATTENDEE', NOW(6), NOW(6)),
    (8, 7, '윤하은', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    -- M9 인프라 구성 협의
    (9, 8, '임도현', 'DEVOPS',   'WRITER',   NOW(6), NOW(6)),
    (9, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6)),
    (9, 7, '윤하은', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    -- M10 수집 요건 정의 회의
    (10, 5, '정민준', 'PLANNING', 'WRITER',   NOW(6), NOW(6)),
    (10, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (10, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    -- M11 파이프라인 일시 보류 결정
    (11, 5, '정민준', 'PLANNING', 'WRITER',   NOW(6), NOW(6)),
    (11, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (11, 6, '강지우', 'DATA',     'ATTENDEE', NOW(6), NOW(6)),
    -- M12 컷오버 최종 점검
    (12, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6)),
    (12, 2, '이하늘', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (12, 4, '최서아', 'FRONTEND', 'ATTENDEE', NOW(6), NOW(6)),
    -- M13 취소된 중간 점검 (소프트 삭제된 회의 — WRITER 행만 유지)
    (13, 1, '김피엠', 'PM',       'WRITER',   NOW(6), NOW(6));


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
