# FlowMate 구현 로드맵 (계획서 인덱스)

> 이 문서는 실행 계획서가 아니다. **18.5일 분량의 설계서를 6개의 실행 계획서로 쪼개는 기준**과,
> 모든 계획서가 공유하는 규약을 고정하는 문서다.
> 각 Phase를 착수할 때 이 문서의 §3 규약을 전제로 해당 Phase 계획서를 작성한다.

- 원본 설계서: [2026-08-05-flowmate-design.md](../specs/2026-08-05-flowmate-design.md)
- 작성일: 2026-08-05
- 대상 저장소: `flowmate` · 패키지 루트 `com.flowmate`

---

## 1. 왜 쪼개는가

설계서는 18.5 작업일 / 7개 Phase를 담고 있다. 하나의 계획서로 묶으면 두 가지가 깨진다.

1. **앞 Phase의 구현 결과가 뒤 Phase의 계획을 바꾼다.** Phase 1에서 `Employee` 필드가 확정되면
   Phase 2의 결재선 정책 코드가 바뀐다. 미리 써두면 대량 수정이 발생한다.
2. **계획서는 "그 자체로 동작하는 소프트웨어"를 산출해야 한다.** 그래야 각 Phase 종료 시점에
   태그를 찍고 데모할 수 있다 (설계서 §9.2).

그래서 **각 계획서는 "머지 가능한 상태"를 산출 단위로 삼는다.**

## 2. 계획서 분할

| # | 계획서 파일 | 설계서 대응 | 일수 | 이 계획서가 끝나면 무엇이 동작하는가 | 태그 |
|---|---|---|---:|---|---|
| 1 | `2026-08-05-phase-0-1-foundation.md` | Phase 0 + 1 | 4.5 | WAR가 Tomcat에서 뜨고, 로그인해서 사원 목록·조직도를 본다. 공통 레이아웃 5종과 클래스 명명 규칙이 고정된다 | `phase-1-org-user` |
| 2 | [`2026-08-06-phase-2-approval-core.md`](2026-08-06-phase-2-approval-core.md) | Phase 2 | 4.5 | 사원A 기안 → 팀장 승인 → 부장 승인 → 완료가 화면에서 전부 된다. 반려 시 유형이 저장된다 | `phase-2-approval-core` |
| 3 | `phase-3-ai-gateway.md` | Phase 3 | 1.0 | `LlmClient` 데코레이터 체인이 완성되고 `FakeLlmClient`로 마스킹·캐싱·폴백이 검증된다. **화면 없음** | `phase-3-ai-gateway` |
| 4 | `phase-4-attendance.md` | Phase 4 | 3.0 | 출퇴근이 기록되고, 연차 신청서 승인 시 잔여 연차가 줄고 근태가 '연차'로 바뀐다. 중간 실패 시 전부 롤백된다 | `phase-4-attendance` |
| 5 | `phase-5-ai-features.md` | Phase 5 | 4.0 | 문서 요약, 상신 전 사전점검(평가셋 5건 통과), 연차 맥락 표시가 동작한다 | `phase-5-ai-features` |
| 6 | `phase-6-finish.md` | Phase 6 | 1.5 | `docker compose up` 한 번으로 전체가 뜨고, CSS가 채워지고, README가 완성된다. **저장소를 public 으로 전환한다** | `v1.0.0` |

> **계획서 6의 필수 항목 ① — 컨테이너 JVM 문자셋.** `pom.xml` 은 Maven·테스트 포크·`spring-boot:run`
> 세 JVM 만 UTF-8 로 고정할 수 있다. **Phase 6 의 외부 Tomcat 컨테이너 JVM 은 `catalina.sh` 가 띄우므로
> pom 의 영향권 밖이다.** slim Linux 이미지는 `LANG` 이 `POSIX`/`C` 인 경우가 많고, JDK 17 은
> UTF-8 기본값(JEP 400)이 없어 `file.encoding` 이 UTF-8 이 되지 않는다.
> → Dockerfile/compose 에서 `CATALINA_OPTS`(또는 `JAVA_OPTS`)에 `-Dfile.encoding=UTF-8` 을 넣거나
> 컨테이너 로케일을 UTF-8 로 맞춘다. **누락되면 한글이 컨테이너에서만 깨진다** (로컬은 정상).
> 같은 이유로 `java -jar target/flowmate.war` 를 손으로 실행할 때도 플래그가 필요하므로,
> README 의 실행 명령은 `mvnw spring-boot:run` 을 표준으로 적는다.
>
> **계획서 6의 필수 항목 ③ — DB 자격증명.** `application.yml` 에 `flowmate/flowmate` 가 리터럴로
> 커밋되어 있다. 로컬 개발 컨테이너의 기본값이고 같은 값이 `docker-compose.yml` 에도 있어야 하므로
> 지금 단계에서는 문제가 아니다. 다만 **public 전환 시점에는** `${DB_PASSWORD:flowmate}` 형태로 바꿔
> 커밋된 기본값은 그대로 동작하게 두면서 재정의 지점을 남긴다. 설정 한 줄 변경이며 별도 도구는 쓰지 않는다.
>
> **계획서 6의 필수 항목 ② — 비밀값 재검.** public 전환 **전에** 전체 커밋 이력에서 비밀값을 재검한다.
> Phase 3에서 Anthropic API 키를 다루므로 `git log -p -- src/main/resources/` 와
> `git log -p -S "sk-ant"` 로 어느 커밋에도 키가 없는지 확인한다.
> `.gitignore` 의 `application-local.yml` 이 1차 방어선이고, 이 재검이 2차다.
> **한 번 push 된 비밀값은 커밋을 되돌려도 원격 이력에 남는다** — 전환 전이 마지막 기회다.

**작성 시점:** 계획서 1은 지금 작성됐다. 2~6은 **직전 Phase의 머지가 끝난 뒤** 작성한다.

**계획서 3의 위치:** 설계서 §9.1은 Phase 3을 Phase 2와 병행하는 것이 "선택이 아니라 전제조건"이라고 못박았다.
계획서 3은 도메인에 의존하지 않으므로 **계획서 2와 함께 작성해두고, Phase 2에서 막힐 때마다 전환해 소화한다.**
브랜치가 다르므로(`feat/phase-2-approval-core` / `feat/phase-3-ai-gateway`) 충돌하지 않는다.

### 2.0 계획서 2 착수 전 확인할 것 (Phase 1 리뷰에서 이월)

Phase 1 은 Critical/Important 없이 마감됐다. 아래는 **"지금 틀린 것"이 아니라
"Phase 2 에서 특정 변경을 하면 그때 문제가 되는 것"** 이다. 계획서 2를 쓸 때 이 절을 먼저 읽는다.

| # | 이월 항목 | 언제 문제가 되는가 | 그때의 조치 | Phase 2 처리 결과 |
|---|---|---|---|---|
| C1 | `LoginEmployee.eraseCredentials()` 가 감싼 `Employee` 인스턴스를 직접 `null` 처리한다 | **`EmployeeMapper.findByEmpNo` 앞에 캐시가 붙는 순간.** 현재는 매 호출이 새 객체를 돌려주므로 안전하지만 그건 *암묵적* 불변식이다. `@Cacheable` 이나 결재선 조회용 공유 맵이 들어가면 캐시된 인스턴스의 해시가 지워져 **그 사원의 이후 모든 로그인이 조용히 실패한다** | `LoginEmployee` 안에 해시 사본을 두고 그걸 지우거나, `EmployeeUserDetailsService` 가 방어적 복사본을 넘긴다 | **이월 (미해결).** 계획서 2 D4 가 미리 정한 대로 `EmployeeMapper` 에 캐시를 붙이지 않았다 — 붙이지 않는 것 자체가 이 Phase 의 조치다. `DefaultApprovalLinePolicy`·부서장 체인 조회도 캐시 없이 매번 새로 조회한다. **여전히 서 있는 제약**: 이후 Phase 에서 이 매퍼에 캐시를 붙이는 순간 이 항목이 다시 유효해진다 |
| C2 | CSRF hidden input 이 JSP 파일마다 손으로 복사되는 규약이다 (공유 조각 없음) | **Phase 2 가 POST 폼을 추가할 때.** 출퇴근 등록, 승인/반려 액션마다 같은 줄을 붙여야 하고, 빠뜨리면 컴파일·템플릿 단계에서 아무 신호가 없다가 **제출 시점에 403** 이 난다 | `common/csrf-input.jsp` 조각을 만들어 include 한다. 의존성 추가 없이 복붙 위험만 제거된다 | **해결 (`a8064c7`).** `common/csrf-input.jsp` 를 만들어 기안·상신·승인·반려·회수·첨부 폼 전부가 include 한다 |
| C3 | AJAX CSRF 배선이 jQuery `$.ajaxSetup` 전용이다 | **Phase 5 의 LLM 호출이 `fetch()` 를 쓸 때.** 스트리밍에는 `$.ajax` 가 잘 맞지 않아 `fetch` 를 쓰게 되는데, 그 경로는 `ajaxSetup` 을 타지 않아 헤더가 안 붙고 **조용히 403** 이 된다 | 해당 호출에 헤더를 직접 넣거나, `fetch` 래퍼를 `common.js` 에 둔다 | **해결 (`bc29f0b`, 계획서 5 Task 6, D5).** 정확히 이 현상이 발생했다 - 사전점검 모달이 상신 버튼 클릭을 가로채 `fetch()` 로 `/api/ai/approvals/{id}/preflight` 를 부르는데, `$.ajaxSetup` 경로를 타지 않아 헤더 없이는 403 이 났다. `common.js` 에 `flowmateFetch(url, options)` 래퍼를 추가해 `$.ajaxSetup` 과 같은 출처(layout 의 meta 태그)에서 CSRF 토큰을 읽어 자동으로 붙인다 - 호출부(`write.jsp`)는 헤더를 손으로 붙이지 않는다. `AiControllerIT#preflightWithoutCsrfHeaderIsForbidden`/`#preflightWithCsrfHeaderSucceeds` 가 실제 Spring Security 필터 체인으로 "헤더 없으면 403, `flowmateFetch` 와 같은 이름·값의 헤더를 붙이면 통과"를 고정한다 |
| C4 | `SecurityConfig` 의 `permitAll` 목록에 `/WEB-INF/views/login.jsp` 가 들어 있다 | 지금 정상 동작하고 취약점도 아니다(컨테이너가 외부 직접 요청을 막는다). 다만 **뷰 경로가 외부 라우트로 오해될 수 있다** | 선택 사항: `dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()` 로 바꾸면 특정 뷰 경로를 규칙에 적지 않아도 된다. 뷰 대상이 바뀌어도 안 깨진다 | 이월 (미해결). Phase 2 는 `SecurityConfig` 의 `permitAll` 목록을 손대지 않았다 |
| C5 | `defaultSuccessUrl("/", true)` 가 저장된 요청을 항상 버린다 | **Phase 2 가 딥링크를 만들 때.** "결재 대기 문서가 있습니다" 알림이 `/approvals/123` 을 가리키면, 로그인 후 항상 `/` 로 가버려 사용자가 링크를 다시 눌러야 한다. 오픈 리다이렉트 위험은 없다(저장된 요청은 서버가 만든 값이다) | `alwaysUse` 를 `false` 로 바꾼다 | **해결 (`6fce0f6`).** 내 결재함 Task 에서 `defaultSuccessUrl("/", false)` 로 바꿔 저장된 요청(딥링크)을 보존한다 |

**Phase 6 최종 처리 (2026-08-11, 계획서 6 D6).** 프로젝트 마감 시점에 이 표를 다시 확인한 결과:

- **C3 은 이미 해결됐다** (위 표, `bc29f0b`) — `flowmateFetch` 래퍼가 그 시점에 정확히 이 항목을
  닫았고, Phase 6은 재확인만 한다. 추가 조치 없음.
- **C1·C4 는 의도적으로 열어 둔 채로 마감한다.** 둘 다 "지금 틀린 것"이 아니라 "특정 변경을
  하면 그때 문제가 되는 것"이었고, 그 특정 변경(C1: `EmployeeMapper` 에 캐시를 붙임, C4:
  `permitAll` 목록을 손댐)이 이 저장소가 public 으로 전환되는 시점까지 일어나지 않았다.
  **닫지 못하는 것을 조용히 지우지 않는다** — 두 항목 모두 `README.md` 의 "알려진 제약" 절에
  옮겨 적었다. 이후 이 프로젝트를 포크해 계속 개발하는 사람은 이 로드맵 대신 README를 먼저
  읽을 가능성이 높으므로, 최종 참조 지점을 README로 옮기는 것이 맞다.

### 2.1 축소 순서 (일정이 밀렸을 때)

설계서 §9.1이 확정한 순서를 그대로 따른다. **계획을 세울 때가 아니라 밀렸을 때 꺼내 쓴다.**

1. 기능 3b LLM 판단 코멘트 (0.5일) — 계획서 5
2. 첨부파일 업로드 (0.5일) — 계획서 2
3. 합의/참조 결재선 (결재 라인만 유지) — 계획서 2
4. 내 결재함 반려 탭 — 계획서 2

---

## 3. 모든 계획서가 공유하는 규약

계획서 2~6을 작성할 때 이 절을 다시 읽고 전제로 삼는다.

### 3.1 패키지

설계서 §4.2를 그대로 따른다. 추가로 확정한 것:

| 경로 | 용도 | 결정 시점 |
|---|---|---|
| `com.flowmate.common.web.Page<T>` | 공통 페이징 객체 | 계획서 1 |
| `com.flowmate.org.security` | `LoginEmployee`, `EmployeeUserDetailsService` | 계획서 1 (§4.2에 없는 추가) |
| `com.flowmate.common.mapper.DbHealthMapper` | DB 연결 확인용 | 계획서 1 |

`config/MyBatisConfig`는 **만들지 않는다.** `mybatis-spring-boot-starter`의 자동설정 +
`application.yml`의 `mybatis.*` 로 충분하다 (YAGNI). 설정 코드가 필요해지는 Phase에서 만든다.

### 3.2 코드 규약

| 항목 | 결정 | 이유 |
|---|---|---|
| Lombok | **사용하지 않는다** | 설계서 기술 스택에 없다. `private` 필드 + 손으로 쓴 `getXxx()`가 §4.3의 JavaBeans 규약을 눈에 보이게 만든다 |
| `record` / 텍스트블록 | 쓰지 않는다 | 설계서 §3 — 회사 환경이 Java 8/11일 가능성 |
| MyBatis 매퍼 | 인터페이스에 `@Mapper` + XML은 `src/main/resources/mapper/{모듈}/*.xml` | `@MapperScan("com.flowmate")`는 매퍼가 아닌 인터페이스까지 잡는다 |
| 세션에 올라가는 객체 | `implements Serializable` + `serialVersionUID` | Tomcat 재시작 시 세션 직렬화 실패 방지 |
| `type-aliases-package` | `com.flowmate` (재귀 스캔) | **클래스 단순명이 전 패키지에서 유일해야 한다.** 충돌하면 별칭이 깨진다 |
| JSP 출력 | 사용자 입력은 항상 `<c:out>` 또는 `fn:escapeXml` | XSS |
| **한글 파일 쓰기** | **`Write`/`Edit` 도구로만 쓴다. `Set-Content`·`Out-File`·`>` 금지** | PS 5.1 의 `Set-Content`/`Add-Content` 기본 인코딩이 시스템 ANSI(CP949)다. 한글 소스·JSP·SQL 을 이걸로 쓰면 **실제로 손상된다** |
| **한글 파일 검증** | **`Read` 도구로 확인한다. `Get-Content`·`cat` 로 판단하지 않는다** | 콘솔 코드페이지가 949 라 UTF-8 한글이 `鍮뚮뱶 ?곗텧臾?` 처럼 깨져 보인다. **파일은 정상인데 손상으로 오판하게 된다** (Task 1 에서 실제로 오경보가 났다). 줄바꿈까지 사라져 보여 더 그럴듯하다 |
| **Maven `-D` 속성 전달** | **각 인자를 개별 인용한다** — `.\mvnw.cmd verify "-Dit.test=XxxIT"` | PowerShell 5.1 이 네이티브 명령에 `-D...` 를 넘길 때 토큰을 망가뜨려 Maven 이 `LifecyclePhaseNotFoundException` 을 던진다. **실측 확인:** 인용 없음 → 실패, 개별 인용 → 정상, `--%` 정지 토큰 → 정상 |
| **PowerShell 문자열 안의 `$`** | 백틱으로 이스케이프한다 — `` '`$2a`$10`$%' `` | 백슬래시(`\$`)는 PowerShell 에서 의미가 없어 리터럴 백슬래시가 SQL `LIKE` 패턴에 들어가 매칭이 0건이 된다 (Task 6 에서 실제로 오탐이 났다) |
| **여러 줄 커밋 메시지** | **파일에 쓴 뒤 `git commit -F <파일>`** | 메시지에 `"` 가 들어가면 인자 전달이 깨져 git 이 메시지 조각을 pathspec 으로 오인한다 (`error: pathspec ... did not match`) |
| 날짜 | DB `DATE` → `java.time.LocalDate`, `TIMESTAMP` → `LocalDateTime`. JSP는 `${x.hireDate}` 그대로 출력 | `<fmt:formatDate>`는 `java.util.Date`만 받는다. `LocalDate.toString()`이 이미 `yyyy-MM-dd`다 |

### 3.3 테스트 분리 (Maven 표준 규약)

| 파일명 | 실행 플러그인 | 실행 명령 | DB 필요 |
|---|---|---|---|
| `*Test.java` | Surefire | `mvnw.cmd test` | **아니오** |
| `*IT.java` | Failsafe | `mvnw.cmd verify` | **예** (Docker PostgreSQL 기동 필요) |

설계서 §8의 "단위 테스트 40건"은 `*Test.java`로, "통합 테스트 3건"은 `*IT.java`로 센다.
**단위 테스트는 Docker 없이 항상 돌아야 한다.** 이 경계가 무너지면 순수 로직 테스트의 가치가 사라진다.

테스트 메서드명은 영어, `@DisplayName`은 한국어로 쓴다.

### 3.4 SQL 파일

`docker/postgres/init/` 에 번호 접두사를 붙여 두고 컨테이너 초기화 시 알파벳 순으로 실행된다.

| 파일 | 계획서 |
|---|---|
| `00-extension.sql` | 1 |
| `10-schema-org.sql` / `11-seed-org.sql` | 1 |
| `20-schema-approval.sql` / `21-seed-approval.sql` | 2 |
| `30-schema-attendance.sql` / `31-seed-attendance.sql` | 4 |
| `40-schema-ai.sql` | 3 |
| `50-seed-demo.sql` (문서 200건 / 반려 40건 / 근태 3개월) | 5 |

**중요:** `/docker-entrypoint-initdb.d`의 스크립트는 **데이터 볼륨이 비어 있을 때만** 실행된다.
스키마를 추가하거나 고쳤으면 `docker compose down -v` 후 다시 올린다. README에 이 명령을 적어둔다.

### 3.5 Oracle 대응표 유지 규칙

설계서 §5.6은 `docs/oracle-mapping.md`를 별도 문서로 유지하라고 했고, §9는 Phase 6에 배치했다.
**이 로드맵은 그 순서를 바꾼다.** 문서는 계획서 1에서 만들고, **PostgreSQL 전용 문법을 쓸 때마다 그 자리에서 한 줄 추가한다.**

이유: Phase 6에 몰아서 쓰면 "내가 어디서 뭘 썼는지" 재조사해야 하고, 그 재조사가 0.5일 안에 안 끝난다.
설계서 §5.6이 "이 대응표를 유지하는 것이 Oracle 경험을 설명 가능하게 만드는 장치"라고 한 만큼, 사후 복원은 목적을 배반한다.

### 3.6 Git

설계서 §9.2 그대로. 원격 저장소 관련은 계획서 1 Task 0B 에서 확정했다.

- 원격: GitHub **private** 저장소 `flowmate` (`origin`). Phase 6 마감 후 public 전환
- 브랜치: `main` + Phase별 `feat/phase-N-<name>`
- Phase 종료 시 `main`에 머지 + 주석 태그 + **`git push origin main --follow-tags`**
- 커밋 단위는 "동작하는 최소 변경". 메시지는 한국어 명령형 + 이유
- **커밋 로그 자체가 포트폴리오**

**Phase 마다 push 하는 이유:** 마지막에 한 번 몰아 push 하면 원격 이력이 한 시점에 뭉친다.
설계서 §9.2가 커밋 로그를 산출물로 본 이상, 18일에 걸쳐 쌓인 흔적 자체가 내용이다.

**`--follow-tags` 를 빠뜨리면** Phase 태그가 로컬에만 남고 원격에는 안 올라간다.

### 3.7 개발 환경 (2026-08-06 실측)

확인 시점에 **JDK · Maven · Docker · gh CLI 가 모두 없었고**, git 만 설치되어 있었다 (`kgu` / `kgu@jisystem.com`).
실행 세션이 관리자로 승격되지 않아(`Elevated: False`) **UAC 가 필요한 MSI 설치를 비대화형으로 진행할 수 없다.**

| 도구 | 방법 | 상태 | 관리자 |
|---|---|---|---|
| Temurin JDK **17.0.20+8** | zip → `C:\Users\pc\dev-tools\jdk-17` | **설치 완료** | 불필요 |
| Apache Maven **3.9.16** | zip → `C:\Users\pc\dev-tools\maven-3.9.16` | **설치 완료** | 불필요 |
| gh CLI **2.97.0** | zip → `C:\Users\pc\dev-tools\gh` | **설치 완료** (인증 대기) | 불필요 |
| WSL2 + Docker Desktop | `wsl --install` + `winget install Docker.DockerDesktop` | **미설치** | **필요 + 재부팅** |

**winget 을 쓰지 않은 이유:**
- `Apache.Maven` 이 winget 인덱스에 **없다** (`winget search maven` 결과에 Apache Maven 부재)
- `EclipseAdoptium.Temurin.17.JDK` · `GitHub.cli` 는 `wix`(MSI) 라 머신 전역 설치에 UAC 필요

zip 방식이 이 프로젝트에 더 맞는다 — 버전이 경로에 박혀 재현 가능하고, 설치 위치가 한 곳으로 모이고,
`dev-tools` 폴더를 지우면 원상복구된다.

**사용자만 할 수 있는 두 가지 (에이전트 대행 불가):**
1. `gh auth login` — 브라우저/기기코드 대화형. 도구 실행 환경은 표준 입력이 막혀 즉시 실패한다
2. WSL2 설치 후 **재부팅**, Docker Desktop 최초 실행 시 라이선스 동의

**Windows 환경변수 전파 함정:** 설치가 바꾼 `PATH`·`JAVA_HOME` 은 이미 실행 중인
프로세스와 그 자식에게 전파되지 않는다. 새 터미널을 열어도 부모(에디터·에이전트)가 예전 환경을
물려주므로 `java` 가 계속 "없음" 으로 보인다. 명령 앞에 다음을 붙인다.

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
```

**Maven 이 `platform encoding: MS949` 를 보고한다 — 차단 요소는 아니다.**
한글은 CP949 로 표현되므로 콘솔의 한국어 `@DisplayName` 출력은 깨지지 않고, 컴파일은
`project.build.sourceEncoding=UTF-8` 이 담당한다. 남는 위험은 **명시적 charset 없이 파일을 읽는 코드**뿐이고
Phase 1 에는 없다. 다만 **Phase 3 의 `PromptRepository` 가 프롬프트 파일을 읽으므로**
계획서 1 Task 2 에서 `.mvn/jvm.config` 로 UTF-8 을 고정해 두고, Phase 3 에서는 읽기 코드에
`StandardCharsets.UTF_8` 을 명시한다.

**Docker 가 필요해지는 최초 지점은 계획서 1 Task 4 다.** Task 1~3(JSP/JSTL 배선)은 DB 를 쓰지 않으므로
재부팅을 뒤로 미루고 진행할 수 있다.

**Windows 환경변수 전파 함정:** 설치 프로그램이 바꾼 `PATH`·`JAVA_HOME` 은 이미 실행 중인
프로세스와 그 자식에게 전파되지 않는다. 설치 직후의 모든 명령 앞에 다음을 붙인다.

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
```

**Docker 가 필요해지는 최초 지점은 계획서 1 Task 4 다.** Task 1~3(JSP/JSTL 배선)은 DB 를 쓰지 않으므로
재부팅을 뒤로 미루고 진행할 수 있다.

### 3.7 CSS를 미루는 조건

설계서 §4.4.2의 의미 기반 명명 규칙이 지켜져야 Phase 6에 JSP를 열지 않을 수 있다.
이를 강제하기 위한 장치를 계획서 1에서 만든다:

**`src/main/webapp/static/css/style.css` 최상단에 클래스 목록 주석을 유지하고, 각 Phase 종료 시 그 Phase에서 새로 만든 클래스명을 추가한다.**

그러면 Phase 6은 "목록을 위에서 아래로 채우는 작업"이 된다. 화면을 뒤지며 클래스를 수집하는 작업이 아니다.

---

## 4. 설계서에서 바꾼 결정 (Deviation Log)

계획서 1을 쓰는 과정에서 설계서와 다르게 정한 것들. **각 항목은 설계서 원문을 대체한다.**

| # | 설계서 | 변경 | 이유 |
|---|---|---|---|
| D1 | §9 Phase 1: 사원 목록(Day 2) → 공통 레이아웃(Day 4.5) 순서 | **공통 레이아웃을 사원 목록보다 먼저** 만든다 | §4.4.1이 직접 경고한 것("공통 조각을 늦게 만들면 이미 만든 화면 전부를 고쳐야 한다")을 §9의 Day 순서가 어기고 있다. 순서를 바꾸면 사원 목록 재작성 작업이 없어진다 |
| D2 | §9 Phase 1: "부서 4" | **부서 7개** (본부 2 + 팀 4 + 대표이사실 1, 3단 계층) | 부서 4개로는 재귀 CTE의 `depth`가 2단에서 끝난다. §6.3의 "하위 부서까지 집계"를 시연할 계층이 없다 |
| D3 | §6.1 재귀 CTE의 `path`가 `dept_id` 기반 | `sort_order` + `dept_id` 기반 `sort_path` | `department.sort_order`를 `NOT NULL DEFAULT 0`으로 선언한 이유가 형제 정렬인데, 원문 CTE는 그 컬럼을 쓰지 않아 정렬이 id 순이 된다 |
| D4 | §4.4 조직도를 "트리로 렌더링" | 중첩 `<ul>`이 아니라 **플랫 리스트 + `--depth{n}` 클래스** | JSP의 `<jsp:include>` 재귀는 `<c:set scope="request">`로 변수를 덮어써서 부모 루프가 깨진다(조용히). 들여쓰기는 CSS의 일이고, 이 방식이 §4.4.2와 정확히 맞는다. Java 트리 조립 클래스도 불필요해진다(YAGNI) |
| D5 | §5.1 시드 비밀번호 | pgcrypto `crypt('flowmate1!', gen_salt('bf', 10))` 로 SQL 안에서 생성 | BCrypt 해시를 손으로 복사·붙여넣는 단계가 없어진다. pgcrypto의 `bf`는 `$2a$` 형식이라 `BCryptPasswordEncoder`가 그대로 검증한다. 데모 비밀번호는 ASCII만 쓴다(pgcrypto `bf`의 8bit 문자 이슈 회피) |
| D6 | §4.2 `config/MyBatisConfig` | 만들지 않는다 | 자동설정 + `application.yml`로 충분. 필요해지는 Phase에서 만든다 |
| D7 | §4.4 공통 조각에 `ai-panel.jsp` 포함 | `ai-panel.jsp`는 **계획서 5에서** 만든다 | Phase 1에 만들면 내용 없는 파일이 된다. 나머지 5종(head/header/sidebar/footer/pagination)은 Phase 1에서 만든다 |
| D9 | §3.1 "기존 taglib URI 를 쓰면 태그가 문자열로 출력되거나 500 발생" | **사실이 아니다.** `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` 의 TLD 를 직접 확인한 결과 이 jar 은 `jakarta.tags.core` 와 `http://java.sun.com/jsp/jstl/core` 를 **모두 등록**한다 (`c.tld` / `c-1_2.tld`). 구형 URI 도 해석된다 | 실제 실패 원인은 URI 가 아니라 **artifact 선택**이다. `javax` 시절 좌표를 Tomcat 10.1 에서 쓰면 `NoClassDefFoundError: javax/servlet/jsp/...` 가 난다. 태그가 문자열로 출력되는 것은 `<%@ taglib %>` 선언을 빠뜨린 경우다. 세 증상을 구분해 두지 않으면 엉뚱한 곳을 고치게 된다. **그래도 `jakarta.tags.*` 를 쓴다** — Jakarta EE 10 의 정식 URI이고 구형은 하위호환 잔존물이다. Phase 0 의 위험도는 설계서 예상보다 낮다 |
| D8 | §5.6 Oracle 대응표를 Phase 6에 작성 | 계획서 1에서 만들고 **매 Phase 증분 추가** | §3.5 참조 |

## 5. 아직 결정되지 않은 것

설계서 §12의 "남은 결정 사항"에 더해, 계획서 1을 쓰면서 드러난 것들.

| # | 항목 | 결정 필요 시점 | 지금의 잠정 결정 |
|---|---|---|---|
| Q1 | **Spring Boot 3.2.x는 OSS 지원이 끝난 라인이다** (2024-12 종료). 2026년 포트폴리오에 EOL 버전을 쓰는 것이 맞는가 | 계획서 1 Task 2 (즉시) | **설계서대로 `3.2.5`를 쓴다.** JSP/Jakarta 배선 정보가 3.2 기준으로 가장 풍부해 Phase 0 리스크가 낮다. 지원 라인으로 올릴 경우 `spring-boot-starter-parent` 버전과 `mybatis-spring-boot-starter` 버전만 바꾸면 되고, 계획서의 다른 코드는 영향받지 않는다. **사용자 판단이 필요한 항목** |
| Q2 | `ApprovalLinePolicy` 의 부서장 판정 | — | **확정 (2026-08-06). 아래 §5.1 에 전문.** `department` 에 `manager_emp_id` 컬럼은 **추가하지 않는다** |
| Q3 | `position`은 PostgreSQL에서 `col_name_keyword`다. 테이블명으로 쓸 수 있지만 경계 대상 | 계획서 1 Task 6에서 스키마 적용 시 즉시 판명 | 설계서대로 `position`을 쓴다. 오류가 나면 `job_position`으로 바꾸고 이 표를 갱신한다 |
| Q4 | Anthropic API 키 보관 방식 | 계획서 3 | **확정 (2026-08-08).** `application-local.yml`(gitignore) 에도 넣지 않는다 — 파일에 있으면 언젠가 스크린샷·붙여넣기로 샌다. **환경변수 `ANTHROPIC_API_KEY` 로만** 받는다. SDK의 `AnthropicOkHttpClient.fromEnv()` 가 환경변수를 직접 읽으므로 코드·설정 파일 어디에도 키 문자열이 등장하지 않는다. `ai.enabled` 는 기본값 `false` 로 두어 키 없이도 클론·빌드·기동·테스트가 전부 통과한다(계획서 3 D3, `mvnw clean verify` 로 실증) — public 전환 후 클론한 사람이 키 없이도 저장소를 살펴볼 수 있어야 한다는 요구를 이 기본값 하나로 만족시킨다 |
| Q6 | **첨부파일 업로드 디렉터리 경로** (`approval_attachment.file_path`) | 계획서 2 | **확정 (`1043d70`).** 설정 키 `flowmate.upload.base-dir`, 기본값 `./upload`. 실제 저장 경로는 `{base-dir}/approval/{yyyy}/{MM}/{UUID}.{ext}`. `.gitignore` 에 `/upload/` 추가 완료(같은 커밋). 원본 파일명은 `approval_attachment.file_name` 에만 두고 디스크에는 UUID 로 저장해 경로 조작·덮어쓰기·한글 파일명 문제를 한 번에 없앤다 |
| Q5 | 반려 유형 6종 최종 확정 | 계획서 2 (설계서 §12에 이미 등록됨) | **확정.** §5.2 정의대로 6종(`INSUFFICIENT_CONTENT`/`EXCESSIVE_AMOUNT`/`MISSING_EVIDENCE`/`PROCEDURE_ERROR`/`BUDGET_EXCEEDED`/`OTHER`) 그대로 구현. 반려 화면에서 유형 선택을 필수로 만들어 `approval_reject_history` 에 쌓는다(Phase 5 사전점검의 학습 원천) |

### 5.1 `ApprovalLinePolicy` 확정안 (2026-08-06)

설계서 §6.2 는 `DefaultApprovalLinePolicy` 를 "기안자 → 부서장 → (금액 300만 초과 시) 임원" 이라고만 적었다.
시드에 대입해보니 세 가지가 미정이었고, 아래로 확정한다.

**`department` 에 `manager_emp_id` 컬럼을 추가하지 않는다.** 부서장은 계산으로 판정한다.
이유: 이미 만든 재귀 CTE 를 결재선 생성에 재사용할 수 있고(공고의 SQL 항목을 한 번 더 증명),
자기참조 FK(`department` → `employee` → `department`)의 시드 삽입 순서 문제를 피한다.
직급과 보직이 어긋나는 경우(과장이 팀장, 차장이 팀원)가 생기면 그때 컬럼을 추가한다.

**`DefaultApprovalLinePolicy` 규칙 — 순서대로 적용한다.**

1. 기안자 부서에서 시작해 **부서 트리를 루트까지 올라간다.**
2. 각 부서에서 `position_level` 이 가장 높은 1명을 뽑는다 (동급이면 `hire_date` 가 이른 사람 — 시드는 동급이 없도록 배치됨).
3. 그 사람의 `position_level` 이 **기안자보다 높고**, **6 미만이며**, 기안자 본인이 아니면 결재선에 추가한다.
   → **레벨 6(이사)을 탐색에서 제외하는 것이 핵심이다.** 제외하지 않으면 이사가 항상 결재선에 들어와
   "금액 300만 초과 시 임원" 조건이 아무 의미가 없어진다.
4. `amount > 3,000,000` 이면 **이사(L6)** 를 마지막에 추가한다. 이미 결재선에 있거나 기안자 본인이면 추가하지 않는다.
5. 위를 마쳤는데 결재선이 **비어 있고** 기안자가 L6 이 아니면, 이사를 1명 추가한다 (빈 결재선 방지).
6. 그래도 비어 있으면(기안자가 이사 본인) **결재자 0명** — 상신 시 즉시 `APPROVED` 로 완료된다.

**시드로 검증한 결과 (계획서 2 의 단위 테스트가 이 표를 그대로 고정한다):**

| 기안자 | 금액 | 생성되는 결재선 | 비고 |
|---|---:|---|---|
| 곽수빈 (개발팀 · 사원 L1) | 100만 | 신동혁(과장) → 박현주(부장) | **설계서 완료 기준 "사원A → 팀장 → 부장 → 완료" 와 일치** |
| 곽수빈 | 500만 | 신동혁 → 박현주 → 정도현(이사) | 규칙 4 |
| 신동혁 (개발팀 · 과장 L3) | 100만 | 박현주(부장) | 자기 부서 최고 직급이 본인 → 규칙 3 에서 제외 |
| 서다인 (인사팀 · 사원 L1) | 100만 | 최민석(차장) → 김성일(부장) | 다른 본부에서도 같은 모양 |
| 박현주 (사업본부 · 부장 L5) | 100만 | 정도현(이사) | 규칙 5 — 빈 결재선 방지 |
| 정도현 (대표이사실 · 이사 L6) | 100만 | **없음** | 규칙 6 — 상신 즉시 완료 |

**`SimpleTwoStepLinePolicy` (교체 구현체, 소규모 고객사용):**
트리를 오르지 않는다. 기안자 소속 부서의 최고 직급 1명만 결재자로 둔다. 금액 조건 없음.
곽수빈이 500만원을 기안해도 결재선은 신동혁 1명이다.
**두 구현체의 차이가 "고객사마다 결재선이 다르다"를 코드로 증명하는 지점**이므로,
같은 입력에 서로 다른 결재선이 나오는 것을 단위 테스트로 나란히 고정한다.

**계획서 2 에서 확인할 것:** 규칙 6(결재자 0명)이 `ApprovalDoc.submit()` 의 상태 전이와 어떻게 만나는지.
`DRAFT → PENDING → APPROVED` 를 한 번에 통과시킬지, `DRAFT → APPROVED` 를 허용할지 정해야 하며,
상태 기계 단위 테스트가 이 경로를 포함해야 한다.

---

## 6. 진행 상황

| 계획서 | 상태 |
|---|---|
| 1. Phase 0+1 토대 | **실행 완료** — `main` 에 머지, 태그 `phase-0-bootstrap` · `phase-1-org-user` |
| — | **사전점검 완료** (Phase 2 착수 전): Boot 3.5.16 업그레이드 · 외부 Tomcat 10.1.57 실배포 검증 · 결재선 정책 확정 · CSS 최소선 · 클린 클론 재현성 |
| 2. Phase 2 전자결재 코어 | **완료** — Task 11개 실행 완료, 머지 대기(Task 11 Step 6은 코디네이터가 수행). 단위 52 · 통합 55 (목표 단위 50 · 통합 40 초과 달성) |
| 3. Phase 3 AI 게이트웨이 | **완료** — Task 6개 실행 완료, 머지 대기(코디네이터가 수행). 단위 81 · 통합 61 (계획 단위 81 · 통합 61 그대로 달성) |
| 4. Phase 4 근태 + 연동 | **완료** — Task 8개 실행 완료, 머지 대기(코디네이터가 수행). 단위 136 · 통합 98 (계획서 예상 단위 약 111 · 통합 약 78 을 상회 달성) |
| 5. Phase 5 AI 기능 | **완료** — Task 1~9 전부 실행 완료, `main` 에 머지, 태그 `phase-5-ai-features`. 단위 150 · 통합 130. 구조화 출력 배선(스키마 2종)·기능 3종(요약·사전점검·연차 맥락)·캐시 TTL·기능 플래그·`DatabasePromptRepository` 까지 마쳐 설계서 §7 의 커스터마이징 지점 5개가 전부 구현 2개씩을 갖췄다. Task 9 평가셋(실제 Gemini API, 수동 실행) **5/5 통과** — `docs/ai-eval-results.md`. 계획 외 추가: `LlmClient` 세 번째 구현 `GeminiLlmClient`(§6.1 이하 참고 대신 README "설계 판단 기록" 참고) |
| 6. Phase 6 마감 | **완료** — Task 1~6 실행 완료. CSS `style.css` 단일 파일 마감(JSP diff 0), 컨테이너 빈 볼륨 기동 검증, DB 자격증명 환경변수화, README·`oracle-mapping.md`·본 로드맵 최종화, 비밀값 재검(§6.4). 단위 154 · 통합 130, `mvnw clean verify` BUILD SUCCESS(키 없이). **Task 7(public 전환)만 사용자가 직접 수행** — 계획서 6이 그렇게 못박았다 |

### 6.1 사전점검 결과 (2026-08-06)

Phase 2 착수 전에 다섯 가지를 확인했다. **두 가지가 특히 값이 있었다.**

| # | 점검 | 결과 |
|---|---|---|
| 1 | Spring Boot 3.2.5(EOL) → **3.5.16** + MyBatis 3.0.5 | 43건 통과. **3.5.16 도 Tomcat 10.1(10.1.55) 을 쓰므로** Jakarta EE 10 / Servlet 6.0 / JSTL 3.0 배선이 그대로 유효하다 — 이것이 안전하게 올릴 수 있었던 근거다 |
| 2 | **외부 Tomcat 10.1 실배포** | **Apache Tomcat/10.1.57 + JVM 17.0.19 에서 동작 확인.** 지금까지 모든 검증이 `spring-boot:run` 의 내장 Tomcat 이었고 `ServletInitializer` 는 실행된 적이 없었다. 컨텍스트 경로가 `/flowmate/login` 으로 정확히 나가고, `lib-provided/` 를 컨테이너가 무시하고 자기 Jasper 를 써도 JSP 가 컴파일된다. `-Dfile.encoding=UTF-8` 은 이 이미지에선 불필요했으나(Temurin 17.0.19 가 JDK-8291959 백포트 포함) 방어적으로 유지 |
| 3 | 결재선 정책 확정 | §5.1 에 6가지 케이스 검증 표로 고정 |
| 4 | CSS 최소선 66개 규칙 | **JSP 0개 변경으로 전 화면 스타일링.** 설계서 §4.4 의 "마지막 Phase 에 JSP 를 열지 않는다" 가 주장이 아니라 사실임이 확인됐다 |
| 5 | 클린 클론 재현성 | 추적 파일만으로 빌드 성공. `mvnw` 는 LF, `mvnw.cmd` 는 CRLF (`.gitattributes` 작동) |

**2번이 결승선 리스크를 제거했다.** 설계서는 Phase 6 에 0.5일로 컨테이너 배포를 잡았는데,
거기서 처음 시도해 실패하면 프로젝트의 중심 주장("산출물은 WAR, 외부 Tomcat 배포")이
고칠 시간 없이 무너진다. 지금 확인해 그 가능성을 없앴다.

### 6.2 Phase 2 리뷰가 잡은 것 (2026-08-08)

계획서 3 이하를 쓰는 사람이 같은 실수를 반복하지 않도록, Phase 2 구현 중 자체 리뷰와
별도 코드 리뷰(커밋 `aa79392`)가 잡은 것 중 다음 Phase에 영향을 주는 세 건을 남긴다.

1. **`RejectReason.isValid(null)` 의 `NullPointerException` (커밋 `275a1b0`).**
   `ALL` 이 `List.of(...)` 로 만든 불변 리스트인데, 이런 리스트의 `contains(null)` 은
   `false` 가 아니라 예외를 던진다("null 원소를 아예 허용하지 않는 리스트라 포함 여부를
   못 묻는다"는 뜻으로 실패한다). 화면에서 반려 유형을 선택하지 않고 보내면 `category` 가
   정확히 이 `null` 이 되어 실사용 경로에서 터진다. **교훈:** `List.of(...)` 로 만든 상수
   컬렉션에 `contains()` 를 걸 때는 인자가 `null` 일 수 있는 자리마다 `x != null && list.contains(x)`
   순서를 지킨다 — 검증 대상이 사용자 입력이면 거의 항상 `null` 이 올 수 있는 자리다.

2. **PostgreSQL 에서는 "제약 위반 → catch → 재시도" 가 죽은 코드다 (커밋 `aa79392`, 가장 심각).**
   문서번호를 `MAX+1` 로 계산해 넣고 `doc_no` UNIQUE 충돌 시 `DuplicateKeyException` 을 잡아
   재계산·재시도하는 코드를 처음에 짰다. **PostgreSQL 은 제약 위반이 나면 트랜잭션 전체를
   중단(abort) 상태로 만들어**, 예외를 잡아도 같은 트랜잭션의 다음 쿼리가 전부 `25P02
   (current transaction is aborted)` 로 죽는다 — 재시도할 "다음 쿼리" 자체가 실행되지 않으므로
   이 재시도 루프는 실전에서 한 번도 동작하지 않는 죽은 코드였다. `pg_try_advisory_xact_lock`
   으로 직접 검증(같은 키는 `false`, 다른 키는 `true`, 트랜잭션 종료 시 자동 해제)한 뒤
   `(접두사, 연도)` 단위 `pg_advisory_xact_lock` 을 채번 전에 걸어 충돌 자체를 없애는 방식으로
   교체했다.
   **Oracle 에서는 다르다** — Oracle 은 문장 단위(statement-level) 롤백이라 제약 위반 문장만
   되돌리고 트랜잭션은 계속 살아 있으므로, "제약 위반 → catch → 재시도" 패턴이 Oracle 에서는
   실제로 동작한다. **즉 이 재시도 패턴은 이식 가능하지 않다** — DB 마다 트랜잭션 중단 범위가
   다르다는 것을 코드 형태로 보여준 사례다. 자문 잠금(advisory lock)은 Oracle 에 없으므로
   Oracle 대응은 `SELECT ... FOR UPDATE` 로 잠금 대상 행을 먼저 확보하거나 전용 채번
   테이블/시퀀스를 쓴다 (`docs/oracle-mapping.md` §2.6).
   **다음 Phase가 반드시 지킬 규칙:** PostgreSQL 트랜잭션 안에서 제약 위반을 잡아 같은
   트랜잭션 안에서 재시도하지 않는다. Phase 4 의 근태 기록이 `ApprovalService.approve()`
   안에 들어가므로(계획서 2 Task 7 이 남긴 주석 자리, 설계서 §6.3), 그 지점에서 유니크
   제약이나 체크 제약을 걸고 실패 시 재시도하는 코드를 짜면 이 항목이 그대로 재발한다 —
   사전에 잠그거나(advisory lock), 애초에 충돌이 나지 않게 설계한다.

3. **범위를 두지 않은 `@ControllerAdvice` 가 다른 모듈의 버그를 삼킬 뻔했다 (커밋 `aa79392`).**
   `GlobalExceptionHandler` 가 `IllegalArgumentException`/`IllegalStateException` 을 잡아
   결재 도메인의 4xx 안내 화면으로 바꿔주고 있었는데, 두 예외는 JDK 전반에서 흔히 던져진다
   (`NumberFormatException` 도 `IllegalArgumentException` 의 하위 타입이다). 범위를 좁히지
   않으면 **전혀 관련 없는 모듈의 진짜 버그가 "정상적인 4xx 안내"로 둔갑해 500 으로 드러나지
   않는다** — 근태·AI 모듈이 같은 예외 타입을 쓰는 순간 조용히 서로의 버그를 가려주는
   관계가 생긴다. `@ControllerAdvice(basePackages = "com.flowmate.approval.controller")`
   로 결재 컨트롤러 패키지에만 적용하도록 좁혔다. **다음 Phase가 지킬 규칙:** 공통
   `IllegalArgumentException`/`IllegalStateException` 을 4xx 로 매핑하는 핸들러를 새로
   만들 때는 처음부터 `basePackages` 로 범위를 좁힌다 — "일단 전역으로 만들고 나중에 좁히기"는
   그 사이에 다른 모듈의 버그를 숨긴 기간을 만든다.

### 6.3 Phase 3 리뷰가 잡은 것 (2026-08-08) — 계획서의 예측 두 건이 실측과 달랐다

계획서 3(D6 부속, Task 5)을 쓰면서 미리 적어둔 위험 두 가지가 있었다. 실측해보니 **하나는
걱정할 필요가 없었고, 다른 하나는 계획서 예상보다 나쁜 형태로 실재했다.** 둘 다
"계획서가 틀렸을 때 코드로 확인하고 계획서를 고친" 사례라서 남긴다.

1. **순환 참조 함정은 실측에서 발생하지 않았다.** `LlmConfig.llmClient(LlmClient baseClient, ...)`
   가 `LlmClient` 를 반환하면서 동시에 `LlmClient` 파라미터를 받길래 `BeanCurrentlyInCreationException`
   을 예상했다. **실제로는 나지 않는다** — Spring 이 자기 자신(지금 만들고 있는 빈)을 후보에서
   제외하는 자기 참조 배제 규칙 때문에, `ai.enabled` 조건으로 `claudeLlmClient`/`fakeLlmClient`
   중 정확히 하나만 활성화되는 이 배선에서는 애초에 후보가 하나뿐이라 모호함이 생기지 않는다.
   그래도 `@Qualifier("baseLlmClient")` 는 남겨뒀다 — 이 암묵적 배제에 기대면, 조건 없는
   세 번째 `LlmClient` 빈이 언젠가 추가되는 순간 후보가 둘로 늘어 다시 모호해지기 때문이다.

2. **`AnthropicOkHttpClient.fromEnv()` 는 키가 없어도 예외를 던지지 않는다 — 계획서 예상보다
   나쁜 결과였다.** 계획서는 "`ai.enabled=true` 인데 키가 없으면 `fromEnv()` 가 알아서
   실패할 것"이라고 적었다. 실측 결과 `fromEnv()` 는 클라이언트를 정상적으로 만들어 주고,
   첫 AI 호출에서야 401 이 난다 — 그런데 그 401 은 바로 바깥의 `ResilientLlmClient` 가
   설계대로 흡수해 `Optional.empty()` 로 바꿔버린다. 결과적으로 **설정 실수(키를 안 넣음)와
   일시적 장애가 화면에서 구별되지 않고, 아무도 눈치채지 못한 채 AI 기능이 영구히 죽은 채로
   운영될 뻔했다** — 폴백이 의도대로 잘 작동하는 것 자체가 문제를 숨기는 역설적인 사례다.
   `claudeLlmClient` 빈 생성 시점에 환경변수를 직접 검사해 없으면 `IllegalStateException` 으로
   기동을 막는 방어 코드를 추가해 고쳤다(fail-fast). **다음 Phase가 지킬 규칙:** SDK의
   "느슨한" 초기화(생성 시점에 필수 자격증명을 검증하지 않는 것)를 가정하지 않는다 —
   문서화된 동작이 아니라 실측으로 확인하고, 필요하면 우리 쪽에서 기동 시점 검사를 추가한다.

### 6.4 Phase 6 마감 처리 (2026-08-11)

계획서 6(`2026-08-09-phase-6-finish.md`) Task 1~6을 전부 실행했다. 요약:

1. **CSS 마감 (Task 1·2).** 사전 대조 후 `style.css` 한 파일만 편집. `git diff --stat` 253
   insertions·3 deletions, **JSP 변경 0줄** — §6.1·6.2에 이미 두 번 있었던 결과가 세 번째로
   재현됐다(가장 큰 규모로). 시각 기반 클래스 이름은 사전 대조에서도 없었다.
2. **컨테이너 문자셋·전체 기동 (Task 3, D2·D5).** `docker compose down -v` 로 빈 볼륨을 만들고
   `docker compose up -d` 로 재기동 — init 스크립트 6개 전부 실행, 행 수 일치, 컨테이너 안
   JVM `file.encoding=UTF-8` 확인, 브라우저로 로그인→기안→상신→승인→근태 흐름에서 한글
   정상 렌더링 확인. DB 자격증명은 `${DB_URL:...}`/`${DB_USERNAME:...}`/`${DB_PASSWORD:...}`
   로 전환(D3) — 커밋된 기본값은 그대로 두어 재정의 지점만 남겼다.
3. **README·문서 최종화 (Task 4·5).** `README.md` 전면 재작성(무엇/누구를 위한 것인지,
   실행법, 아키텍처 스케치, 설계 판단 기록 8건, 알려진 제약, 데모 시나리오, 테스트 카운트).
   `docs/oracle-mapping.md` 에 §2.10(pgcrypto `crypt`/`gen_salt` 시드 해시)과 `ON CONFLICT`
   의 추가 사용 위치(`LeaveRequestMapper`, `41-seed-attendance.sql`)를 보강 — 계획서 6이
   요구한 여섯 구문(양방향 재귀 CTE·`FOR UPDATE`·`ON CONFLICT...DO UPDATE`→`MERGE INTO`·
   `pg_advisory_xact_lock`·`generate_series`→`CONNECT BY LEVEL`·`crypt`/`gen_salt`)이 전부
   실제 사용 위치와 함께 문서화된 것을 확인했다. 이월 항목 C1·C4는 §2.0 표 아래에 "Phase 6
   최종 처리"로 기록하고 README "알려진 제약" 절로 참조를 옮겼다; C3은 이미 해결됨을 재확인.
   태그 6개(`phase-0-bootstrap` … `phase-5-ai-features`) 전부 올바른 "Phase N 완료" 머지
   커밋을 가리키는 것을 `git log --first-parent` 로 재확인했다.
4. **비밀값 재검 (Task 6, D4, ★★).** public 전환 직전의 마지막 확인. `git log --all -p -S
   "sk-ant"`, `-S "AIza"`, `src/main/resources/` 전체 이력, `.env`/`application-local`/`secret`
   이름으로 추가된 파일 이력, 추적 파일 중 `.env`/`upload/`/`.log`/`application-local` 패턴을
   전부 확인했다 — **결과는 이 계획서가 아니라 실행 로그(에이전트 최종 보고)에 그대로 남긴다.**
   전부 0건이 아니면 이 항목이 여기서 "완료"로 표시되어 있지 않을 것이다.

**Task 7(public 전환)은 계획서 6이 못박은 대로 사용자가 직접 한다.** 전환 후 `v1.0.0` 태그를
붙이는 것도 사용자 몫이다.
