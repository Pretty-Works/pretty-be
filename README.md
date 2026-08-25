# PrettyWorks Backend

사내 업무의 후속 처리를 AI Agent가 이어받는 협업 플랫폼 **PrettyWorks**의 API 서버입니다.
Agent는 데이터를 직접 바꾸지 않고 **변경안**을 만들며, 사용자 승인을 거쳐야 반영됩니다.
이 저장소는 인증 · 도메인 데이터 · Agent 게이트웨이를 담당합니다.

REST 92개 · 테이블 26개 · 테스트 35개

- **전체 소개** — [조직 프로필](https://github.com/Pretty-Works) · 서비스 · 아키텍처 · 팀
- **라이브** — [api.prettyworks.co.kr](https://api.prettyworks.co.kr)
- **형제 저장소** — [pretty-fe](https://github.com/Pretty-Works/pretty-fe) · [pretty-llm](https://github.com/Pretty-Works/pretty-llm)
- **Backend** — [김수민](https://github.com/soomink210) · [박지원](https://github.com/jiwonpark01) · [이민주](https://github.com/minju0236)

---

## 프로젝트 구조

```
HK/PrettyWorks_BE/
├── agent/            AI Agent 연동
├── auth/             로그인 · 토큰 발급 · 세션
├── security/         인증 필터 · 인가 설정
├── user/             사용자 · 계정
├── project/          프로젝트 · 멤버 · 마일스톤 · 게시글 · 회의록 · 재무
├── task/             할 일
├── calendar/         일정 · 휴가 · 연차
├── notification/     알림
├── replan/           일정 재계획 시나리오
├── idempotency/      멱등 키
└── global/           공통 응답 · 예외 · 설정 · 필터
```

`agent` 패키지만 계층을 한 겹 더 나눴습니다.

```
agent/
├── conversation/     대화 · 메시지 이력
├── execution/        실행 상태 머신 · SSE 릴레이 · FastAPI 게이트웨이
├── interaction/      승인 · 질문 카드
├── tool/             FastAPI가 호출하는 내부 도구 API (조회 16 · 쓰기 12)
├── suggestion/       제안 생성
├── summary/          프로젝트 요약
├── meetingdraft/     회의록 초안
└── shared/           첨부 · 한도 · 내부 인증
```

각 모듈은 `api / application / domain / persistence`로 나뉘고, FastAPI를 호출하는 모듈에는 `gateway`가 추가됩니다. 다른 도메인은 `controller / service / repository` 구조입니다.

---

## Agent 연동

Agent 추론은 FastAPI가 담당하고, 이 서버는 그 앞단에서 인증 · 권한 · 실행 상태를 관리합니다.

### 신뢰 경계

```
FE  →  Spring  →  FastAPI  →  Spring  →  FE
```

프론트엔드는 AI 서버를 직접 호출하지 않습니다. **Spring이 유일한 신뢰 계층**이고 FastAPI는 내부망에서 Spring하고만 통신합니다. 사용자 JWT는 FastAPI로 넘기지 않습니다 — LLM은 신뢰할 수 없는 입력원이므로 Agent가 자격증명을 들고 있으면 프롬프트 조작으로 타인의 데이터에 접근할 여지가 생깁니다.

`/api/internal/agent/**`는 세 헤더로 판정합니다.

| 헤더 | 검증 |
|---|---|
| `X-Internal-Api-Key` | 호출자가 등록된 AI 서버인가 |
| `X-Run-Id` | 어떤 실행인가 — 서버가 Run을 조회해 `user_id`를 역산 |
| `X-Approval-Token` | 사용자 승인이 있었는가 — 쓰기 요청에만 검사 |

사용자를 식별하는 경로는 `X-Run-Id` 하나뿐입니다. **Agent는 자신이 어떤 실행 중인지만 알고, 그 실행의 주인이 누구인지는 서버가 결정합니다.** 요청 본문에 임의의 `user_id`를 넣어도 사용되지 않습니다.

### 실행 격리와 용량

FastAPI의 SSE를 소비하는 작업은 블로킹이라 톰캣 요청 스레드에서 처리하면 웹 요청이 함께 막힙니다. 전용 스레드 풀로 분리했습니다.

| 값 | 근거 |
|---|---|
| `execution.threads: 24` | 세그먼트 평균 80초, 임직원 500명 피크 기준 동시 27건 → 이를 덮는 크기 |
| `queue-capacity: 100` | 스레드를 못 받은 실행의 대기열 |
| `hikari.maximum-pool-size: 40` | 실행 24가 최악으로 다 물어도 **웹 요청 몫 16이 남도록** |
| `run.max-active-total: 32` | 실행 스레드보다 8건 위 — 같게 두면 큐를 못 쓰고, 크게 두면 빈 화면이 늘어남 |

커넥션 풀은 사용자 수가 아니라 **동시에 도는 쿼리 수**로 정했습니다. 커넥션은 쿼리가 도는 동안만 잡히기 때문입니다.

### SSE

| 항목 | 값 |
|---|---|
| 하트비트 | 15초 |
| 세그먼트 타임아웃 | 10분 |
| 사용자당 동시 연결 | 5 |
| 이벤트 보존 | 1시간 — 종료 직전 끊긴 브라우저가 `done`/`error`를 재생할 수 있도록 |

**DB가 이벤트 원본이고 Redis는 다중 인스턴스에 새 시퀀스를 알리는 용도로만** 씁니다. Redis 발행이 실패해도 이벤트는 DB에 남아 있고, 다른 인스턴스가 다음 하트비트 때 놓친 시퀀스를 따라잡습니다. 전달이 최대 15초 늦어질 뿐 유실되지 않습니다.

### 승인 게이트

데이터를 바꾸는 Agent 요청은 사용자 승인 없이 반영되지 않습니다. 승인 시 발급되는 토큰은 **원문을 DB에 저장하지 않고** HMAC으로 재계산하므로 서버가 재시작해도 같은 토큰이 유효합니다.

| 항목 | 값 |
|---|---|
| 승인 토큰 TTL | 10분 |
| 승인 · 질문 응답 대기 | 12시간 — 밤새 돌려두고 아침에 승인하는 흐름 |
| 실행 수명 상한 | 36시간 |

응답 대기(12시간)는 실행 수명(36시간)보다 짧아야 합니다. 길면 실행이 먼저 죽어 **답할 수 없는 카드만 홈 화면에 남습니다.**

---

## API 명세

- **Swagger UI** — `/swagger-ui.html` (로컬 기동 후)
- **명세서** — [팀 Notion](https://app.notion.com/p/API-28898c017c1683b797bf01bbadbc55c9)

외부 64개 · Agent 내부 도구 28개

| 도메인 | 개수 | 범위 |
|---|---|---|
| agent | 43 | 외부 15 · 내부 도구 28 |
| project | 23 | 프로젝트 · 멤버 · 마일스톤 · 게시글 · 회의록 · 재무 |
| calendar | 10 | 일정 · 휴가 · 연차 |
| task | 6 | 할 일 |
| auth | 4 | 로그인 · 로그아웃 · 토큰 재발급 |
| notification | 4 | 알림 |
| user | 2 | 사용자 |

### 응답 형식

모든 응답은 `ResponseBodyAdvice`가 `BaseResponse`로 감쌉니다. springdoc은 컨트롤러 반환 타입만 보기 때문에 문서에는 감싸지 않은 알맹이만 나오는데, 그대로 두면 프론트가 `result`를 빠뜨린 파싱 코드를 짜게 됩니다. `SwaggerConfig`의 `OpenApiCustomizer`가 스키마를 한 겹 감싸 **문서와 실제 응답을 일치**시킵니다.

### 내부 도구 API

FastAPI가 호출하는 경로입니다. **공개 API 문서에서 제외**(`springdoc.paths-to-exclude`)하므로 아래 목록이 유일한 명세입니다 — 엔드포인트를 추가하면 이 표도 함께 갱신해야 합니다.

<details>
<summary><b>조회 16개</b> — <code>X-Internal-Api-Key</code> + <code>X-Run-Id</code></summary>

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/internal/agent/me` | 호출자 본인 정보 |
| GET | `/api/internal/agent/users` | 사용자 검색 |
| GET | `/api/internal/agent/projects` | 프로젝트 검색 |
| GET | `/api/internal/agent/projects/{projectId}/members` | 참여자 |
| GET | `/api/internal/agent/projects/{projectId}/milestones` | 마일스톤 |
| GET | `/api/internal/agent/projects/{projectId}/posts` | 게시글 목록 |
| GET | `/api/internal/agent/projects/{projectId}/posts/{postId}` | 게시글 상세 |
| GET | `/api/internal/agent/projects/{projectId}/meetings` | 회의록 목록 |
| GET | `/api/internal/agent/projects/{projectId}/meetings/{meetingId}` | 회의록 상세 |
| GET | `/api/internal/agent/projects/{projectId}/budget` | 예산 |
| GET | `/api/internal/agent/projects/{projectId}/expenses` | 지출 내역 |
| GET | `/api/internal/agent/tasks` | 할 일 |
| GET | `/api/internal/agent/schedules` | 일정 |
| GET | `/api/internal/agent/leaves` | 휴가 |
| GET | `/api/internal/agent/leaves/balance` | 연차 잔여 |
| GET | `/api/internal/agent/runs/{runId}/user` | 실행 소유자 조회 — Gmail MCP 전용 |

</details>

<details>
<summary><b>쓰기 12개</b> — 위 두 헤더 + <code>X-Approval-Token</code></summary>

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/internal/agent/tasks` | 할 일 생성 |
| PATCH | `/api/internal/agent/tasks/{taskId}/status` | 할 일 상태 변경 |
| POST | `/api/internal/agent/projects/{projectId}/meetings` | 회의록 생성 |
| POST | `/api/internal/agent/projects/{projectId}/posts` | 게시글 생성 |
| POST | `/api/internal/agent/projects/{projectId}/expenses` | 지출 등록 |
| PATCH | `/api/internal/agent/projects/{projectId}/milestones/{milestoneId}/status` | 마일스톤 상태 변경 |
| POST | `/api/internal/agent/schedules` | 일정 생성 |
| PATCH | `/api/internal/agent/schedules/{scheduleId}` | 일정 수정 |
| POST | `/api/internal/agent/leaves` | 휴가 신청 |
| PATCH | `/api/internal/agent/leaves/{leaveId}` | 휴가 수정 |
| POST | `/api/internal/agent/projects/{projectId}/replans` | 재계획 시나리오 생성 |
| POST | `/api/internal/agent/projects/{projectId}/replans/{replanId}/apply` | 재계획 적용 |

</details>

---

## 데이터 모델

26개 테이블. 스키마 정본은 [`db/init.sql`](src/main/resources/db/init.sql)입니다.

| 영역 | 테이블 |
|---|---|
| 사용자 · 인증 | `users` `refresh_tokens` `leave_balances` |
| 프로젝트 | `projects` `project_members` `milestones` `project_posts` `project_summaries` |
| 업무 | `tasks` `meetings` `meeting_attendees` `expenses` |
| 일정 | `schedules` `schedule_participants` `schedule_leaves` |
| Agent | `agent_conversations` `agent_messages` `agent_message_steps` `agent_message_attachments` `agent_runs` `agent_events` `agent_interactions` |
| 재계획 | `replans` `replan_scenarios` |
| 공통 | `notifications` `idempotency_keys` |

전체 ERD는 [조직 프로필](https://github.com/Pretty-Works)에서 볼 수 있습니다.

데모 데이터는 가상 회사의 조직도 · 인물 · 사건 연대기를 먼저 설계한 뒤 그 설정을 따라 생성했습니다. 설계 자료는 [`docs/company/`](docs/company), 생성 기준은 [`db/DEMO_DATA_GUIDE.md`](src/main/resources/db/DEMO_DATA_GUIDE.md)에 있습니다.

---

## 테스트

```bash
./gradlew test
```

테스트 35개 중 32개가 `agent` 패키지입니다.

| 대상 | 테스트 |
|---|---|
| 신뢰 경계 | `InternalAgentFilterTest` — 내부 API 키 · Run ID 판정 |
| 승인 게이트 | `ApprovalTokenServiceTest` · `AgentInteractionResolutionServiceTest` · `AgentResumeServiceTest` |
| 실행 · 용량 | `AgentRunFactoryTest` — 개인/전체 동시 실행 한도 |
| SSE 릴레이 | `AgentServerSseParserTest` · `AgentServerEventDecoderTest` · `AgentServerClientStreamingTest` |
| 멱등 | `ParamsCanonicalizerTest` — 파라미터 정규화 해시 |
| 내부 도구 | `AgentTaskToolServiceTest` 등 도메인별 6종 |
| 승인 카드 미리보기 | `TaskCreatePreviewRendererTest` 등 3종 |

---

## 설계 기록

| 분류 | 문서 |
|---|---|
| **Agent 연동** | [실행을 별도 스레드 풀로 격리](https://app.notion.com/p/3bf574a060ac802faf2be238eb43506a) · [승인 게이트와 Approval Token](https://app.notion.com/p/3bf574a060ac803d937ff7ccb65de35f) · [Run 상태 관리와 SSE 재연결](https://app.notion.com/p/3bf574a060ac806ebb77cc63da829313) |
| **데이터 관리** | [도메인 간 참조를 ID로 고정](https://app.notion.com/p/3bf574a060ac80739d37faea3c8a469d) · [알림 저장의 트랜잭션 경계](https://app.notion.com/p/3bf574a060ac80b98a19d785b88a9f64) · [중복 생성과 동시 수정](https://app.notion.com/p/3bf574a060ac8033ad78c29ec92101da) |
| **인증 · 세션** | [JWT 세션 무효화 — 차단 단위](https://app.notion.com/p/3bf574a060ac80cb9f39c21694e48601) · [로그아웃 — 서버 세션과 클라이언트 캐시](https://app.notion.com/p/3bf574a060ac80e9903fe3faf825acc1) |
| **도메인 규칙** | [권한 규칙의 Policy 분리](https://app.notion.com/p/3bf574a060ac8090b1cbeaa4174d1070) · [마일스톤 순서 불변식](https://app.notion.com/p/3bf574a060ac803d9e34c07178ef5b96) · [할 일 권한](https://app.notion.com/p/3bf574a060ac806aa867cafa805449ff) |

> 전체 문서 — [PrettyWorks 개발 기록](https://app.notion.com/p/PrettyWorks-AI-3bf574a060ac803abd3ce27f7e73e437)

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.1.0 · Spring Security · Spring Data JPA · Validation · Actuator |
| **Auth** | JJWT 0.13 (Access / Refresh Token Rotation) |
| **Data** | MySQL 8.4 · Redis (Cache · Pub/Sub) |
| **Docs** | SpringDoc OpenAPI 3.0 |
| **Infra** | Docker Compose · Caddy · AWS EC2 · RDS |

> 배포 구성과 CI/CD 흐름 다이어그램은 [조직 프로필](https://github.com/Pretty-Works)에 있습니다.

---

## 로컬 실행

### 1. 사전 요구사항

| 도구 | 요구사항 |
|---|---|
| JDK | 21 |
| MySQL | 8.4 (Docker Compose 제공) |
| Redis | 선택 — 없어도 단일 인스턴스로 동작 |
| Node.js | 선택 — 시드 정합성 검사 스크립트용 |

### 2. 설정 파일

설정은 `application.yml`에 있고 **시크릿만 `.env`로 주입**합니다. `.env`만 만들면 됩니다.

```bash
cp .env.example .env
```

`application.yml`의 `spring.config.import`가 `.env`를 읽어 `${...}` 참조를 해결합니다. optional 접두사라 `.env`가 없는 환경(Docker 등)에서는 컨테이너 환경변수를 그대로 씁니다.

`.env`에서 값이 비어 있는 항목은 반드시 채워야 앱이 뜹니다.

| 키 | 생성 |
|---|---|
| `JWT_SECRET_KEY` | 랜덤 64 byte → Base64 |
| `AGENT_INTERNAL_API_KEY` | 랜덤 32 byte → Base64 |
| `AGENT_APPROVAL_TOKEN_SECRET` | 랜덤 32 byte → Base64 |

```bash
openssl rand -base64 64 | tr -d '\n'
```

> `AGENT_INTERNAL_API_KEY`는 pretty-llm의 `INTERNAL_API_KEY`와 **같은 값**이어야 합니다. 한쪽만 바꾸면 Agent 연동이 끊깁니다.

### 3. 데이터베이스

`ddl-auto: validate`라서 **스키마가 엔티티와 어긋나면 앱이 뜨지 않습니다.** 의도된 안전장치이므로 스키마 정본(`db/init.sql`)을 먼저 로드해야 합니다.

```bash
docker compose up -d mysql
```

스키마와 시드 로드 절차는 [`db/README.md`](src/main/resources/db/README.md) 참고. 시드 데이터의 모든 날짜는 `CURDATE()` 기준 상대값이라 언제 로드해도 "진행 중 프로젝트", "이번 주 할 일" 상태가 그대로 재현됩니다.

### 4. 실행

```bash
./gradlew bootRun
```

- 서버 — http://localhost:8080
- Swagger UI — http://localhost:8080/swagger-ui.html

### 5. Agent 서버 없이 띄우기

pretty-llm(FastAPI)이 없어도 BE는 정상 기동합니다. Agent 관련 요청만 실패하고 나머지 도메인 API는 모두 동작하므로, 프론트엔드 개발이나 도메인 API 확인은 BE 단독으로 가능합니다.

Agent 기능까지 확인하려면 [pretty-llm](https://github.com/Pretty-Works/pretty-llm)을 3002 포트에 띄우고 `AGENT_SERVER_URL`을 맞춥니다.
