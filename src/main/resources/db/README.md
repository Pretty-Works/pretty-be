# DB 리셋 & 데이터 로드 절차

로컬 개발용 MySQL(Docker)을 **초기화하고 스키마·시드를 다시 넣는** 절차입니다.
`ddl-auto: validate` 라서 스키마가 엔티티와 어긋나면 앱이 안 뜨므로, 스키마가 바뀌었거나 데이터를
깨끗이 다시 넣고 싶을 때 이 순서대로 실행하세요.

> 실행 위치: **PowerShell**, 프로젝트 루트 `C:\Users\tnals\Desktop\PrettyWorks_BE`

---

## 0) 컨테이너 이름 확인

```powershell
docker compose ps
```

`NAME` 열의 mysql 컨테이너 이름(예: `prettyworks_be-mysql-1`)을 아래 명령의 `<name>` 자리에 넣으세요.

## 1) DB 비우기 — 통째 재생성 (제일 깔끔)

```powershell
docker exec -i <name> sh -c "mysql -uroot -p1234 -e 'DROP DATABASE IF EXISTS prettyworks_test; CREATE DATABASE prettyworks_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'"
```

<details><summary>DB는 유지하고 테이블만 지우려면 (대안)</summary>

```powershell
docker exec -i <name> sh -c "mysql -uroot -p1234 prettyworks_test -e 'SET FOREIGN_KEY_CHECKS=0; DROP TABLE IF EXISTS agent_interactions, agent_events, agent_runs, agent_message_steps, agent_message_attachments, agent_messages, agent_conversations, notifications, idempotency_keys, expenses, schedule_participants, schedule_leaves, schedules, meeting_attendees, meetings, project_posts, tasks, milestones, project_members, refresh_tokens, leave_balances, projects, users; SET FOREIGN_KEY_CHECKS=1;'"
```
FK 때문에 `SET FOREIGN_KEY_CHECKS=0` 없이는 부모 테이블을 못 지웁니다.

> ⚠️ **테이블을 새로 추가했다면 이 목록에도 반드시 넣으세요.** `init.sql`이 `CREATE TABLE IF NOT EXISTS`라서,
> 빠뜨린 테이블은 지워지지 않고 남았다가 재로드 때 그대로 건너뛰어집니다. 그러면 스키마가 옛 상태로 남아
> `ddl-auto: validate`에서 앱이 안 뜨거나, 더 나쁘게는 어긋난 채로 뜹니다. 헷갈리면 1번(DB 통째 재생성)을 쓰세요.
</details>

## 2) 스키마 로드 (init.sql)

```powershell
docker cp src\main\resources\db\init.sql <name>:/tmp/init.sql
docker exec -i <name> sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test < /tmp/init.sql"
```

## 3) 시드 로드 (seed.sql)

```powershell
docker cp src\main\resources\db\seed.sql <name>:/tmp/seed.sql
docker exec -i <name> sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test < /tmp/seed.sql"
```

## 4) 확인

```powershell
docker exec -i <name> sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 prettyworks_test -e 'SELECT id, content FROM tasks WHERE id=1; SELECT id, name FROM users WHERE id=1;'"
```

`스프린트 리뷰 안건 취합`, `김피엠` 처럼 한글이 제대로 나오면 성공.

---

## ⚠️ 한글 안 깨지는 핵심 규칙

| | |
|---|---|
| ✅ | `docker cp` 로 파일을 컨테이너에 넣고 → `docker exec` 안에서 `< /tmp/파일` 로 실행 |
| ✅ | 로드 시 `--default-character-set=utf8mb4` 꼭 붙이기 |
| ❌ | `Get-Content ... \| docker ...` — PowerShell 파이프가 바이트를 재인코딩해서 한글이 깨짐 |
| ❌ | `docker compose cp` — 가끔 파일이 컨테이너에 안 들어감. `docker cp`(컨테이너 이름 직접 지정)를 쓸 것 |

## 참고

- 비밀번호: 시드의 전 사용자 비밀번호는 `Test1234!` (BCrypt 해시).
- 순서: 반드시 **init.sql(스키마) → seed.sql(데이터)** 순. seed.sql은 빈 테이블 기준으로 id가
  `users 1~10`, `projects 1~5` … 순서대로 부여되는 것을 전제로 FK를 참조합니다.
- 날짜: seed의 모든 날짜는 `CURDATE()` 기준 **상대값**(`DATE_SUB(CURDATE(), INTERVAL n DAY)` 등)이라
  언제 로드해도 "진행 중 프로젝트", "이번 주 할 일" 같은 상태가 그대로 재현됩니다. 고정 날짜를 새로
  넣지 마세요 — 시간이 지나면 시드가 낡아 테스트가 어긋납니다.
