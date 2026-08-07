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
| 2 | `phase-2-approval-core.md` | Phase 2 | 4.5 | 사원A 기안 → 팀장 승인 → 부장 승인 → 완료가 화면에서 전부 된다. 반려 시 유형이 저장된다 | `phase-2-approval-core` |
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

| # | 이월 항목 | 언제 문제가 되는가 | 그때의 조치 |
|---|---|---|---|
| C1 | `LoginEmployee.eraseCredentials()` 가 감싼 `Employee` 인스턴스를 직접 `null` 처리한다 | **`EmployeeMapper.findByEmpNo` 앞에 캐시가 붙는 순간.** 현재는 매 호출이 새 객체를 돌려주므로 안전하지만 그건 *암묵적* 불변식이다. `@Cacheable` 이나 결재선 조회용 공유 맵이 들어가면 캐시된 인스턴스의 해시가 지워져 **그 사원의 이후 모든 로그인이 조용히 실패한다** | `LoginEmployee` 안에 해시 사본을 두고 그걸 지우거나, `EmployeeUserDetailsService` 가 방어적 복사본을 넘긴다 |
| C2 | CSRF hidden input 이 JSP 파일마다 손으로 복사되는 규약이다 (공유 조각 없음) | **Phase 2 가 POST 폼을 추가할 때.** 출퇴근 등록, 승인/반려 액션마다 같은 줄을 붙여야 하고, 빠뜨리면 컴파일·템플릿 단계에서 아무 신호가 없다가 **제출 시점에 403** 이 난다 | `common/csrf-input.jsp` 조각을 만들어 include 한다. 의존성 추가 없이 복붙 위험만 제거된다 |
| C3 | AJAX CSRF 배선이 jQuery `$.ajaxSetup` 전용이다 | **Phase 5 의 LLM 호출이 `fetch()` 를 쓸 때.** 스트리밍에는 `$.ajax` 가 잘 맞지 않아 `fetch` 를 쓰게 되는데, 그 경로는 `ajaxSetup` 을 타지 않아 헤더가 안 붙고 **조용히 403** 이 된다 | 해당 호출에 헤더를 직접 넣거나, `fetch` 래퍼를 `common.js` 에 둔다 |
| C4 | `SecurityConfig` 의 `permitAll` 목록에 `/WEB-INF/views/login.jsp` 가 들어 있다 | 지금 정상 동작하고 취약점도 아니다(컨테이너가 외부 직접 요청을 막는다). 다만 **뷰 경로가 외부 라우트로 오해될 수 있다** | 선택 사항: `dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()` 로 바꾸면 특정 뷰 경로를 규칙에 적지 않아도 된다. 뷰 대상이 바뀌어도 안 깨진다 |
| C5 | `defaultSuccessUrl("/", true)` 가 저장된 요청을 항상 버린다 | **Phase 2 가 딥링크를 만들 때.** "결재 대기 문서가 있습니다" 알림이 `/approvals/123` 을 가리키면, 로그인 후 항상 `/` 로 가버려 사용자가 링크를 다시 눌러야 한다. 오픈 리다이렉트 위험은 없다(저장된 요청은 서버가 만든 값이다) | `alwaysUse` 를 `false` 로 바꾼다 |

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
| Q4 | Anthropic API 키 보관 방식 | 계획서 3 | `application-local.yml`(gitignore) + 환경변수 `ANTHROPIC_API_KEY`. 계획서 1에서 `.gitignore` 항목을 확장자·파일명 변형까지 넣어뒀다. **넓은 와일드카드(`application-*.yml`)는 쓰지 않았다** — 계획서 6에서 정상 커밋해야 하는 프로필 설정이 생긴다 |
| Q6 | **첨부파일 업로드 디렉터리 경로** (`approval_attachment.file_path`) | 계획서 2 | 미정. **경로를 정하는 즉시 `.gitignore` 에 추가한다.** 프로젝트 디렉터리 안(`./upload/` 등)으로 정하면 업로드된 파일이 커밋될 수 있고, 저장소는 Phase 6 이후 public 이 된다. 계획서 1에서 추측성 경로를 미리 넣지 않은 이유는 이름이 다르면 방어가 되지 않기 때문이다 |
| Q5 | 반려 유형 6종 최종 확정 | 계획서 2 (설계서 §12에 이미 등록됨) | §5.2 정의대로 |

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
| 1. Phase 0+1 토대 | **작성 완료** — 실행 대기 |
| 2. Phase 2 전자결재 코어 | 미작성 (계획서 1 머지 후) |
| 3. Phase 3 AI 게이트웨이 | 미작성 (계획서 2와 동시 작성) |
| 4. Phase 4 근태 + 연동 | 미작성 |
| 5. Phase 5 AI 기능 | 미작성 |
| 6. Phase 6 마감 | 미작성 |
