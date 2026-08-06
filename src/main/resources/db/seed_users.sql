-- PrettyWorks 데모 시드 — 다온테크(주) 임직원 287명
-- docs/company/ 의 인물 카드(280명 재직·휴직 + 퇴사자 7명)를 users 테이블로 옮긴 것.
-- init.sql로 스키마를 먼저 만든 뒤 이 파일을 이어서 로드한다(로드 절차는 init.sql 상단 주석 참고,
-- Get-Content | docker 파이프와 docker compose cp 는 한글 깨짐/파일 누락으로 금지).
--
-- ⚠ 비밀번호 해시를 먼저 채워야 한다 — 아래 SET 문의 'REPLACE_ME...' 를
--   BCryptPasswordEncoder().encode("Daon!2026") 실제 출력값으로 바꾼 뒤 실행할 것.
--   (이 프로젝트는 PasswordConfig.java 에서 new BCryptPasswordEncoder() 기본 strength=10 을 그대로 쓰므로,
--    IntelliJ에서 그 인코더로 직접 인코딩해야 로그인 시 매칭이 보장된다. 임의로 만든 해시 문자열을
--    쓰면 형식은 통과해도 실제 비밀번호와 안 맞아 로그인에 실패한다.)
-- 전원 동일 평문 비밀번호를 쓰는 이유: 02번 문서(인물 작성 규격) §4 DB 매핑에 이미 명시된 데모 전용 계정 정책.
--
-- 사번 범위:
--   DT22-0001~0024   리더십 21명 선점 (경영진 7 + 팀장 14, 사장·부사장 포함)
--   DT22-0025~0217   전출조 + 2022년 공채1기 (일반 + 퇴사자 2명 포함)
--   DT23-0001~0045   2023년 입사자 (공채2기 + 경력, 퇴사자 3명 포함)
--   DT24-0001~0055   2024년 입사자 (공채3기 + 경력, 퇴사자 2명 포함)
--   DT25-0001~0058   2025년 입사자 (공채4기 + 경력)
--   DT26-0001~0005   2026년 입사자 (경력만, 공채5기는 9월 예정이라 아직 없음)
-- 상태 분포: ACTIVE 277 · ON_LEAVE 3(신태경·배윤서·임규호, 03번 문서 부록B) · RESIGNED 7(03번 문서 부록A)
--   → 정원 280 = ACTIVE 277 + ON_LEAVE 3. RESIGNED 7은 정원 밖 별도 행.

SET @PWHASH = '$2y$10$LbJt3UI.WeepFTIO.RGxgOF3ztmuVcEOQuxfp4Ft.Ezv8MwvOElqC';

-- 경영지원 (MANAGEMENT_SUPPORT) (11명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0001', @PWHASH, '오현주', 'hyunju.oh@daontech.co.kr', '010-4001-0614', '1971-06-14', 'FEMALE', 'MANAGEMENT_SUPPORT', 'PRESIDENT', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0002', @PWHASH, '남기훈', 'kihoon.nam@daontech.co.kr', '010-4002-0519', '1974-05-19', 'MALE', 'MANAGEMENT_SUPPORT', 'VICE_PRESIDENT', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0005', @PWHASH, '문정선', 'jeongseon.mun@daontech.co.kr', '010-4005-0412', '1977-04-12', 'FEMALE', 'MANAGEMENT_SUPPORT', 'EXECUTIVE', 'ACTIVE', '2022-04-01', '2022-04-01 00:00:00', '2022-04-01 00:00:00'),
('DT22-0011', @PWHASH, '신동렬', 'dongryeol.shin@daontech.co.kr', '010-4011-0227', '1981-02-27', 'MALE', 'MANAGEMENT_SUPPORT', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0025', @PWHASH, '김하온', 'haon.kim@daontech.co.kr', '010-4025-0514', '1992-05-14', 'FEMALE', 'MANAGEMENT_SUPPORT', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0026', @PWHASH, '박시우', 'siwoo.park@daontech.co.kr', '010-4026-0310', '1999-03-10', 'MALE', 'MANAGEMENT_SUPPORT', 'STAFF', 'ACTIVE', '2022-09-01', '2022-09-01 00:00:00', '2022-09-01 00:00:00'),
('DT22-0027', @PWHASH, '이서준', 'seojun.lee@daontech.co.kr', '010-4027-0621', '1989-06-21', 'MALE', 'MANAGEMENT_SUPPORT', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0001', @PWHASH, '배수아', 'sua.bae@daontech.co.kr', '010-4001-0530', '2001-05-30', 'FEMALE', 'MANAGEMENT_SUPPORT', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT22-0028', @PWHASH, '윤성민', 'seongmin.yun@daontech.co.kr', '010-4028-0130', '1987-01-30', 'MALE', 'MANAGEMENT_SUPPORT', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0029', @PWHASH, '임재현', 'jaehyeon.im@daontech.co.kr', '010-4029-0327', '1991-03-27', 'MALE', 'MANAGEMENT_SUPPORT', 'SENIOR', 'ACTIVE', '2022-04-01', '2022-04-01 00:00:00', '2022-04-01 00:00:00'),
('DT23-0003', @PWHASH, '강지원', 'jiwon.kang@daontech.co.kr', '010-4003-1025', '1995-10-25', 'FEMALE', 'MANAGEMENT_SUPPORT', 'SENIOR', 'ACTIVE', '2023-11-16', '2023-11-16 00:00:00', '2023-11-16 00:00:00')
;

-- 인사 (HR) (8명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0012', @PWHASH, '표유진', 'yujin.pyo@daontech.co.kr', '010-4012-0719', '1983-07-19', 'FEMALE', 'HR', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0030', @PWHASH, '이지안', 'jian.lee@daontech.co.kr', '010-4030-0403', '1990-04-03', 'FEMALE', 'HR', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0031', @PWHASH, '송예은', 'yeeun.song@daontech.co.kr', '010-4031-1120', '1994-11-20', 'FEMALE', 'HR', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0004', @PWHASH, '배지윤', 'jiyun.bae@daontech.co.kr', '010-4004-0227', '1996-02-27', 'FEMALE', 'HR', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT22-0033', @PWHASH, '홍수진', 'sujin.hong@daontech.co.kr', '010-4033-0525', '1988-05-25', 'FEMALE', 'HR', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0006', @PWHASH, '조은빈', 'eunbin.jo@daontech.co.kr', '010-4006-1030', '1997-10-30', 'FEMALE', 'HR', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT24-0006', @PWHASH, '곽나윤', 'nayun.gwak@daontech.co.kr', '010-4006-0322', '2000-03-22', 'FEMALE', 'HR', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT25-0007', @PWHASH, '장현우', 'hyunwoo.jang@daontech.co.kr', '010-4007-0914', '1999-09-14', 'MALE', 'HR', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00')
;

-- 재무회계 (FINANCE) (8명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0013', @PWHASH, '조민석', 'minseok.jo@daontech.co.kr', '010-4013-0925', '1979-09-25', 'MALE', 'FINANCE', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0034', @PWHASH, '윤소정', 'sojeong.yun@daontech.co.kr', '010-4034-0319', '1988-03-19', 'FEMALE', 'FINANCE', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0035', @PWHASH, '하은채', 'eunchae.ha@daontech.co.kr', '010-4035-1105', '1993-11-05', 'FEMALE', 'FINANCE', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0007', @PWHASH, '민지수', 'jisu.min@daontech.co.kr', '010-4007-0821', '1995-08-21', 'FEMALE', 'FINANCE', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT22-0037', @PWHASH, '오수빈', 'subin.oh@daontech.co.kr', '010-4037-0530', '1985-05-30', 'FEMALE', 'FINANCE', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0038', @PWHASH, '임소연', 'soyeon.im@daontech.co.kr', '010-4038-0122', '1994-01-22', 'FEMALE', 'FINANCE', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0008', @PWHASH, '곽서윤', 'seoyun.gwak@daontech.co.kr', '010-4008-0908', '1996-09-08', 'FEMALE', 'FINANCE', 'SENIOR', 'ACTIVE', '2023-04-16', '2023-04-16 00:00:00', '2023-04-16 00:00:00'),
('DT24-0008', @PWHASH, '문가은', 'gaeun.mun@daontech.co.kr', '010-4008-0725', '2001-07-25', 'FEMALE', 'FINANCE', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00')
;

-- 영업 (SALES) (13명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0014', @PWHASH, '황보람', 'boram.hwang@daontech.co.kr', '010-4014-0211', '1982-02-11', 'FEMALE', 'SALES', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0084', @PWHASH, '봉현석', 'hyeonseok.bong@daontech.co.kr', '010-4084-0312', '1985-03-12', 'MALE', 'SALES', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0085', @PWHASH, '마상철', 'sangcheol.ma@daontech.co.kr', '010-4085-0518', '1989-05-18', 'MALE', 'SALES', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0090', @PWHASH, '도예은', 'yeeun.do@daontech.co.kr', '010-4090-0517', '1995-05-17', 'FEMALE', 'SALES', 'SENIOR', 'ACTIVE', '2022-07-01', '2022-07-01 00:00:00', '2022-07-01 00:00:00'),
('DT23-0023', @PWHASH, '위진혁', 'jinhyeok.wi@daontech.co.kr', '010-4023-0613', '1993-06-13', 'MALE', 'SALES', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT22-0086', @PWHASH, '소하은', 'haeun.so@daontech.co.kr', '010-4086-0227', '1991-02-27', 'FEMALE', 'SALES', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0087', @PWHASH, '함지수', 'jisu.ham@daontech.co.kr', '010-4087-0614', '1987-06-14', 'FEMALE', 'SALES', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0088', @PWHASH, '오민혁', 'minhyeok.oh@daontech.co.kr', '010-4088-0410', '1991-04-10', 'MALE', 'SALES', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0025', @PWHASH, '나서은', 'seoeun.na@daontech.co.kr', '010-4025-0331', '1994-03-31', 'FEMALE', 'SALES', 'SENIOR', 'ACTIVE', '2023-06-16', '2023-06-16 00:00:00', '2023-06-16 00:00:00'),
('DT23-0024', @PWHASH, '최우진', 'woojin.choi@daontech.co.kr', '010-4024-0407', '1993-04-07', 'MALE', 'SALES', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT25-0034', @PWHASH, '배하연', 'hayeon.bae@daontech.co.kr', '010-4034-0715', '1996-07-15', 'FEMALE', 'SALES', 'SENIOR', 'ACTIVE', '2025-01-16', '2025-01-16 00:00:00', '2025-01-16 00:00:00'),
('DT22-0089', @PWHASH, '도영재', 'yeongjae.do@daontech.co.kr', '010-4089-0325', '1992-03-25', 'MALE', 'SALES', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0030', @PWHASH, '서인혁', 'inhyeok.seo@daontech.co.kr', '010-4030-0526', '1989-05-26', 'MALE', 'SALES', 'SENIOR', 'ACTIVE', '2024-08-16', '2024-08-16 00:00:00', '2024-08-16 00:00:00')
;

-- 사업기획 (PLANNING) (11명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0006', @PWHASH, '배준혁', 'junhyeok.bae@daontech.co.kr', '010-4006-1023', '1974-10-23', 'MALE', 'PLANNING', 'EXECUTIVE', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0015', @PWHASH, '노진우', 'jinwoo.no@daontech.co.kr', '010-4015-1205', '1983-12-05', 'MALE', 'PLANNING', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0078', @PWHASH, '오채린', 'chaerin.oh@daontech.co.kr', '010-4078-0312', '1985-03-12', 'FEMALE', 'PLANNING', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0079', @PWHASH, '임소정', 'sojeong.im@daontech.co.kr', '010-4079-0518', '1992-05-18', 'FEMALE', 'PLANNING', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0082', @PWHASH, '최재훈', 'jaehun.choi@daontech.co.kr', '010-4082-0227', '1991-02-27', 'MALE', 'PLANNING', 'SENIOR', 'ACTIVE', '2022-06-01', '2022-06-01 00:00:00', '2022-06-01 00:00:00'),
('DT22-0080', @PWHASH, '구태환', 'taehwan.gu@daontech.co.kr', '010-4080-0325', '1990-03-25', 'MALE', 'PLANNING', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0081', @PWHASH, '위성현', 'seonghyun.wi@daontech.co.kr', '010-4081-0219', '1986-02-19', 'MALE', 'PLANNING', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0020', @PWHASH, '채빈나', 'binna.chae@daontech.co.kr', '010-4020-0517', '1994-05-17', 'FEMALE', 'PLANNING', 'SENIOR', 'ACTIVE', '2023-04-16', '2023-04-16 00:00:00', '2023-04-16 00:00:00'),
('DT23-0021', @PWHASH, '마정훈', 'jeonghun.ma@daontech.co.kr', '010-4021-0613', '1993-06-13', 'MALE', 'PLANNING', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT24-0025', @PWHASH, '고아진', 'ajin.go@daontech.co.kr', '010-4025-0715', '2000-07-15', 'FEMALE', 'PLANNING', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT25-0029', @PWHASH, '도희수', 'heesu.do@daontech.co.kr', '010-4029-0526', '1998-05-26', 'FEMALE', 'PLANNING', 'STAFF', 'ACTIVE', '2025-01-16', '2025-01-16 00:00:00', '2025-01-16 00:00:00')
;

-- 컨설팅 (CONSULTING) (17명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0016', @PWHASH, '안세희', 'sehui.an@daontech.co.kr', '010-4016-0122', '1979-01-22', 'FEMALE', 'CONSULTING', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0092', @PWHASH, '석다연', 'dayeon.seok@daontech.co.kr', '010-4092-0312', '1985-03-12', 'FEMALE', 'CONSULTING', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0095', @PWHASH, '권도영', 'doyeong.gwon@daontech.co.kr', '010-4095-0614', '1991-06-14', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0027', @PWHASH, '홍재현', 'jaehyeon.hong@daontech.co.kr', '010-4027-0331', '1993-03-31', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT22-0096', @PWHASH, '임세아', 'sea.im@daontech.co.kr', '010-4096-0410', '1990-04-10', 'FEMALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0097', @PWHASH, '노태섭', 'taeseop.no@daontech.co.kr', '010-4097-0325', '1992-03-25', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0100', @PWHASH, '공서희', 'seohui.gong@daontech.co.kr', '010-4100-0613', '1994-06-13', 'FEMALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2022-05-16', '2022-05-16 00:00:00', '2022-05-16 00:00:00'),
('DT22-0093', @PWHASH, '편도현', 'dohyeon.pyeon@daontech.co.kr', '010-4093-0518', '1987-05-18', 'MALE', 'CONSULTING', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0029', @PWHASH, '마수현', 'suhyeon.ma@daontech.co.kr', '010-4029-0122', '1996-01-22', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2023-04-16', '2023-04-16 00:00:00', '2023-04-16 00:00:00'),
('DT23-0030', @PWHASH, '탁서연', 'seoyeon.tak@daontech.co.kr', '010-4030-0526', '1997-05-26', 'FEMALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2023-11-16', '2023-11-16 00:00:00', '2023-11-16 00:00:00'),
('DT22-0098', @PWHASH, '채동민', 'dongmin.chae@daontech.co.kr', '010-4098-0719', '1989-07-19', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0101', @PWHASH, '함가은', 'gaeun.ham@daontech.co.kr', '010-4101-0407', '1998-04-07', 'FEMALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2022-10-01', '2022-10-01 00:00:00', '2022-10-01 00:00:00'),
('DT22-0094', @PWHASH, '석민규', 'mingyu.seok@daontech.co.kr', '010-4094-0227', '1989-02-27', 'MALE', 'CONSULTING', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0032', @PWHASH, '최진혁', 'jinhyeok.choi@daontech.co.kr', '010-4032-0128', '1994-01-28', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2024-06-16', '2024-06-16 00:00:00', '2024-06-16 00:00:00'),
('DT24-0033', @PWHASH, '유소민', 'somin.yu@daontech.co.kr', '010-4033-0622', '1995-06-22', 'FEMALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT24-0034', @PWHASH, '손민석', 'minseok.son@daontech.co.kr', '010-4034-0217', '1991-02-17', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2024-08-01', '2024-08-01 00:00:00', '2024-08-01 00:00:00'),
('DT24-0035', @PWHASH, '여준희', 'junhui.yeo@daontech.co.kr', '010-4035-0409', '1996-04-09', 'MALE', 'CONSULTING', 'SENIOR', 'ACTIVE', '2024-10-16', '2024-10-16 00:00:00', '2024-10-16 00:00:00')
;

-- 프로젝트관리 (PM) (19명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0017', @PWHASH, '구본재', 'bonjae.gu@daontech.co.kr', '010-4017-1030', '1977-10-30', 'MALE', 'PM', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0114', @PWHASH, '이도윤', 'doyun.lee@daontech.co.kr', '010-4114-0520', '1985-05-20', 'MALE', 'PM', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0115', @PWHASH, '송하영', 'hayeong.song@daontech.co.kr', '010-4115-0814', '1987-08-14', 'FEMALE', 'PM', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0033', @PWHASH, '곽지완', 'jiwan.gwak@daontech.co.kr', '010-4033-0125', '1988-01-25', 'MALE', 'PM', 'SENIOR', 'ACTIVE', '2023-11-01', '2023-11-01 00:00:00', '2023-11-01 00:00:00'),
('DT22-0116', @PWHASH, '정소율', 'soyul.jeong@daontech.co.kr', '010-4116-0611', '1992-06-11', 'FEMALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0117', @PWHASH, '하진용', 'jinyong.ha@daontech.co.kr', '010-4117-0923', '1989-09-23', 'MALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0042', @PWHASH, '유하은', 'haeun.yu@daontech.co.kr', '010-4042-1205', '1998-12-05', 'FEMALE', 'PM', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT22-0124', @PWHASH, '백승민', 'seungmin.baek@daontech.co.kr', '010-4124-0217', '1999-02-17', 'MALE', 'PM', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0118', @PWHASH, '곽태희', 'taehui.gwak@daontech.co.kr', '010-4118-0409', '1994-04-09', 'FEMALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0048', @PWHASH, '노건희', 'geonhui.no@daontech.co.kr', '010-4048-1030', '2000-10-30', 'MALE', 'PM', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT22-0119', @PWHASH, '임서진', 'seojin.im@daontech.co.kr', '010-4119-1114', '1991-11-14', 'FEMALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0120', @PWHASH, '조현민', 'hyeonmin.jo@daontech.co.kr', '010-4120-0302', '1990-03-02', 'MALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0125', @PWHASH, '배주안', 'juan.bae@daontech.co.kr', '010-4125-0719', '1997-07-19', 'MALE', 'PM', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0121', @PWHASH, '최한결', 'hangyeol.choi@daontech.co.kr', '010-4121-0827', '1993-08-27', 'MALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0047', @PWHASH, '강도희', 'dohui.kang@daontech.co.kr', '010-4047-0310', '2001-03-10', 'FEMALE', 'PM', 'STAFF', 'ACTIVE', '2025-03-16', '2025-03-16 00:00:00', '2025-03-16 00:00:00'),
('DT22-0122', @PWHASH, '오재혁', 'jaehyeok.oh@daontech.co.kr', '010-4122-1201', '1988-12-01', 'MALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0126', @PWHASH, '문승호', 'seungho.mun@daontech.co.kr', '010-4126-0525', '1996-05-25', 'MALE', 'PM', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0123', @PWHASH, '신우진', 'ujin.shin@daontech.co.kr', '010-4123-0108', '1995-01-08', 'MALE', 'PM', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0032', @PWHASH, '표가은', 'gaeun.pyo@daontech.co.kr', '010-4032-0916', '1999-09-16', 'FEMALE', 'PM', 'STAFF', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00')
;

-- 프론트엔드개발 (FRONTEND) (35명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0018', @PWHASH, '임다혜', 'dahye.im@daontech.co.kr', '010-4018-0508', '1985-05-08', 'FEMALE', 'FRONTEND', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0142', @PWHASH, '남시우', 'siu.nam@daontech.co.kr', '010-4142-0314', '1987-03-14', 'MALE', 'FRONTEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0143', @PWHASH, '오하린', 'harin.oh@daontech.co.kr', '010-4143-0902', '1985-09-02', 'FEMALE', 'FRONTEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0144', @PWHASH, '배준호', 'junho.bae@daontech.co.kr', '010-4144-0127', '1990-01-27', 'MALE', 'FRONTEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0145', @PWHASH, '최이안', 'ian.choi@daontech.co.kr', '010-4145-0611', '1989-06-11', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0146', @PWHASH, '정하나', 'hana.jeong@daontech.co.kr', '010-4146-0405', '1993-04-05', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0147', @PWHASH, '유건우', 'geonu.yu@daontech.co.kr', '010-4147-1019', '1990-10-19', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0148', @PWHASH, '조은비', 'eunbi.jo@daontech.co.kr', '010-4148-0208', '1996-02-08', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0149', @PWHASH, '임태오', 'taeo.im@daontech.co.kr', '010-4149-0723', '1999-07-23', 'MALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0150', @PWHASH, '한소이', 'soi.han@daontech.co.kr', '010-4150-1130', '2000-11-30', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0151', @PWHASH, '구현서', 'hyeonseo.gu@daontech.co.kr', '010-4151-0516', '1988-05-16', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0152', @PWHASH, '배아현', 'ahyeon.bae@daontech.co.kr', '010-4152-0309', '1998-03-09', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0153', @PWHASH, '최도훈', 'dohun.choi@daontech.co.kr', '010-4153-0814', '2001-08-14', 'MALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2022-09-01', '2022-09-01 00:00:00', '2022-09-01 00:00:00'),
('DT22-0154', @PWHASH, '신라율', 'rayul.shin@daontech.co.kr', '010-4154-1201', '1992-12-01', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0155', @PWHASH, '곽민서', 'minseo.gwak@daontech.co.kr', '010-4155-0427', '1989-04-27', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0036', @PWHASH, '오시윤', 'siyun.oh@daontech.co.kr', '010-4036-0918', '1997-09-18', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2023-05-16', '2023-05-16 00:00:00', '2023-05-16 00:00:00'),
('DT22-0156', @PWHASH, '임도경', 'dogyeong.im@daontech.co.kr', '010-4156-0106', '1994-01-06', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0157', @PWHASH, '표서연', 'seoyeon.pyo@daontech.co.kr', '010-4157-0624', '1991-06-24', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0158', @PWHASH, '하준영', 'junyeong.ha@daontech.co.kr', '010-4158-0211', '1999-02-11', 'MALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0052', @PWHASH, '강나은', 'naeun.kang@daontech.co.kr', '010-4052-0519', '2002-05-19', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT22-0159', @PWHASH, '조이든', 'ideun.jo@daontech.co.kr', '010-4159-1030', '1997-10-30', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0160', @PWHASH, '문서율', 'seoyul.mun@daontech.co.kr', '010-4160-0807', '1998-08-07', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0046', @PWHASH, '신하율', 'hayul.shin@daontech.co.kr', '010-4046-0422', '2000-04-22', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2024-11-01', '2024-11-01 00:00:00', '2024-11-01 00:00:00'),
('DT22-0161', @PWHASH, '배지오', 'jio.bae@daontech.co.kr', '010-4161-0715', '1990-07-15', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0047', @PWHASH, '유채은', 'chaeeun.yu@daontech.co.kr', '010-4047-1103', '1993-11-03', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2024-03-16', '2024-03-16 00:00:00', '2024-03-16 00:00:00'),
('DT23-0037', @PWHASH, '곽시완', 'siwan.gwak@daontech.co.kr', '010-4037-0627', '1996-06-27', 'MALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2023-11-16', '2023-11-16 00:00:00', '2023-11-16 00:00:00'),
('DT22-0162', @PWHASH, '남예준', 'yejun.nam@daontech.co.kr', '010-4162-0219', '1988-02-19', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0038', @PWHASH, '오한별', 'hanbyeol.oh@daontech.co.kr', '010-4038-0925', '1995-09-25', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2023-08-16', '2023-08-16 00:00:00', '2023-08-16 00:00:00'),
('DT24-0048', @PWHASH, '최지환', 'jihwan.choi@daontech.co.kr', '010-4048-0313', '1992-03-13', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT24-0049', @PWHASH, '임소망', 'somang.im@daontech.co.kr', '010-4049-0108', '2001-01-08', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT25-0053', @PWHASH, '배건율', 'geonyul.bae@daontech.co.kr', '010-4053-1004', '1999-10-04', 'MALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2025-03-16', '2025-03-16 00:00:00', '2025-03-16 00:00:00'),
('DT25-0054', @PWHASH, '정유안', 'yuan.jeong@daontech.co.kr', '010-4054-0528', '1996-05-28', 'FEMALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2025-01-16', '2025-01-16 00:00:00', '2025-01-16 00:00:00'),
('DT25-0055', @PWHASH, '최하준', 'hajun.choi@daontech.co.kr', '010-4055-0820', '1989-08-20', 'MALE', 'FRONTEND', 'SENIOR', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT26-0002', @PWHASH, '표시안', 'sian.pyo@daontech.co.kr', '010-4002-1215', '2002-12-15', 'FEMALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2026-02-16', '2026-02-16 00:00:00', '2026-02-16 00:00:00'),
('DT23-0039', @PWHASH, '임로운', 'roun.im@daontech.co.kr', '010-4039-0402', '1997-04-02', 'MALE', 'FRONTEND', 'STAFF', 'ACTIVE', '2023-02-16', '2023-02-16 00:00:00', '2023-02-16 00:00:00')
;

-- 백엔드개발 (BACKEND) (68명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0003', @PWHASH, '윤태경', 'taegyeong.yun@daontech.co.kr', '010-4003-0314', '1979-03-14', 'MALE', 'BACKEND', 'EXECUTIVE', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0019', @PWHASH, '서강우', 'gangwoo.seo@daontech.co.kr', '010-4019-1117', '1981-11-17', 'MALE', 'BACKEND', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0163', @PWHASH, '하지원', 'jiwon.ha@daontech.co.kr', '010-4163-0411', '1986-04-11', 'FEMALE', 'BACKEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0164', @PWHASH, '윤성우', 'seongu.yun@daontech.co.kr', '010-4164-0823', '1992-08-23', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0175', @PWHASH, '임재훈', 'jaehun.im@daontech.co.kr', '010-4175-0608', '1989-06-08', 'MALE', 'BACKEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0183', @PWHASH, '서윤호', 'yunho.seo@daontech.co.kr', '010-4183-1130', '1984-11-30', 'MALE', 'BACKEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0190', @PWHASH, '하유진', 'yujin.ha@daontech.co.kr', '010-4190-0520', '1991-05-20', 'FEMALE', 'BACKEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0198', @PWHASH, '조태현', 'taehyeon.jo@daontech.co.kr', '010-4198-0119', '1987-01-19', 'MALE', 'BACKEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0199', @PWHASH, '배민아', 'mina.bae@daontech.co.kr', '010-4199-0605', '1990-06-05', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0207', @PWHASH, '곽민준', 'minjun.gwak@daontech.co.kr', '010-4207-0402', '1990-04-02', 'MALE', 'BACKEND', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0165', @PWHASH, '조민규', 'mingyu.jo@daontech.co.kr', '010-4165-0214', '1990-02-14', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0166', @PWHASH, '배소현', 'sohyeon.bae@daontech.co.kr', '010-4166-1107', '1993-11-07', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0167', @PWHASH, '최윤아', 'yuna.choi@daontech.co.kr', '010-4167-0519', '1995-05-19', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0168', @PWHASH, '임건형', 'geonhyeong.im@daontech.co.kr', '010-4168-0930', '1989-09-30', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0169', @PWHASH, '오다인', 'dain.oh@daontech.co.kr', '010-4169-0125', '1998-01-25', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0170', @PWHASH, '남준서', 'junseo.nam@daontech.co.kr', '010-4170-0612', '2000-06-12', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0171', @PWHASH, '구서준', 'seojun.gu@daontech.co.kr', '010-4171-0308', '1999-03-08', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0040', @PWHASH, '정하윤', 'hayun.jeong@daontech.co.kr', '010-4040-1016', '1997-10-16', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2023-05-16', '2023-05-16 00:00:00', '2023-05-16 00:00:00'),
('DT22-0172', @PWHASH, '신태경', 'taegyeong.shin@daontech.co.kr', '010-4172-0704', '1988-07-04', 'MALE', 'BACKEND', 'SENIOR', 'ON_LEAVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0173', @PWHASH, '곽은서', 'eunseo.gwak@daontech.co.kr', '010-4173-1222', '1991-12-22', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0174', @PWHASH, '배시현', 'sihyeon.bae@daontech.co.kr', '010-4174-0429', '2001-04-29', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0213', @PWHASH, '유하람', 'haram.yu@daontech.co.kr', '010-4213-0917', '2000-09-17', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-09-01', '2022-09-01 00:00:00', '2022-09-01 00:00:00'),
('DT22-0176', @PWHASH, '조서영', 'seoyeong.jo@daontech.co.kr', '010-4176-0327', '1991-03-27', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0177', @PWHASH, '최민혁', 'minhyeok.choi@daontech.co.kr', '010-4177-1011', '1988-10-11', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0178', @PWHASH, '배윤서', 'yunseo.bae@daontech.co.kr', '010-4178-0819', '1994-08-19', 'FEMALE', 'BACKEND', 'SENIOR', 'ON_LEAVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0179', @PWHASH, '오태민', 'taemin.oh@daontech.co.kr', '010-4179-0105', '1990-01-05', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0041', @PWHASH, '정소윤', 'soyun.jeong@daontech.co.kr', '010-4041-0423', '1996-04-23', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2023-11-16', '2023-11-16 00:00:00', '2023-11-16 00:00:00'),
('DT22-0180', @PWHASH, '강현우', 'hyeonu.kang@daontech.co.kr', '010-4180-0714', '1999-07-14', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0181', @PWHASH, '임채원', 'chaewon.im@daontech.co.kr', '010-4181-1102', '1997-11-02', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0182', @PWHASH, '남도훈', 'dohun.nam@daontech.co.kr', '010-4182-0228', '2000-02-28', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0214', @PWHASH, '조은재', 'eunjae.jo@daontech.co.kr', '010-4214-0509', '1998-05-09', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-09-01', '2022-09-01 00:00:00', '2022-09-01 00:00:00'),
('DT23-0042', @PWHASH, '배하율', 'hayul.bae@daontech.co.kr', '010-4042-0816', '2001-08-16', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2023-11-16', '2023-11-16 00:00:00', '2023-11-16 00:00:00'),
('DT22-0184', @PWHASH, '문가연', 'gayeon.mun@daontech.co.kr', '010-4184-0217', '1989-02-17', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0185', @PWHASH, '최시훈', 'sihun.choi@daontech.co.kr', '010-4185-0625', '1992-06-25', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0186', @PWHASH, '임도현', 'dohyeon.im@daontech.co.kr', '010-4186-0913', '1995-09-13', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0187', @PWHASH, '배서현', 'seohyeon.bae@daontech.co.kr', '010-4187-1204', '1990-12-04', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0052', @PWHASH, '정우람', 'uram.jeong@daontech.co.kr', '010-4052-0321', '1993-03-21', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2024-07-16', '2024-07-16 00:00:00', '2024-07-16 00:00:00'),
('DT22-0188', @PWHASH, '곽하늘', 'haneul.gwak@daontech.co.kr', '010-4188-0708', '1999-07-08', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0058', @PWHASH, '남지우', 'jiu.nam@daontech.co.kr', '010-4058-1026', '2000-10-26', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2025-03-16', '2025-03-16 00:00:00', '2025-03-16 00:00:00'),
('DT22-0189', @PWHASH, '오건희', 'geonhui.oh@daontech.co.kr', '010-4189-0415', '1998-04-15', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0050', @PWHASH, '신다율', 'dayul.shin@daontech.co.kr', '010-4050-0131', '2001-01-31', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT22-0191', @PWHASH, '임동혁', 'donghyeok.im@daontech.co.kr', '010-4191-0814', '1988-08-14', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0192', @PWHASH, '조현아', 'hyeona.jo@daontech.co.kr', '010-4192-1103', '1991-11-03', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0193', @PWHASH, '배승우', 'seungu.bae@daontech.co.kr', '010-4193-0227', '1994-02-27', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0194', @PWHASH, '최하늘', 'haneul.choi@daontech.co.kr', '010-4194-1222', '1999-12-22', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0195', @PWHASH, '남기욱', 'giuk.nam@daontech.co.kr', '010-4195-0330', '1997-03-30', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0196', @PWHASH, '정예린', 'yerin.jeong@daontech.co.kr', '010-4196-0616', '1989-06-16', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0053', @PWHASH, '오승택', 'seungtaek.oh@daontech.co.kr', '010-4053-0909', '1996-09-09', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2024-07-16', '2024-07-16 00:00:00', '2024-07-16 00:00:00'),
('DT22-0197', @PWHASH, '강도연', 'doyeon.kang@daontech.co.kr', '010-4197-0711', '2000-07-11', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0215', @PWHASH, '임재원', 'jaewon.im@daontech.co.kr', '010-4215-1024', '1998-10-24', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-09-01', '2022-09-01 00:00:00', '2022-09-01 00:00:00'),
('DT22-0200', @PWHASH, '최재원', 'jaewon.choi@daontech.co.kr', '010-4200-0927', '1990-09-27', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0201', @PWHASH, '임소현', 'sohyeon.im@daontech.co.kr', '010-4201-0214', '1993-02-14', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0202', @PWHASH, '정가은', 'gaeun.jeong@daontech.co.kr', '010-4202-0508', '1988-05-08', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0056', @PWHASH, '남태윤', 'taeyun.nam@daontech.co.kr', '010-4056-1117', '1995-11-17', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2025-01-16', '2025-01-16 00:00:00', '2025-01-16 00:00:00'),
('DT22-0203', @PWHASH, '구재현', 'jaehyeon.gu@daontech.co.kr', '010-4203-0823', '1999-08-23', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0204', @PWHASH, '오소연', 'soyeon.oh@daontech.co.kr', '010-4204-0406', '2001-04-06', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0205', @PWHASH, '배준영', 'junyeong.bae@daontech.co.kr', '010-4205-0729', '1991-07-29', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0206', @PWHASH, '신유진', 'yujin.shin@daontech.co.kr', '010-4206-0112', '1998-01-12', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0051', @PWHASH, '임하진', 'hajin.im@daontech.co.kr', '010-4051-0325', '2000-03-25', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT25-0057', @PWHASH, '조은우', 'eunu.jo@daontech.co.kr', '010-4057-0618', '1997-06-18', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2025-01-16', '2025-01-16 00:00:00', '2025-01-16 00:00:00'),
('DT22-0208', @PWHASH, '배지환', 'jihwan.bae@daontech.co.kr', '010-4208-1015', '1989-10-15', 'MALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT26-0005', @PWHASH, '최서아', 'seoa.choi@daontech.co.kr', '010-4005-0830', '1994-08-30', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2026-02-16', '2026-02-16 00:00:00', '2026-02-16 00:00:00'),
('DT22-0209', @PWHASH, '임규호', 'gyuho.im@daontech.co.kr', '010-4209-1201', '1992-12-01', 'MALE', 'BACKEND', 'SENIOR', 'ON_LEAVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT26-0003', @PWHASH, '오단아', 'dana.oh@daontech.co.kr', '010-4003-0317', '1996-03-17', 'FEMALE', 'BACKEND', 'SENIOR', 'ACTIVE', '2026-01-16', '2026-01-16 00:00:00', '2026-01-16 00:00:00'),
('DT22-0210', @PWHASH, '정태균', 'taegyun.jeong@daontech.co.kr', '010-4210-0905', '1999-09-05', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0211', @PWHASH, '남서윤', 'seoyun.nam@daontech.co.kr', '010-4211-0628', '2001-06-28', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0212', @PWHASH, '배현준', 'hyeonjun.bae@daontech.co.kr', '010-4212-1111', '1998-11-11', 'MALE', 'BACKEND', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT26-0004', @PWHASH, '조유나', 'yuna.jo@daontech.co.kr', '010-4004-0524', '2000-05-24', 'FEMALE', 'BACKEND', 'STAFF', 'ACTIVE', '2026-01-16', '2026-01-16 00:00:00', '2026-01-16 00:00:00')
;

-- 품질보증 (QA) (25명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0020', @PWHASH, '정미르', 'mireu.jeong@daontech.co.kr', '010-4020-0403', '1984-04-03', 'FEMALE', 'QA', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0127', @PWHASH, '문가온', 'gaon.mun@daontech.co.kr', '010-4127-0418', '1986-04-18', 'FEMALE', 'QA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0128', @PWHASH, '하승우', 'seungwoo.ha@daontech.co.kr', '010-4128-1105', '1989-11-05', 'MALE', 'QA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0043', @PWHASH, '유이현', 'ihyeon.yu@daontech.co.kr', '010-4043-0722', '1990-07-22', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2024-02-01', '2024-02-01 00:00:00', '2024-02-01 00:00:00'),
('DT22-0129', @PWHASH, '조민아', 'mina.jo@daontech.co.kr', '010-4129-0209', '1991-02-09', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0130', @PWHASH, '백현준', 'hyeonjun.baek@daontech.co.kr', '010-4130-0830', '1988-08-30', 'MALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0131', @PWHASH, '임소민', 'somin.im@daontech.co.kr', '010-4131-0514', '1994-05-14', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0132', @PWHASH, '곽태민', 'taemin.gwak@daontech.co.kr', '010-4132-0927', '1990-09-27', 'MALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0133', @PWHASH, '신유하', 'yuha.shin@daontech.co.kr', '010-4133-0119', '1993-01-19', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0134', @PWHASH, '오단비', 'danbi.oh@daontech.co.kr', '010-4134-0608', '1998-06-08', 'FEMALE', 'QA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0135', @PWHASH, '최시우', 'siwoo.choi@daontech.co.kr', '010-4135-0325', '2000-03-25', 'MALE', 'QA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0050', @PWHASH, '배아린', 'arin.bae@daontech.co.kr', '010-4050-0901', '1999-09-01', 'FEMALE', 'QA', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT23-0034', @PWHASH, '남도현', 'dohyeon.nam@daontech.co.kr', '010-4034-1112', '1997-11-12', 'MALE', 'QA', 'STAFF', 'ACTIVE', '2023-08-16', '2023-08-16 00:00:00', '2023-08-16 00:00:00'),
('DT22-0136', @PWHASH, '정라온', 'raon.jeong@daontech.co.kr', '010-4136-1203', '1989-12-03', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0137', @PWHASH, '강지호', 'jiho.kang@daontech.co.kr', '010-4137-0416', '1992-04-16', 'MALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0138', @PWHASH, '윤하림', 'harim.yun@daontech.co.kr', '010-4138-0729', '1995-07-29', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0045', @PWHASH, '서준서', 'junseo.seo@daontech.co.kr', '010-4045-1008', '1991-10-08', 'MALE', 'QA', 'SENIOR', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT22-0139', @PWHASH, '문가율', 'gayul.mun@daontech.co.kr', '010-4139-0221', '1999-02-21', 'FEMALE', 'QA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0140', @PWHASH, '최윤재', 'yunjae.choi@daontech.co.kr', '010-4140-0517', '2001-05-17', 'MALE', 'QA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0049', @PWHASH, '배시은', 'sieun.bae@daontech.co.kr', '010-4049-0804', '1996-08-04', 'FEMALE', 'QA', 'STAFF', 'ACTIVE', '2025-05-01', '2025-05-01 00:00:00', '2025-05-01 00:00:00'),
('DT22-0141', @PWHASH, '하도훈', 'dohoon.ha@daontech.co.kr', '010-4141-0214', '1988-02-14', 'MALE', 'QA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0051', @PWHASH, '채유나', 'yuna.chae@daontech.co.kr', '010-4051-1230', '2000-12-30', 'FEMALE', 'QA', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT24-0044', @PWHASH, '임하율', 'haryul.im@daontech.co.kr', '010-4044-0411', '1998-04-11', 'MALE', 'QA', 'STAFF', 'ACTIVE', '2024-07-16', '2024-07-16 00:00:00', '2024-07-16 00:00:00'),
('DT26-0001', @PWHASH, '구태은', 'taeeun.gu@daontech.co.kr', '010-4001-0625', '1993-06-25', 'FEMALE', 'QA', 'SENIOR', 'ACTIVE', '2026-01-16', '2026-01-16 00:00:00', '2026-01-16 00:00:00'),
('DT23-0035', @PWHASH, '정소하', 'soha.jeong@daontech.co.kr', '010-4035-0109', '2002-01-09', 'FEMALE', 'QA', 'STAFF', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00')
;

-- 데브옵스 (DEVOPS) (16명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0021', @PWHASH, '최윤성', 'yunseong.choi@daontech.co.kr', '010-4021-0820', '1985-08-20', 'MALE', 'DEVOPS', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0047', @PWHASH, '하준서', 'junseo.ha@daontech.co.kr', '010-4047-0410', '1988-04-10', 'MALE', 'DEVOPS', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0048', @PWHASH, '오태양', 'taeyang.oh@daontech.co.kr', '010-4048-0715', '1992-07-15', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0055', @PWHASH, '지수민', 'sumin.ji@daontech.co.kr', '010-4055-0322', '1996-03-22', 'FEMALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-05-16', '2022-05-16 00:00:00', '2022-05-16 00:00:00'),
('DT23-0013', @PWHASH, '곽민성', 'minseong.gwak@daontech.co.kr', '010-4013-0518', '1993-05-18', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT22-0049', @PWHASH, '나건우', 'geonwoo.na@daontech.co.kr', '010-4049-0630', '1995-06-30', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0050', @PWHASH, '함서영', 'seoyeong.ham@daontech.co.kr', '010-4050-0117', '1986-01-17', 'FEMALE', 'DEVOPS', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0051', @PWHASH, '진태오', 'taeo.jin@daontech.co.kr', '010-4051-0325', '1991-03-25', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0057', @PWHASH, '안도훈', 'dohun.an@daontech.co.kr', '010-4057-0611', '1994-06-11', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-06-01', '2022-06-01 00:00:00', '2022-06-01 00:00:00'),
('DT22-0052', @PWHASH, '윤재형', 'jaehyeong.yun@daontech.co.kr', '010-4052-0526', '1987-05-26', 'MALE', 'DEVOPS', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0053', @PWHASH, '황시온', 'sion.hwang@daontech.co.kr', '010-4053-0311', '1993-03-11', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0019', @PWHASH, '추은결', 'eungyeol.chu@daontech.co.kr', '010-4019-0629', '1995-06-29', 'FEMALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2025-01-16', '2025-01-16 00:00:00', '2025-01-16 00:00:00'),
('DT23-0015', @PWHASH, '구민재', 'minjae.gu@daontech.co.kr', '010-4015-0208', '1994-02-08', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT25-0020', @PWHASH, '한도윤', 'doyun.han@daontech.co.kr', '010-4020-0714', '1989-07-14', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2025-04-01', '2025-04-01 00:00:00', '2025-04-01 00:00:00'),
('DT22-0054', @PWHASH, '탁현준', 'hyeonjun.tak@daontech.co.kr', '010-4054-0603', '1991-06-03', 'MALE', 'DEVOPS', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT25-0018', @PWHASH, '진아영', 'ayeong.jin@daontech.co.kr', '010-4018-0410', '2001-04-10', 'FEMALE', 'DEVOPS', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00')
;

-- 인프라운영 (INFRA) (18명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0004', @PWHASH, '하수민', 'sumin.ha@daontech.co.kr', '010-4004-1108', '1979-11-08', 'FEMALE', 'INFRA', 'EXECUTIVE', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0022', @PWHASH, '진병호', 'byeongho.jin@daontech.co.kr', '010-4022-0609', '1977-06-09', 'MALE', 'INFRA', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0058', @PWHASH, '지동원', 'dongwon.ji@daontech.co.kr', '010-4058-0312', '1985-03-12', 'MALE', 'INFRA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0061', @PWHASH, '방윤아', 'yuna.bang@daontech.co.kr', '010-4061-0614', '1993-06-14', 'FEMALE', 'INFRA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0064', @PWHASH, '하도경', 'dogyeong.ha@daontech.co.kr', '010-4064-0517', '1998-05-17', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0065', @PWHASH, '서인호', 'inho.seo@daontech.co.kr', '010-4065-0219', '1986-02-19', 'MALE', 'INFRA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0066', @PWHASH, '황준표', 'junpyo.hwang@daontech.co.kr', '010-4066-0613', '1990-06-13', 'MALE', 'INFRA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0067', @PWHASH, '임하늘', 'haneul.im@daontech.co.kr', '010-4067-0407', '1994-04-07', 'FEMALE', 'INFRA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0021', @PWHASH, '정소민', 'somin.jeong@daontech.co.kr', '010-4021-0219', '2000-02-19', 'FEMALE', 'INFRA', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT25-0024', @PWHASH, '곽민혁', 'minhyeok.gwak@daontech.co.kr', '010-4024-0603', '2002-06-03', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT24-0019', @PWHASH, '신재호', 'jaeho.shin@daontech.co.kr', '010-4019-0710', '1998-07-10', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2024-04-01', '2024-04-01 00:00:00', '2024-04-01 00:00:00'),
('DT22-0072', @PWHASH, '배현정', 'hyeonjeong.bae@daontech.co.kr', '010-4072-0416', '1987-04-16', 'FEMALE', 'INFRA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0073', @PWHASH, '한지석', 'jiseok.han@daontech.co.kr', '010-4073-0308', '1992-03-08', 'MALE', 'INFRA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0076', @PWHASH, '문가영', 'gayeong.mun@daontech.co.kr', '010-4076-0630', '1995-06-30', 'FEMALE', 'INFRA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0077', @PWHASH, '도영훈', 'yeonghun.do@daontech.co.kr', '010-4077-0511', '1999-05-11', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0022', @PWHASH, '주형진', 'hyeongjin.ju@daontech.co.kr', '010-4022-0325', '2000-03-25', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00'),
('DT25-0022', @PWHASH, '윤도현', 'dohyeon.yun@daontech.co.kr', '010-4022-0714', '1997-07-14', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2025-02-16', '2025-02-16 00:00:00', '2025-02-16 00:00:00'),
('DT25-0026', @PWHASH, '탁승민', 'seungmin.tak@daontech.co.kr', '010-4026-0409', '2002-04-09', 'MALE', 'INFRA', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00')
;

-- 데이터관리 (DATA) (19명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0023', @PWHASH, '고은결', 'eungyeol.go@daontech.co.kr', '010-4023-0914', '1982-09-14', 'FEMALE', 'DATA', 'TEAM_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0102', @PWHASH, '김태율', 'taeyul.kim@daontech.co.kr', '010-4102-0412', '1988-04-12', 'MALE', 'DATA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0103', @PWHASH, '백서윤', 'seoyun.baek@daontech.co.kr', '010-4103-1120', '1986-11-20', 'FEMALE', 'DATA', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0040', @PWHASH, '마예린', 'yerin.ma@daontech.co.kr', '010-4040-0214', '1991-02-14', 'FEMALE', 'DATA', 'SENIOR', 'ACTIVE', '2024-11-01', '2024-11-01 00:00:00', '2024-11-01 00:00:00'),
('DT22-0104', @PWHASH, '정하람', 'haram.jeong@daontech.co.kr', '010-4104-0708', '1993-07-08', 'FEMALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0105', @PWHASH, '강민준', 'minjun.kang@daontech.co.kr', '010-4105-1025', '1990-10-25', 'MALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0031', @PWHASH, '오승현', 'seunghyeon.oh@daontech.co.kr', '010-4031-0319', '1994-03-19', 'MALE', 'DATA', 'SENIOR', 'ACTIVE', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT25-0046', @PWHASH, '배지훈', 'jihun.bae@daontech.co.kr', '010-4046-0408', '1999-04-08', 'MALE', 'DATA', 'STAFF', 'ACTIVE', '2025-09-01', '2025-09-01 00:00:00', '2025-09-01 00:00:00'),
('DT22-0106', @PWHASH, '윤서아', 'seoa.yun@daontech.co.kr', '010-4106-1102', '1998-11-02', 'FEMALE', 'DATA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0107', @PWHASH, '최도현', 'dohyeon.choi@daontech.co.kr', '010-4107-0602', '1989-06-02', 'MALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0108', @PWHASH, '임채운', 'chaeun.im@daontech.co.kr', '010-4108-0914', '1996-09-14', 'MALE', 'DATA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0109', @PWHASH, '조은서', 'eunseo.jo@daontech.co.kr', '010-4109-1209', '1992-12-09', 'FEMALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0110', @PWHASH, '남준혁', 'junhyeok.nam@daontech.co.kr', '010-4110-0817', '1990-08-17', 'MALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0038', @PWHASH, '신재우', 'jaeu.shin@daontech.co.kr', '010-4038-0228', '2000-02-28', 'MALE', 'DATA', 'STAFF', 'ACTIVE', '2024-06-01', '2024-06-01 00:00:00', '2024-06-01 00:00:00'),
('DT22-0111', @PWHASH, '황도현', 'dohyeon.hwang@daontech.co.kr', '010-4111-0130', '1988-01-30', 'MALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0112', @PWHASH, '구현우', 'hyeonu.gu@daontech.co.kr', '010-4112-0506', '1997-05-06', 'MALE', 'DATA', 'STAFF', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0041', @PWHASH, '박시안', 'sian.park@daontech.co.kr', '010-4041-1019', '1999-10-19', 'MALE', 'DATA', 'STAFF', 'ACTIVE', '2024-11-16', '2024-11-16 00:00:00', '2024-11-16 00:00:00'),
('DT22-0113', @PWHASH, '정유빈', 'yubin.jeong@daontech.co.kr', '010-4113-0511', '1993-05-11', 'FEMALE', 'DATA', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0039', @PWHASH, '하은우', 'eunu.ha@daontech.co.kr', '010-4039-0115', '2001-01-15', 'MALE', 'DATA', 'STAFF', 'ACTIVE', '2024-09-02', '2024-09-02 00:00:00', '2024-09-02 00:00:00')
;

-- 정보보안 (SECURITY) (12명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT22-0007', @PWHASH, '권영달', 'yeongdal.gwon@daontech.co.kr', '010-4007-0630', '1973-06-30', 'MALE', 'SECURITY', 'EXECUTIVE', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0024', @PWHASH, '류경호', 'gyeongho.ryu@daontech.co.kr', '010-4024-0327', '1980-03-27', 'MALE', 'SECURITY', 'TEAM_LEADER', 'ACTIVE', '2022-03-16', '2022-03-16 00:00:00', '2022-03-16 00:00:00'),
('DT22-0039', @PWHASH, '서준혁', 'junhyeok.seo@daontech.co.kr', '010-4039-0315', '1988-03-15', 'MALE', 'SECURITY', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0040', @PWHASH, '표건우', 'geonwoo.pyo@daontech.co.kr', '010-4040-0620', '1991-06-20', 'MALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0045', @PWHASH, '마준영', 'junyeong.ma@daontech.co.kr', '010-4045-0410', '1994-04-10', 'MALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2022-08-16', '2022-08-16 00:00:00', '2022-08-16 00:00:00'),
('DT23-0010', @PWHASH, '신유빈', 'yubin.shin@daontech.co.kr', '010-4010-0512', '1996-05-12', 'FEMALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2023-02-01', '2023-02-01 00:00:00', '2023-02-01 00:00:00'),
('DT22-0041', @PWHASH, '백승아', 'seunga.baek@daontech.co.kr', '010-4041-0223', '1987-02-23', 'FEMALE', 'SECURITY', 'PART_LEADER', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0042', @PWHASH, '우진성', 'jinseong.woo@daontech.co.kr', '010-4042-0715', '1992-07-15', 'MALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0043', @PWHASH, '장은비', 'eunbi.jang@daontech.co.kr', '010-4043-0509', '1995-05-09', 'FEMALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT22-0044', @PWHASH, '정민혁', 'minhyeok.jeong@daontech.co.kr', '010-4044-0604', '1989-06-04', 'MALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT23-0011', @PWHASH, '구자헌', 'jaheon.gu@daontech.co.kr', '010-4011-0214', '1994-02-14', 'MALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2023-07-01', '2023-07-01 00:00:00', '2023-07-01 00:00:00'),
('DT24-0010', @PWHASH, '최도영', 'doyeong.choi@daontech.co.kr', '010-4010-0128', '1998-01-28', 'MALE', 'SECURITY', 'SENIOR', 'ACTIVE', '2024-03-16', '2024-03-16 00:00:00', '2024-03-16 00:00:00')
;

-- 퇴사자 7명 (RESIGNED, 정원 280 밖) (7명)
INSERT INTO users (employee_no, password_hash, name, email, phone_number, birth_date, gender, department, position, status, hire_date, created_at, modified_at) VALUES
('DT23-0043', @PWHASH, '한지섭', 'jiseop.han@daontech.co.kr', '010-4043-0512', '1994-05-12', 'MALE', 'SECURITY', 'SENIOR', 'RESIGNED', '2023-01-16', '2023-01-16 00:00:00', '2023-01-16 00:00:00'),
('DT22-0216', @PWHASH, '윤태호', 'taeho.yun@daontech.co.kr', '010-4216-0308', '1990-03-08', 'MALE', 'CONSULTING', 'SENIOR', 'RESIGNED', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00'),
('DT24-0054', @PWHASH, '곽서진', 'seojin.gwak@daontech.co.kr', '010-4054-0921', '1993-09-21', 'FEMALE', 'BACKEND', 'SENIOR', 'RESIGNED', '2024-02-16', '2024-02-16 00:00:00', '2024-02-16 00:00:00'),
('DT23-0044', @PWHASH, '표민찬', 'minchan.pyo@daontech.co.kr', '010-4044-0130', '1998-01-30', 'MALE', 'PM', 'STAFF', 'RESIGNED', '2023-09-01', '2023-09-01 00:00:00', '2023-09-01 00:00:00'),
('DT24-0055', @PWHASH, '여도경', 'dogyeong.yeo@daontech.co.kr', '010-4055-1114', '1991-11-14', 'FEMALE', 'CONSULTING', 'SENIOR', 'RESIGNED', '2024-08-16', '2024-08-16 00:00:00', '2024-08-16 00:00:00'),
('DT23-0045', @PWHASH, '주하빈', 'habin.ju@daontech.co.kr', '010-4045-0603', '1996-06-03', 'FEMALE', 'SALES', 'STAFF', 'RESIGNED', '2023-05-16', '2023-05-16 00:00:00', '2023-05-16 00:00:00'),
('DT22-0217', @PWHASH, '남건율', 'geonyul.nam@daontech.co.kr', '010-4217-0819', '1992-08-19', 'MALE', 'DEVOPS', 'SENIOR', 'RESIGNED', '2022-03-02', '2022-03-02 00:00:00', '2022-03-02 00:00:00')
;

-- =============================================================================
-- 검증 쿼리 (로드 후 확인용 — 실행 안 해도 무방)
-- =============================================================================
-- SELECT COUNT(*) FROM users;                                    -- 287
-- SELECT status, COUNT(*) FROM users GROUP BY status;             -- ACTIVE 277 / ON_LEAVE 3 / RESIGNED 7
-- SELECT department, COUNT(*) FROM users WHERE status <> 'RESIGNED' GROUP BY department;
--   -- MANAGEMENT_SUPPORT 11 / HR 8 / FINANCE 8 / SALES 13 / PLANNING 11 / CONSULTING 17 / PM 19
--   -- FRONTEND 35 / BACKEND 68 / QA 25 / DEVOPS 16 / INFRA 18 / DATA 19 / SECURITY 12  (합 280)
-- SELECT position, COUNT(*) FROM users GROUP BY position;
