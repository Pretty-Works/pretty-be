-- 프로젝트 생성 API 테스트용 시드 유저 (비밀번호 전원 Test1234!)
-- 로드: docker compose cp src\main\resources\db\seed-users.sql mysql:/tmp/seed-users.sql
--       docker compose exec -T mysql sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test < /tmp/seed-users.sql"
-- 빈 users 테이블 기준으로 id는 1~7 순서대로 부여됩니다.
--
--  id  이름     부서       직급          상태       용도
--  1   김피엠   PM         STAFF        ACTIVE     생성 가능(부서 PM) — 오너 후보
--  2   이팀장   BACKEND    TEAM_LEADER  ACTIVE     생성 가능(팀장 이상) — 오너 후보
--  3   박사원   BACKEND    STAFF        ACTIVE     생성 불가(사원·비PM) → PROJECT_001 / 참여자
--  4   최선임   FRONTEND   SENIOR       ACTIVE     참여자
--  5   정파트   PLANNING   PART_LEADER  ACTIVE     참여자
--  6   한퇴사   QA         STAFF        RESIGNED   비활성 → USER_001 테스트
--  7   오휴직   SALES      STAFF        ON_LEAVE   비활성 → USER_001 테스트

INSERT INTO users
    (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date)
VALUES
    ('EMP001', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '김피엠', 'emp001@test.com', '010-1000-0001', '1985-03-10', 'MALE',   'PM',       'STAFF',       'ACTIVE',   '2015-01-02'),
    ('EMP002', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '이팀장', 'emp002@test.com', '010-1000-0002', '1982-07-21', 'FEMALE', 'BACKEND',  'TEAM_LEADER', 'ACTIVE',   '2013-03-04'),
    ('EMP003', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '박사원', 'emp003@test.com', '010-1000-0003', '1995-11-05', 'MALE',   'BACKEND',  'STAFF',       'ACTIVE',   '2022-06-01'),
    ('EMP004', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '최선임', 'emp004@test.com', '010-1000-0004', '1990-01-18', 'FEMALE', 'FRONTEND', 'SENIOR',      'ACTIVE',   '2019-09-16'),
    ('EMP005', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '정파트', 'emp005@test.com', '010-1000-0005', '1988-05-30', 'MALE',   'PLANNING', 'PART_LEADER', 'ACTIVE',   '2017-02-13'),
    ('EMP006', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '한퇴사', 'emp006@test.com', '010-1000-0006', '1993-08-24', 'FEMALE', 'QA',       'STAFF',       'RESIGNED', '2020-04-06'),
    ('EMP007', '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC', '오휴직', 'emp007@test.com', '010-1000-0007', '1992-12-12', 'MALE',   'SALES',    'STAFF',       'ON_LEAVE', '2021-07-19');
