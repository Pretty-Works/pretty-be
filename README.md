# PrettyWorks Backend

사내 업무의 후속 처리를 AI Agent가 이어받는 협업 플랫폼 **PrettyWorks**의 API 서버입니다.
Agent는 데이터를 직접 바꾸지 않습니다. **변경안**을 만들고 사용자가 승인해야 반영됩니다.
이 저장소는 인증, 도메인 데이터, Agent 게이트웨이를 맡습니다.

REST 92개 · 테이블 26개 · 테스트 35개

- **전체 소개** — [조직 프로필](https://github.com/Pretty-Works) · 서비스 · 화면 · 팀
- **데모 영상** — [YouTube](https://youtu.be/3elt4EvM3Dg)
- **형제 저장소** — [pretty-fe](https://github.com/Pretty-Works/pretty-fe) · [pretty-llm](https://github.com/Pretty-Works/pretty-llm)

| 이름 | 역할 | 담당 기능 |
|---|---|---|
| [김수민](https://github.com/soomink210) | Project Lead | 아키텍처 설계 · 인증/인가 · Agent 연동 · 프로젝트/할 일/재무/알림 |
| [박지원](https://github.com/jiwonpark01) | Full-stack Developer | 토큰 인증 개선 · 일정/휴가/계정 · Agent 내부 Tool · Gmail 연동 |
| [이민주](https://github.com/minju0236) | Frontend Tech Lead | 회의록/게시판 · 회의록 초안 · AI 요약 배너 · Agent 대화 목록 |

---

## 시스템 아키텍처

![System Architecture](https://raw.githubusercontent.com/Pretty-Works/.github/main/profile/img/architecture.png)

이 저장소는 그림에서 EC2 안의 **Spring Boot** 영역입니다.

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.1.0 · Spring Security · Spring Data JPA · Validation · Actuator |
| **Auth** | JJWT 0.13 (Access / Refresh Token Rotation) |
| **Data** | MySQL 8.4 (운영 RDS · 로컬 8.0) · Redis (Pub/Sub · 캐시 · 세션 차단 목록) |
| **Docs** | SpringDoc OpenAPI 3.0 |
| **Infra** | Docker Compose · Caddy · AWS EC2 · RDS |

---

## Agent 연동

Agent 추론은 FastAPI가 맡습니다. 이 서버는 그 앞에서 인증과 권한을 확인하고 실행 상태를 관리합니다.

### 신뢰 경계

```
FE  →  Spring  →  FastAPI  →  Spring  →  FE
```

**Spring이 유일한 신뢰 계층**입니다. 프론트엔드는 AI 서버를 직접 호출하지 않고, 사용자 JWT도 FastAPI로 넘기지 않습니다. Agent가 자격증명을 들고 있으면 프롬프트 조작으로 타인의 데이터에 접근할 여지가 생기기 때문입니다.

`/api/internal/agent/**`는 세 헤더로 판정합니다.

| 헤더 | 검증 |
|---|---|
| `X-Internal-Api-Key` | 호출자가 등록된 AI 서버인가 |
| `X-Run-Id` | 어떤 실행인가 — 서버가 Run을 조회해 `user_id`를 역산 |
| `X-Approval-Token` | 사용자 승인이 있었는가 — 쓰기 요청에만 검사 |

사용자는 **`X-Run-Id`로만 식별**합니다. 요청 본문의 `user_id`는 쓰지 않습니다.

### 실행 격리와 용량

SSE 수신은 블로킹이라 톰캣 요청 스레드와 분리된 전용 스레드 풀에서 처리합니다.

| 값 | 근거 |
|---|---|
| `execution.threads: 24` | 피크 동시 27건 기준 (세그먼트 평균 80초 · 임직원 500명) |
| `queue-capacity: 100` | 스레드를 못 받은 실행의 대기열 |
| `hikari.maximum-pool-size: 40` | 실행 24가 다 물어도 **웹 요청 몫 16이 남도록** |
| `run.max-active-total: 32` | 스레드보다 8건 여유 — 같으면 큐를 못 쓰고, 크면 빈 화면으로 기다리는 실행이 늘어남 |

### SSE

| 항목 | 값 |
|---|---|
| 하트비트 | 15초 |
| 세그먼트 타임아웃 | 10분 |
| 사용자당 동시 연결 | 5 |
| 이벤트 보존 | 1시간 — 끊긴 브라우저도 `done`/`error`를 다시 받도록 |

**DB가 이벤트 원본**이고 Redis는 다른 인스턴스에 새 이벤트를 알리는 데만 씁니다. Redis가 실패해도 다음 하트비트에 따라잡으므로 최대 15초 늦어질 뿐 유실되지 않습니다.

### 승인 게이트

데이터를 바꾸는 Agent 요청은 사용자 승인 후에만 반영됩니다. 승인 토큰은 **원문을 저장하지 않고** HMAC으로 다시 계산하므로 서버가 재시작해도 유효합니다.

| 항목 | 값 |
|---|---|
| 승인 토큰 TTL | 10분 |
| 승인 · 질문 응답 대기 | 12시간 — 밤새 돌려두고 아침에 승인하는 흐름 |
| 실행 수명 상한 | 36시간 |

응답 대기를 실행 수명보다 짧게 잡아, 답할 수 없는 카드가 홈 화면에 남지 않게 했습니다.

---

## API 명세

외부 API 64개는 Swagger UI(`/swagger-ui.html`), Agent 내부 도구 28개는 [아래 표](#내부-도구-api)에 있습니다.

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

모든 응답은 `ResponseBodyAdvice`가 `BaseResponse`로 감쌉니다. springdoc은 이 래핑을 모르므로 `SwaggerConfig`의 `OpenApiCustomizer`가 문서 스키마도 같이 감싸 **실제 응답과 맞춥니다**.

### 내부 도구 API

FastAPI 전용 경로라 **공개 API 문서에서 제외**(`springdoc.paths-to-exclude`)했습니다. 아래 표가 유일한 명세이므로 엔드포인트를 추가하면 함께 갱신합니다.

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
<summary><b>쓰기 12개</b> — <code>X-Internal-Api-Key</code> + <code>X-Run-Id</code> + <code>X-Approval-Token</code></summary>

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

테이블은 26개이고 스키마 정본은 [`db/init.sql`](src/main/resources/db/init.sql)입니다.

| 영역 | 테이블 |
|---|---|
| 사용자 · 인증 | `users` `refresh_tokens` `leave_balances` |
| 프로젝트 | `projects` `project_members` `milestones` `project_posts` `project_summaries` |
| 업무 | `tasks` `meetings` `meeting_attendees` `expenses` |
| 일정 | `schedules` `schedule_participants` `schedule_leaves` |
| Agent | `agent_conversations` `agent_messages` `agent_message_steps` `agent_message_attachments` `agent_runs` `agent_events` `agent_interactions` |
| 재계획 | `replans` `replan_scenarios` |
| 공통 | `notifications` `idempotency_keys` |

- 전체 ERD — [조직 프로필](https://github.com/Pretty-Works)
- 데모 데이터 — 가상 회사 설정([`docs/company/`](docs/company))을 먼저 설계하고 [`db/DEMO_DATA_GUIDE.md`](src/main/resources/db/DEMO_DATA_GUIDE.md) 기준으로 생성

---

## 테스트

```bash
./gradlew test
```

외부 서버(FastAPI)와 맞물려 손으로 확인하기 어려운 `agent` 패키지 위주로 테스트를 진행했습니다. (35개 중 32개)

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

## 로컬 실행

### 1. 사전 요구사항

| 도구 | 요구사항 |
|---|---|
| JDK | 21 |
| Docker | MySQL 8.0 · Redis 7 — `bootRun` 시 자동 기동 |
| Node.js | 선택 — 시드 정합성 검사용 |

- Redis 없이도 기동. 단, **로그아웃한 access token이 만료 전까지 유효**

### 2. 환경변수

```bash
cp .env.example .env
```

- 환경별 값과 시크릿만 `.env`로 주입. 아래 키는 필수

| 키 | 값 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/prettyworks_test?characterEncoding=UTF-8&connectionTimeZone=Asia/Seoul` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `compose.yaml`의 MySQL 비밀번호와 같은 값 (로컬 기본 `1234`) |
| `JWT_SECRET_KEY` | 랜덤 64 byte → Base64 |
| `AGENT_INTERNAL_API_KEY` | 랜덤 32 byte → Base64 |
| `AGENT_APPROVAL_TOKEN_SECRET` | 랜덤 32 byte → Base64 |

- 시크릿 생성 — `openssl rand -base64 64 | tr -d '\n'`
- `AGENT_INTERNAL_API_KEY`는 pretty-llm의 `INTERNAL_API_KEY`와 **같은 값** (다르면 Agent 연동이 끊김)

### 3. 데이터베이스

```bash
docker compose up -d
```

- 처음 한 번만 직접 기동. 이후에는 `bootRun`이 자동 기동
- 스키마 정본(`db/init.sql`)을 먼저 로드. `ddl-auto: validate`라 **스키마가 엔티티와 다르면 기동 실패**
- 로드 절차는 [`db/README.md`](src/main/resources/db/README.md) 참고

### 4. 실행

```bash
./gradlew bootRun
```

- 서버 — http://localhost:8080
- Swagger UI — http://localhost:8080/swagger-ui.html
- 시연 계정 — [`db/DEMO_ACCOUNTS.md`](src/main/resources/db/DEMO_ACCOUNTS.md)
- pretty-llm 없이도 기동. Agent 요청만 실패
- Agent까지 쓰려면 [pretty-llm](https://github.com/Pretty-Works/pretty-llm)을 3002 포트에 띄우고 `AGENT_SERVER_URL` 설정
