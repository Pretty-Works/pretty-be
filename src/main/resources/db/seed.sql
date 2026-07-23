-- PrettyWorks 통합 시드 데이터
-- 빈(방금 init.sql 로 생성된) DB 기준으로 한 번 실행 → 전 테이블에 데이터가 채워집니다.
-- id 는 AUTO_INCREMENT 라 "빈 테이블에 아래 순서대로" 넣으면 users 1~10, projects 1~5 … 로 부여됩니다.
-- 전 사용자 비밀번호: Test1234!  (BCrypt 해시)
--
-- 로드 (한글 안전, 자세한 절차는 같은 폴더 README.md):
--   docker cp src\main\resources\db\seed.sql <컨테이너>:/tmp/seed.sql
--   docker exec -i <컨테이너> sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test < /tmp/seed.sql"
-- ※ Get-Content ... | docker ... (PowerShell 파이프)와 docker compose cp 는 한글 깨짐/파일 누락으로 금지.
--
-- ※ init.sql(스키마)만 먼저 로드된 상태에서 실행하세요. FK 순서대로 삽입하므로 외래키 비활성화가 필요 없습니다.

SET NAMES utf8mb4;

-- 전원 공통 비밀번호 해시 (Test1234!)
SET @pw = '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC';


-- =============================================================================
-- 1) users  (id 1~10)
--   1 김피엠   PM        TEAM_LEADER  ACTIVE    | 6 강지우   DATA      STAFF        ACTIVE
--   2 이하늘   BACKEND   SENIOR       ACTIVE    | 7 윤하은   FRONTEND  STAFF        ACTIVE
--   3 박도윤   BACKEND   STAFF        ACTIVE    | 8 임도현   DEVOPS    SENIOR       ACTIVE
--   4 최서아   FRONTEND  SENIOR       ACTIVE    | 9 한퇴사   QA        STAFF        RESIGNED (비활성)
--   5 정민준   PLANNING  PART_LEADER  ACTIVE    |10 오휴직   SALES     STAFF        ON_LEAVE (비활성)
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
-- 2) projects  (id 1~5)
-- =============================================================================
INSERT INTO projects
    (name, status, start_date, target_date, target_budget, description, created_at, modified_at)
VALUES
    ('AI 검색 고도화',       'ONGOING',   '2026-06-01', '2026-09-30', 50000000.00, '사내 검색 품질 개선 및 임베딩 기반 랭킹 도입', NOW(6), NOW(6)),
    ('사내 그룹웨어 리뉴얼', 'ONGOING',   '2026-07-01', '2026-12-31', 80000000.00, '레거시 그룹웨어 UI/UX 전면 개편',            NOW(6), NOW(6)),
    ('데이터 파이프라인 구축','HOLDING',   '2026-05-01', '2026-08-31', 30000000.00, '수집~적재 자동화 파이프라인 (일시 보류)',     NOW(6), NOW(6)),
    ('레거시 마이그레이션',  'COMPLETED', '2026-01-01', '2026-06-30', 20000000.00, '온프레미스 → 클라우드 이관 (완료)',           NOW(6), NOW(6)),
    ('구 사내포털',          'ARCHIVED',  '2025-03-01', '2025-12-31', 10000000.00, '구버전 사내포털 (보관 처리)',                 NOW(6), NOW(6));


-- =============================================================================
-- 3) project_members  (owner=is_owner TRUE, 나머지 참여자 / user3 은 P2 에서 LEFT)
-- =============================================================================
INSERT INTO project_members
    (project_id, user_id, is_owner, role, status, left_at, created_at, modified_at)
VALUES
    -- P1 (owner 김피엠)
    (1, 1, TRUE,  'PM', 'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 2, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 3, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 4, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (1, 6, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    -- P2 (owner 김피엠), user3 은 탈퇴(LEFT)
    (2, 1, TRUE,  'PM', 'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 4, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 7, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 8, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 5, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (2, 3, FALSE, NULL, 'LEFT',   '2026-07-10 09:00:00', NOW(6), NOW(6)),
    -- P3 (owner 정민준)
    (3, 5, TRUE,  'PM', 'ACTIVE', NULL, NOW(6), NOW(6)),
    (3, 2, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (3, 6, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    -- P4 (owner 김피엠, 완료 프로젝트)
    (4, 1, TRUE,  'PM', 'ACTIVE', NULL, NOW(6), NOW(6)),
    (4, 2, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    (4, 4, FALSE, NULL, 'ACTIVE', NULL, NOW(6), NOW(6)),
    -- P5 (owner 김피엠, 보관 프로젝트)
    (5, 1, TRUE,  'PM', 'ACTIVE', NULL, NOW(6), NOW(6));


-- =============================================================================
-- 4) milestones  (프로젝트 기간 내 target_date)
-- =============================================================================
INSERT INTO milestones
    (project_id, target_date, goal, created_at, modified_at)
VALUES
    (1, '2026-07-15', '1차 검색 품질 벤치마크', NOW(6), NOW(6)),
    (1, '2026-08-20', '임베딩 파이프라인 완료', NOW(6), NOW(6)),
    (1, '2026-09-25', '정식 배포',              NOW(6), NOW(6)),
    (2, '2026-08-31', '디자인 시스템 확정',     NOW(6), NOW(6)),
    (2, '2026-11-30', '전 기능 QA 완료',        NOW(6), NOW(6)),
    (3, '2026-06-30', '수집 스키마 설계',       NOW(6), NOW(6)),
    (3, '2026-08-15', '적재 자동화',            NOW(6), NOW(6)),
    (4, '2026-03-31', '스키마 이관',            NOW(6), NOW(6)),
    (4, '2026-06-15', '컷오버',                 NOW(6), NOW(6));


-- =============================================================================
-- 5) tasks  (50건) — 담당자는 해당 프로젝트 ACTIVE 멤버.
--   completed_at NULL = 미완료 / 값 있으면 완료. 오늘 ~ 2026-07-23 기준(이번 주 07-20~07-26).
--   과거 마감 + 미완료 = carry-over(지난 주 이월), 이번 주/미래 마감 혼합.
-- =============================================================================
INSERT INTO tasks
    (project_id, assignee_id, content, completed_at, due_date, created_at, modified_at)
VALUES
    -- P1 (AI 검색 고도화)
    (1, 1, '스프린트 리뷰 안건 취합',  NULL,                  '2026-07-22', NOW(6), NOW(6)),
    (1, 1, '검색 로드맵 문서화',       NULL,                  '2026-07-25', NOW(6), NOW(6)),
    (1, 2, '검색 API 인덱싱 개선',     '2026-07-21 15:30:00', '2026-07-21', NOW(6), NOW(6)),
    (1, 2, '랭킹 알고리즘 튜닝',       NULL,                  '2026-07-24', NOW(6), NOW(6)),
    (1, 2, '캐시 레이어 도입',         NULL,                  '2026-07-16', NOW(6), NOW(6)),  -- 이월
    (1, 3, '쿼리 파서 리팩터링',       NULL,                  '2026-07-23', NOW(6), NOW(6)),
    (1, 3, '로그 수집 배치',           '2026-07-20 11:00:00', '2026-07-20', NOW(6), NOW(6)),
    (1, 4, '검색 결과 UI 개선',        NULL,                  '2026-07-22', NOW(6), NOW(6)),
    (1, 4, '자동완성 컴포넌트',        NULL,                  '2026-07-26', NOW(6), NOW(6)),
    (1, 4, '접근성 점검',              NULL,                  '2026-07-15', NOW(6), NOW(6)),  -- 이월
    (1, 6, '임베딩 파이프라인 구축',   NULL,                  '2026-07-24', NOW(6), NOW(6)),
    (1, 6, '데이터 라벨링 QA',         '2026-07-19 18:00:00', '2026-07-19', NOW(6), NOW(6)),
    (1, 6, '피처 스토어 스키마',       NULL,                  '2026-07-29', NOW(6), NOW(6)),  -- 미래
    (1, 2, '벤치마크 리포트 작성',     '2026-07-18 17:00:00', '2026-07-18', NOW(6), NOW(6)),
    -- P2 (사내 그룹웨어 리뉴얼)
    (2, 1, '그룹웨어 요구사항 정리',   '2026-07-21 09:30:00', '2026-07-21', NOW(6), NOW(6)),
    (2, 1, '마일스톤 재조정',          NULL,                  '2026-07-25', NOW(6), NOW(6)),
    (2, 5, '정보구조(IA) 설계',        NULL,                  '2026-07-23', NOW(6), NOW(6)),
    (2, 5, '사용자 시나리오 작성',     NULL,                  '2026-07-17', NOW(6), NOW(6)),  -- 이월
    (2, 4, '디자인 시스템 토큰화',     NULL,                  '2026-07-22', NOW(6), NOW(6)),
    (2, 4, '공통 레이아웃 마크업',     '2026-07-22 16:00:00', '2026-07-20', NOW(6), NOW(6)),
    (2, 7, '알림 센터 UI',             NULL,                  '2026-07-26', NOW(6), NOW(6)),
    (2, 7, '다크모드 대응',            NULL,                  '2026-07-14', NOW(6), NOW(6)),  -- 이월
    (2, 8, 'CI 파이프라인 구성',       '2026-07-21 20:00:00', '2026-07-21', NOW(6), NOW(6)),
    (2, 8, '스테이징 인프라 세팅',     NULL,                  '2026-07-23', NOW(6), NOW(6)),
    (2, 8, '모니터링 대시보드',        NULL,                  '2026-07-28', NOW(6), NOW(6)),  -- 미래
    (2, 5, '릴리즈 노트 템플릿',       NULL,                  '2026-07-25', NOW(6), NOW(6)),
    (2, 4, '반응형 QA',                NULL,                  '2026-07-16', NOW(6), NOW(6)),  -- 이월
    (2, 7, '접근성 개선',              NULL,                  '2026-07-26', NOW(6), NOW(6)),
    -- P3 (데이터 파이프라인 구축)
    (3, 5, '수집 요건 정의',           '2026-07-20 10:00:00', '2026-07-20', NOW(6), NOW(6)),
    (3, 2, '커넥터 개발',              NULL,                  '2026-07-24', NOW(6), NOW(6)),
    (3, 2, '스키마 마이그레이션',      NULL,                  '2026-07-18', NOW(6), NOW(6)),  -- 이월
    (3, 6, '적재 배치 설계',           NULL,                  '2026-07-23', NOW(6), NOW(6)),
    (3, 6, '데이터 품질 룰',           NULL,                  '2026-07-27', NOW(6), NOW(6)),  -- 미래
    (3, 6, '파티셔닝 전략',            '2026-07-15 13:00:00', '2026-07-15', NOW(6), NOW(6)),
    (3, 2, '증분 적재 PoC',            NULL,                  '2026-07-26', NOW(6), NOW(6)),
    -- P4 (레거시 마이그레이션, 완료 프로젝트 — 대부분 완료·과거 마감)
    (4, 1, '이관 계획 수립',           '2026-05-20 10:00:00', '2026-05-20', NOW(6), NOW(6)),
    (4, 2, '스키마 이관 스크립트',     '2026-06-10 15:00:00', '2026-06-10', NOW(6), NOW(6)),
    (4, 2, '데이터 검증',              '2026-06-20 16:00:00', '2026-06-20', NOW(6), NOW(6)),
    (4, 4, '레거시 화면 대체',         '2026-06-25 11:00:00', '2026-06-25', NOW(6), NOW(6)),
    (4, 1, '컷오버 리허설',            '2026-06-28 09:00:00', '2026-06-28', NOW(6), NOW(6)),
    -- 개인 할 일 (project_id NULL)
    (NULL, 1, '주간 업무 보고 작성',   NULL,                  '2026-07-24', NOW(6), NOW(6)),
    (NULL, 2, '기술 블로그 초안',      NULL,                  '2026-07-26', NOW(6), NOW(6)),
    (NULL, 2, '도서 DDIA 5장 정리',    NULL,                  '2026-07-19', NOW(6), NOW(6)),  -- 이월(개인)
    (NULL, 3, '사내 교육 수강',        '2026-07-22 19:00:00', '2026-07-25', NOW(6), NOW(6)),
    (NULL, 4, '포트폴리오 정리',       NULL,                  '2026-07-27', NOW(6), NOW(6)),  -- 미래
    (NULL, 5, '경비 정산',             '2026-07-21 13:00:00', '2026-07-21', NOW(6), NOW(6)),
    (NULL, 6, '자격증 신청',           NULL,                  '2026-07-23', NOW(6), NOW(6)),
    (NULL, 7, '건강검진 예약',         NULL,                  '2026-07-30', NOW(6), NOW(6)),  -- 미래
    (NULL, 8, '온콜 인수인계 문서',    NULL,                  '2026-07-18', NOW(6), NOW(6)),  -- 이월(개인)
    (NULL, 1, '팀 회식 장소 예약',     NULL,                  '2026-07-25', NOW(6), NOW(6));


-- =============================================================================
-- 6) project_posts  (프로젝트 게시판)
-- =============================================================================
INSERT INTO project_posts
    (project_id, author_id, title, content, created_at, modified_at)
VALUES
    (1, 1, '킥오프 회의록 공유',        '검색 고도화 프로젝트 킥오프 내용 정리했습니다. 확인 부탁드려요.', '2026-07-16 09:00:00', '2026-07-16 09:00:00'),
    (1, 2, '인덱싱 개선 논의',          '역색인 구조를 이렇게 바꾸면 어떨지 의견 주세요.',                '2026-07-18 14:20:00', '2026-07-18 14:20:00'),
    (2, 1, '리뉴얼 범위 공지',          '이번 스프린트 리뉴얼 범위와 우선순위 공유합니다.',              '2026-07-19 10:00:00', '2026-07-19 10:00:00'),
    (2, 8, '배포 파이프라인 안내',      'CI/CD 파이프라인 사용법 문서 링크 첨부합니다.',                 '2026-07-21 11:30:00', '2026-07-21 11:30:00'),
    (2, 4, '디자인 시스템 리뷰 요청',   '토큰화한 디자인 시스템 리뷰 부탁드립니다.',                     '2026-07-22 16:40:00', '2026-07-22 16:40:00'),
    (3, 5, '수집 대상 확정',            '1차 수집 대상 소스 목록 확정했습니다.',                         '2026-07-14 13:00:00', '2026-07-14 13:00:00');


-- =============================================================================
-- 7) meetings  (회의록, document_no UNIQUE)
-- =============================================================================
INSERT INTO meetings
    (project_id, document_no, title, author_id, meeting_date, location, purpose, content, follow_up, recording, deleted_at, created_at, modified_at)
VALUES
    (1,    'MTG-2026-001', '검색 고도화 킥오프',   1, '2026-06-05', '본사 3층 회의실 A', '프로젝트 범위·일정 합의', '범위, 역할, 마일스톤 확정',       '주간 스프린트 리뷰 운영', 'recordings/mtg-2026-001.mp4', NULL,                  NOW(6), NOW(6)),
    (2,    'MTG-2026-002', '그룹웨어 리뉴얼 착수', 1, '2026-07-03', '본사 5층 회의실 B', '리뉴얼 착수 및 요구사항', '요구사항 우선순위 정리',         '디자인 시스템 선행',       NULL,                          NULL,                  NOW(6), NOW(6)),
    (NULL, 'MTG-2026-003', '전사 기획 정기회의',   5, '2026-07-10', '온라인(Zoom)',      '월간 기획 공유',           '부서별 진행상황 공유',           '차월 목표 수립',           'recordings/mtg-2026-003.mp4', NULL,                  NOW(6), NOW(6)),
    (1,    'MTG-2026-004', '검색 스프린트 리뷰',   2, '2026-07-22', '본사 3층 회의실 A', '스프린트 결과 리뷰',       '인덱싱/랭킹 개선 데모',         '벤치마크 리포트 공유',     NULL,                          NULL,                  NOW(6), NOW(6)),
    -- 소프트 삭제된 회의: MeetingEntity @SQLRestriction("deleted_at IS NULL") 로 목록/상세에서 자동 제외됨 (필터 테스트용)
    (2,    'MTG-2026-005', '취소된 중간 점검',     1, '2026-07-08', '본사 5층 회의실 B', '중간 점검(취소)',         '일정 사유로 취소됨',             '재소집 예정',             NULL,                          '2026-07-12 09:00:00', NOW(6), NOW(6));


-- =============================================================================
-- 8) meeting_attendees  (회의별 WRITER 1명 + ATTENDEE, 이름·부서 스냅샷)
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
    (2, 8, '임도현', 'DEVOPS',   'ATTENDEE', NOW(6), NOW(6)),
    (2, 5, '정민준', 'PLANNING', 'ATTENDEE', NOW(6), NOW(6)),
    (3, 5, '정민준', 'PLANNING', 'WRITER',   NOW(6), NOW(6)),
    (3, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6)),
    (4, 2, '이하늘', 'BACKEND',  'WRITER',   NOW(6), NOW(6)),
    (4, 3, '박도윤', 'BACKEND',  'ATTENDEE', NOW(6), NOW(6)),
    (4, 1, '김피엠', 'PM',       'ATTENDEE', NOW(6), NOW(6));


-- =============================================================================
-- 9) schedules  (일정, id 1~8) — 7·8 은 휴가로 leaves 확장
-- =============================================================================
INSERT INTO schedules
    (user_id, title, start_at, end_at, all_day, type, created_at, modified_at)
VALUES
    (1, '주간 팀 미팅',        '2026-07-20 10:00:00', '2026-07-20 11:00:00', FALSE, 'MEETING',   NOW(6), NOW(6)),  -- 1
    (1, '검색팀 스프린트 리뷰', '2026-07-22 14:00:00', '2026-07-22 15:30:00', FALSE, 'MEETING',   NOW(6), NOW(6)),  -- 2
    (2, '기술 세미나',          '2026-07-24 16:00:00', '2026-07-24 17:00:00', FALSE, 'MEETING',   NOW(6), NOW(6)),  -- 3
    (4, '디자인 리뷰',          '2026-07-23 11:00:00', '2026-07-23 12:00:00', FALSE, 'MEETING',   NOW(6), NOW(6)),  -- 4
    (5, '전사 워크숍',          '2026-07-27 00:00:00', '2026-07-27 23:59:59', TRUE,  'MEETING',   NOW(6), NOW(6)),  -- 5
    (8, '배포 점검',            '2026-07-25 20:00:00', '2026-07-25 22:00:00', FALSE, 'FIELDWORK', NOW(6), NOW(6)),  -- 6
    (3, '연차 휴가',            '2026-07-28 00:00:00', '2026-07-28 23:59:59', TRUE,  'PERSONAL',  NOW(6), NOW(6)),  -- 7 (leave, 휴가는 schedule_leaves로 구분)
    (6, '병가',                 '2026-07-21 00:00:00', '2026-07-21 23:59:59', TRUE,  'PERSONAL',  NOW(6), NOW(6));  -- 8 (leave)


-- =============================================================================
-- 10) schedule_leaves  (휴가 상세, schedule_id 7·8 과 1:1)
-- =============================================================================
INSERT INTO schedule_leaves
    (schedule_id, leave_type, reason, days, created_at, modified_at)
VALUES
    (7, 'ANNUAL', '개인 연차', 1, NOW(6), NOW(6)),
    (8, 'SICK',   '몸살',      1, NOW(6), NOW(6));


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
-- 12) leave_balances  (연차 현황, 사용자별 2026년 부여일수)
-- =============================================================================
INSERT INTO leave_balances
    (user_id, year, granted_days, created_at, modified_at)
VALUES
    (1, 2026, 15, NOW(6), NOW(6)),
    (2, 2026, 15, NOW(6), NOW(6)),
    (3, 2026, 15, NOW(6), NOW(6)),
    (4, 2026, 15, NOW(6), NOW(6)),
    (5, 2026, 15, NOW(6), NOW(6)),
    (6, 2026, 15, NOW(6), NOW(6)),
    (7, 2026, 15, NOW(6), NOW(6)),
    (8, 2026, 15, NOW(6), NOW(6)),
    (9, 2026, 15, NOW(6), NOW(6)),
    (10, 2026, 15, NOW(6), NOW(6));
