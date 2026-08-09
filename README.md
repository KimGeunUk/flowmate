# FlowMate

### AI 사전점검 그룹웨어 — 전자결재 · 근태관리

AI가 결재 반려를 미리 막아주는 사내 그룹웨어.

- 설계서: [docs/superpowers/specs/2026-08-05-flowmate-design.md](docs/superpowers/specs/2026-08-05-flowmate-design.md)
- 구현 로드맵: [docs/superpowers/plans/2026-08-05-flowmate-roadmap.md](docs/superpowers/plans/2026-08-05-flowmate-roadmap.md)

## 기술 스택

Java 17 · Spring Boot 3.5.16 (WAR) · JSP + JSTL + jQuery · MyBatis 3 · PostgreSQL 16 · Spring Security 6 · Anthropic Java SDK · Maven · Docker

## 실행 방법

### 1. DB 기동

```powershell
docker compose up -d postgres
```

스키마를 추가하거나 고친 뒤에는 볼륨을 지우고 다시 올려야 init 스크립트가 재실행된다.

```powershell
docker compose down -v
docker compose up -d postgres
```

### 2. 애플리케이션 실행

```powershell
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080/`

개발 중에는 이 명령을 표준으로 쓴다. `java -jar` 로 직접 실행하면 JVM 문자셋이
플랫폼 기본값(이 PC 는 MS949)이 되므로 `-Dfile.encoding=UTF-8` 이 필요하다.

### 3. 테스트

```powershell
.\mvnw.cmd test      # 단위 테스트 (DB 불필요)
.\mvnw.cmd verify    # 단위 + 통합 테스트 (DB 기동 필요)
```

### 4. WAR 빌드

```powershell
.\mvnw.cmd clean package
# target/flowmate.war
```

## 데모 계정

비밀번호는 전원 `flowmate1!` 이다.

| 사원번호 | 이름 | 부서 | 직급 | 권한 | 용도 |
|---|---|---|---|---|---|
| `2020003` | 곽수빈 | 개발팀 | 사원 | USER | 기안자 |
| `2016004` | 신동혁 | 개발팀 | 과장 | MANAGER | 개발팀 부서장 (1차 결재) |
| `2016002` | 박현주 | 사업본부 | 부장 | MANAGER | 상위 결재 |
| `2015001` | 정도현 | 대표이사실 | 이사 | ADMIN | 임원 결재 · 관리자 |
| `2017001` | 최민석 | 인사팀 | 차장 | ADMIN | 인사 담당 |

## 조직 구조 (시드)

```
대표이사실 (정도현 · 이사)
├─ 경영지원본부 (김성일 · 부장)
│   ├─ 인사팀   (최민석 · 차장) 3명
│   └─ 재무팀   (오세훈 · 과장) 3명
└─ 사업본부   (박현주 · 부장)
    ├─ 마케팅팀 (윤서영 · 차장) 4명
    └─ 개발팀   (신동혁 · 과장) 7명
```

부서마다 최고 직급이 1명씩만 배치되어 있다. 이후 결재선 정책이
"같은 부서 최고 직급"으로 부서장을 판정하므로, 동급이 둘이면 결재선이 비결정적으로 바뀐다.

## 데모 시나리오

1. `2020003` 곽수빈 로그인 → 기안 작성 → 지출결의 100만원 → 임시저장
   → **결재선이 자동 생성된다** (신동혁 과장 → 박현주 부장) → 상신
2. `2016004` 신동혁 로그인 → 내 결재함 **대기** 탭에 문서가 보임 → 승인
3. `2016002` 박현주 로그인 → 대기 탭 → 승인 → **완료**
4. 곽수빈으로 돌아가 이력 4건(기안·상신·승인·승인) 확인

**커스터마이징 시연:** 금액을 500만원으로 바꿔 저장하면 결재선에 이사가 추가된다.
`application.yml` 의 `flowmate.approval.line-policy` 를 `simple-two-step` 으로 바꾸고
재기동하면 같은 금액에서도 결재선이 팀장 1명으로 줄어든다.

**반려 시연:** 신동혁이 반려할 때 유형 선택이 필수다. 선택한 유형은
`approval_reject_history` 에 쌓이고 Phase 5 의 AI 사전점검이 이 표를 읽는다.

## 구현 현황

- [x] Phase 0 — 환경 구축 (JSP + Jakarta JSTL + MyBatis + PostgreSQL)
- [x] Phase 1 — 조직 · 사용자 (로그인, 사원 목록, 조직도, 공통 레이아웃)
- [x] Phase 2 — 전자결재 코어
- [x] Phase 3 — AI 게이트웨이 (화면 없음 - `LlmClient` 데코레이터 체인, 마스킹, 캐싱, 폴백)
- [x] Phase 4 — 근태 + 연동 (출퇴근 등록, 근태 조회, 연차 승인 → 근태 반영)
- [ ] Phase 5 — AI 기능
- [ ] Phase 6 — 마감 (CSS · Docker 배포 · README)

## 테스트

| 구분 | 파일 규칙 | 실행 | DB |
|---|---|---|---|
| 단위 | `*Test.java` | `mvnw.cmd test` | 불필요 |
| 통합 | `*IT.java` | `mvnw.cmd verify` | 필요 |

Phase 4 종료 시점: 단위 136건 · 통합 98건 (Phase 3 종료 시점 단위 81건 · 통합 61건에서 증가).

`ai.enabled` 는 기본값이 `false` 라 `ANTHROPIC_API_KEY` 없이도 빌드·기동·테스트가 전부
통과한다 — AI 게이트웨이는 화면이 없고 `FakeLlmClient` 로 마스킹·캐싱·폴백 전 과정이
체인 수준에서 검증된다.

단위 테스트가 DB 없이 도는 경계를 의도적으로 유지한다. 이 경계가 무너지면
순수 로직 테스트가 컨테이너 기동에 묶여 빠른 피드백을 잃는다.

## 설계 판단 기록

### 연차 승인 → 근태 반영: Spring 이벤트 대신 같은 트랜잭션의 직접 호출을 선택했다 (Phase 4)

연차 신청서가 최종 승인되면 잔여 연차를 줄이고 해당 일자의 근태를 '연차'로 바꿔야 한다.
`ApprovalService.approve()` 가 `attendance` 모듈의 `LeaveApplyService.apply(...)` 를
**Spring `ApplicationEvent` 가 아니라 같은 트랜잭션 안에서 직접 호출**한다.

- **이벤트의 트랜잭션 경계가 불명확한 것이 문제다.** 기본 `ApplicationEventPublisher` 는
  발행 즉시 동기 호출되므로 얼핏 안전해 보이지만, 리스너가 `@TransactionalEventListener`
  로 바뀌거나 비동기(`@Async`)로 바뀌는 순간 승인 트랜잭션과 근태 반영 트랜잭션이
  분리된다. 그 경계는 코드를 눈으로 봐서는 드러나지 않고, 리스너 쪽 애노테이션 하나로
  조용히 달라진다 — "승인은 됐는데 연차가 안 깎였다"는 실제 그룹웨어에서 흔한 사고가
  정확히 이 지점에서 생긴다.
- **같은 트랜잭션 안의 직접 호출은 실패를 한데 묶는다.** 결재선 갱신·문서 상태 갱신처럼
  이미 실행된 쓰기가 있어도, 근태 반영이 실패하면 그 트랜잭션 전체가 롤백되어 문서는
  `PENDING` 으로, 잔여 연차는 원래 값으로 되돌아간다. 부분 성공이라는 상태 자체가
  나오지 않는다. `ApprovalServiceLeaveApplyRollbackIT#attendanceFailureRollsBackTheApproval`
  이 근태 반영을 일부러 실패시켜 이 롤백을 실측으로 증명한다.
- **결합도는 인터페이스로 완화한다.** `approval` 이 아는 것은 `attendance.service.LeaveApplyService`
  인터페이스 하나뿐이고, 구현(`DefaultLeaveApplyService`)이나 매퍼는 모른다. 값도
  `approvalId` 하나가 아니라 `LeaveApplyCommand`(empId·leaveType·기간·일수)로 넘긴다 —
  `attendance` 가 `approval` 소유 테이블(`leave_request`)을 읽을 필요가 없어져 의존이
  `approval → attendance` 한 방향으로 유지된다(순환 없음). 이벤트가 주는 이점(느슨한
  결합) 중 트랜잭션 안전성과 무관한 부분은 인터페이스 분리만으로 이미 얻은 셈이다.

대안(이벤트로 비동기 반영)이 데이터 정합성보다 결합도를 우선한 선택이었다면, 이 프로젝트가
증명하려는 것("두 모듈이 하나의 트랜잭션 안에서 정확히 맞물린다")과 정면으로 어긋난다.
