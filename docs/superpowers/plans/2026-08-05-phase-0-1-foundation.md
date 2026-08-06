# FlowMate Phase 0+1 — 환경 구축 및 조직·사용자 토대 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 3 WAR가 JSP를 렌더링하고, PostgreSQL에서 읽은 조직·사원 데이터를 로그인한 사용자에게 보여주는 상태까지 만든다. 이후 모든 화면이 복사할 공통 레이아웃과 클래스 명명 규칙을 여기서 고정한다.

**Architecture:** Controller → Service → MyBatis Mapper → PostgreSQL의 3계층. 화면은 `WEB-INF/views/` 아래 JSP를 `<jsp:include>`로 조립하고, 공통 조각 5종(head/header/sidebar/footer/pagination)이 레이아웃 원본이 된다. 인증은 Spring Security 6 `formLogin` + `UserDetailsService` 구현체 하나. 순수 로직(페이징 계산, 검색 조건 정규화, 사용자 조회)은 DB 없이 도는 단위 테스트로, SQL과 로그인 흐름은 Docker PostgreSQL을 요구하는 통합 테스트(`*IT`)로 검증한다.

**Tech Stack:** Java 17, Spring Boot 3.2.5 (WAR), JSP + Jakarta JSTL 3.0, jQuery 3.7.1, MyBatis 3 (`mybatis-spring-boot-starter` 3.0.3), PostgreSQL 16 (Docker), Spring Security 6, Maven, JUnit 5 + AssertJ + Mockito

**참조 문서:**
- 설계서: [2026-08-05-flowmate-design.md](../specs/2026-08-05-flowmate-design.md)
- 로드맵·공통 규약: [2026-08-05-flowmate-roadmap.md](2026-08-05-flowmate-roadmap.md)

---

## 파일 구조

이 계획서가 끝나면 존재하는 파일. **각 파일의 책임을 여기서 고정하고, 작업 순서는 이 구조를 따른다.**

```
d:\projects\flowmate\
├─ pom.xml                                  Maven WAR 빌드, 의존성, Surefire/Failsafe 분리
├─ mvnw.cmd / mvnw / .mvn/                   Maven Wrapper (버전 고정)
├─ .gitignore
├─ README.md                                 실행법 · 데모 계정 · 설계 판단 기록
├─ docker-compose.yml                        PostgreSQL 16 (Phase 6에서 Tomcat 서비스 추가)
├─ docker/postgres/init/
│   ├─ 00-extension.sql                      pgcrypto (시드 비밀번호 해시 생성용)
│   ├─ 10-schema-org.sql                     department / position / employee
│   └─ 11-seed-org.sql                       부서 7 · 직급 6 · 사원 20
├─ docs/oracle-mapping.md                    PostgreSQL → Oracle 문법 대응표 (증분 관리)
└─ src/
   ├─ main/
   │  ├─ java/com/flowmate/
   │  │  ├─ FlowmateApplication.java          @SpringBootApplication
   │  │  ├─ ServletInitializer.java           외부 Tomcat 배포 진입점
   │  │  ├─ config/
   │  │  │  ├─ WebMvcConfig.java              webapp/static 을 /static/** 로 노출
   │  │  │  └─ SecurityConfig.java            SecurityFilterChain, PasswordEncoder
   │  │  ├─ common/
   │  │  │  ├─ web/Page.java                  페이징 계산 (순수 로직, 단위 테스트 대상)
   │  │  │  └─ mapper/DbHealthMapper.java     DB 연결 확인
   │  │  └─ org/
   │  │     ├─ controller/HomeController.java
   │  │     ├─ controller/LoginController.java
   │  │     ├─ controller/EmployeeController.java
   │  │     ├─ service/EmployeeService.java
   │  │     ├─ service/DepartmentService.java
   │  │     ├─ mapper/EmployeeMapper.java
   │  │     ├─ mapper/DepartmentMapper.java
   │  │     ├─ domain/Employee.java
   │  │     ├─ domain/EmployeeSearchCond.java 검색 조건 정규화 (순수 로직, 단위 테스트 대상)
   │  │     ├─ domain/DeptTreeItem.java       재귀 CTE 한 행
   │  │     └─ security/
   │  │        ├─ LoginEmployee.java          UserDetails 구현 (세션 적재 객체)
   │  │        ├─ EmployeeUserDetailsService.java
   │  │        └─ LoginEmployeeAdvice.java    모든 화면에 loginEmployee 주입
   │  ├─ resources/
   │  │  ├─ application.yml
   │  │  └─ mapper/org/EmployeeMapper.xml, DepartmentMapper.xml
   │  └─ webapp/
   │     ├─ static/css/style.css              Phase 6까지 클래스 목록만 유지
   │     ├─ static/js/jquery-3.7.1.min.js
   │     ├─ static/js/common.js               CSRF 헤더 · 페이징 링크 위임
   │     └─ WEB-INF/views/
   │        ├─ home.jsp
   │        ├─ login.jsp
   │        ├─ common/{head,header,sidebar,footer,pagination}.jsp
   │        └─ org/{employee-list,dept-tree}.jsp
   └─ test/
      ├─ java/com/flowmate/
      │  ├─ common/web/PageTest.java                       (단위)
      │  ├─ org/domain/EmployeeSearchCondTest.java          (단위)
      │  ├─ org/security/EmployeeUserDetailsServiceTest.java(단위, Mockito)
      │  ├─ org/mapper/EmployeeMapperIT.java                (통합, DB)
      │  ├─ org/mapper/DepartmentMapperIT.java              (통합, DB)
      │  └─ org/security/LoginIT.java                       (통합, DB + MockMvc)
      └─ resources/application.yml
```

**단위/통합 경계:** `*Test.java`는 Docker 없이 돈다. `*IT.java`는 Docker PostgreSQL을 요구한다.
`mvnw.cmd test` = 단위만, `mvnw.cmd verify` = 둘 다.

---

## Phase 0 — 환경 구축 (1.5일)

> 설계서 §3.1이 "입문자가 반나절~하루를 잃는 지점"이라고 지목한 구간이다.
> **탈출 조건: Task 3에 4시간 이상 소모되면 Spring Boot 2.7.18 + `javax` JSTL로 하향하고 README에 사유를 기록한다.**

### Task 0: 개발 도구 설치와 GitHub 연결

> **2026-08-06 확인 결과 이 PC에는 JDK · Maven · Docker · gh CLI 가 모두 없고, git remote 도 없다.**
> 설계서 §9는 Day 1에 "설치 확인"만 적었지만 확인할 대상 자체가 없으므로 설치가 선행 작업이다.
> git 은 설치되어 있고 사용자 정보(`kgu` / `kgu@jisystem.com`)도 설정되어 있다.
>
> **0C(Docker)는 재부팅을 요구한다.** 재부팅이 부담되면 **0A → 0B → Task 1·2·3 → 0C → Task 4** 순서로 진행한다.
> Task 1~3 은 DB 를 쓰지 않아 Docker 없이 완주할 수 있다.

#### Task 0A: JDK 17 · Maven 설치 — **완료 (2026-08-06)**

> **winget 을 쓰지 않고 zip 배포본으로 설치했다.** 사유:
> 1. `Apache.Maven` 은 이 PC 의 winget 인덱스에 **존재하지 않는다** (`winget search maven` 에 Apache Maven 이 없다)
> 2. `EclipseAdoptium.Temurin.17.JDK` 는 `wix`(MSI) 패키지라 머신 전역 설치에 UAC 가 필요하고,
>    실행 세션이 승격되지 않아(`Elevated: False`) 비대화형으로 진행할 수 없다
>
> zip 방식이 오히려 이 프로젝트에 더 맞는다 — 관리자 권한이 필요 없고, 버전이 경로에 박혀 재현 가능하며,
> 설치 위치가 `C:\Users\pc\dev-tools` 한 곳으로 모인다.

**설치된 것:**

| 도구 | 버전 | 경로 |
|---|---|---|
| Temurin JDK | 17.0.20+8 | `C:\Users\pc\dev-tools\jdk-17` |
| Apache Maven | 3.9.16 | `C:\Users\pc\dev-tools\maven-3.9.16` |

- [x] **Step 1: zip 을 내려받아 압축을 푼다**

```powershell
$tools = 'C:\Users\pc\dev-tools'
$dl = "$env:TEMP\flowmate-setup"
New-Item -ItemType Directory -Force $tools, $dl | Out-Null
$curl = "$env:SystemRoot\System32\curl.exe"

# JDK: Adoptium API 로 현재 GA 링크를 조회한 뒤 내려받는다 (버전을 손으로 박지 않는다)
$asset = (Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0]
& $curl -L --fail --retry 3 -o "$dl\jdk17.zip" $asset.binary.package.link

# Maven: 3.9.x 최신 (dlcdn 에는 현재 라인만 있다. 특정 버전은 archive.apache.org 사용)
& $curl -L --fail --retry 3 -o "$dl\maven.zip" 'https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'

Expand-Archive "$dl\jdk17.zip"  "$dl\jdk-extract" -Force
Expand-Archive "$dl\maven.zip"  "$dl\mvn-extract" -Force
Move-Item (Get-ChildItem "$dl\jdk-extract" -Directory | Select-Object -First 1).FullName "$tools\jdk-17"
Move-Item (Get-ChildItem "$dl\mvn-extract" -Directory | Select-Object -First 1).FullName "$tools\maven-3.9.16"
```

폴더명을 `jdk-17` 로 고정하는 이유: 원본 폴더명이 `jdk-17.0.20+8` 인데 `+` 가 경로에 들어가면
일부 스크립트·클래스패스 처리에서 문제가 될 수 있다. 패치 버전이 올라도 경로가 안 바뀌는 이점도 있다.

- [x] **Step 2: 사용자 범위 환경변수를 설정한다 (관리자 권한 불필요)**

```powershell
$javaHome = 'C:\Users\pc\dev-tools\jdk-17'
$mvnBin   = 'C:\Users\pc\dev-tools\maven-3.9.16\bin'
[Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'User')

# 기존 사용자 PATH 를 덮어쓰지 않고 없는 항목만 덧붙인다
$parts = ([Environment]::GetEnvironmentVariable('Path','User') -split ';') | Where-Object { $_ -ne '' }
foreach ($add in @("$javaHome\bin", $mvnBin)) { if ($parts -notcontains $add) { $parts += $add } }
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')
```

- [x] **Step 3: 현재 셸에 새 환경변수를 강제로 읽어들인다**

**Windows 에서 설치 프로그램이 바꾼 `PATH`·`JAVA_HOME` 은 이미 실행 중인 프로세스와 그 자식 프로세스에 전파되지 않는다.**
새 터미널을 열어도 부모(에디터·에이전트)가 예전 환경을 물려주므로 `java` 가 계속 "없음"으로 보인다.
설치 직후 실행하는 모든 명령 앞에 이 두 줄을 붙인다.

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
```

- [x] **Step 4: 설치를 검증한다**

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
Write-Output "JAVA_HOME = $env:JAVA_HOME"
java -version
javac -version
mvn -version
```

실제 결과 (2026-08-06):

```
JAVA_HOME = C:\Users\pc\dev-tools\jdk-17
openjdk version "17.0.20" 2026-07-21
OpenJDK Runtime Environment Temurin-17.0.20+8 (build 17.0.20+8)
javac 17.0.20
Apache Maven 3.9.16
Java version: 17.0.20, vendor: Eclipse Adoptium, runtime: C:\Users\pc\dev-tools\jdk-17
Default locale: ko_KR, platform encoding: MS949
```

> **`platform encoding: MS949` 에 대해** — 한글은 CP949 로 표현되므로 콘솔의 한국어
> `@DisplayName` 출력은 깨지지 않는다. 컴파일도 `pom.xml` 의 `project.build.sourceEncoding=UTF-8` 이
> 담당한다. 따라서 이것은 **차단 요소가 아니다.**
>
> 다만 남는 위험이 하나 있다: 명시적 charset 없이 파일을 읽는 코드(`new FileReader(f)`,
> `Files.readString(p)` 의 구버전 시그니처 등)는 MS949 로 읽어 UTF-8 파일을 깨뜨린다.
> Phase 1 에는 그런 코드가 없지만 **Phase 3 의 `PromptRepository` 가 프롬프트 파일을 읽는다.**
> Task 2 에서 `.mvn/jvm.config` 로 UTF-8 을 고정해 두는 것은 그때를 위한 위생 조치다
> (그리고 Phase 3 에서는 읽기 코드에 `StandardCharsets.UTF_8` 을 명시한다).

**실패 시:**

| 증상 | 조치 |
|---|---|
| `java` 를 못 찾는다 | Step 3의 두 줄을 실행하지 않았다. **Windows 는 설치가 바꾼 환경변수를 실행 중 프로세스와 그 자식에 전파하지 않는다** |
| `mvn -version` 의 Java 가 17이 아니다 | 다른 JDK 가 `PATH` 앞에 있다. `JAVA_HOME` 이 17을 가리키면 Maven 은 `JAVA_HOME` 을 우선하므로 `JAVA_HOME` 을 확인한다 |
| Adoptium API 응답이 비었다 | `https://adoptium.net/temurin/releases/` 에서 Windows x64 JDK **zip** 을 직접 내려받는다 (`.msi` 가 아니다) |

#### Task 0B: gh CLI 설치와 GitHub private 저장소 연결

- [x] **Step 1: gh CLI 를 설치한다 — 완료 (2026-08-06, v2.97.0)**

`GitHub.cli` 도 `wix`(MSI) 라 UAC 가 필요하다. Task 0A 와 같은 이유로 zip 배포본을 쓴다.

```powershell
$tools = 'C:\Users\pc\dev-tools'
$dl = "$env:TEMP\flowmate-setup"
$rel = Invoke-RestMethod 'https://api.github.com/repos/cli/cli/releases/latest' -Headers @{ 'User-Agent' = 'flowmate-setup' }
$asset = $rel.assets | Where-Object { $_.name -like 'gh_*_windows_amd64.zip' } | Select-Object -First 1
& "$env:SystemRoot\System32\curl.exe" -L --fail --retry 3 -o "$dl\gh.zip" $asset.browser_download_url
Expand-Archive "$dl\gh.zip" "$dl\gh-extract" -Force

# ★ 주의: gh 의 zip 은 루트에 최상위 폴더가 없고 bin/ 이 바로 들어 있다.
#   JDK/Maven zip 처럼 "첫 디렉터리를 올린다"고 하면 bin 자체가 올라와 경로가 한 단계 어긋난다.
Move-Item (Join-Path "$dl\gh-extract" 'bin') "$tools\gh"

$parts = ([Environment]::GetEnvironmentVariable('Path','User') -split ';') | Where-Object { $_ -ne '' }
if ($parts -notcontains "$tools\gh") { $parts += "$tools\gh"; [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User') }
```

검증:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
gh --version
```

기대: `gh version 2.97.0` (또는 그 이상)

- [ ] **Step 2: ★ 사용자가 직접 GitHub 인증을 수행한다**

> **이 Step 은 에이전트가 대신할 수 없다.** `gh auth login` 은 브라우저 또는 기기 코드 입력을 요구하는
> 대화형 명령이고, 도구 실행 환경은 표준 입력이 막혀 있어 즉시 실패한다.
> **사용자가 자신의 터미널에서 직접 실행한다.**

```powershell
gh auth login
```

선택: `GitHub.com` → `HTTPS` → `Login with a web browser` → 표시된 코드를 브라우저에 입력.

완료 확인 (이건 에이전트가 실행 가능):

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
gh auth status
```

기대: `Logged in to github.com account <계정명>` 과 `- Token scopes: ... 'repo' ...`

- [ ] **Step 3: 계획 문서를 먼저 커밋한다**

`gh repo create --push` 는 커밋이 있어야 동작한다. 현재 `docs/superpowers/plans/` 가 추적되지 않은 상태다.

```powershell
git add docs/superpowers/plans/
git commit -m "docs: 구현 로드맵과 Phase 0+1 계획서 추가

18.5일 분량 설계서를 Phase 단위 6개 계획서로 쪼갠다.
앞 Phase의 구현 결과가 뒤 Phase의 계획을 바꾸므로 2~6은 직전 Phase 머지 후 작성한다."
```

- [ ] **Step 4: private 저장소를 만들고 remote 로 연결한다**

```powershell
gh repo create flowmate --private --source=. --remote=origin --push
```

- [ ] **Step 5: 연결을 검증한다**

```powershell
git remote -v
git branch -vv
gh repo view --json name,visibility,url
```

기대:
- `origin` 이 `https://github.com/<계정>/flowmate.git`
- `main` 이 `origin/main` 을 추적한다 (`[origin/main]` 표시)
- `visibility` 가 `PRIVATE`

> **공개 범위: 진행 중에는 PRIVATE 로 두고 Phase 6 마감 후 PUBLIC 으로 전환한다.**
> 전환 직전에 반드시 전체 이력을 재검해야 한다 — Phase 3에서 Anthropic API 키를 다루므로
> `git log -p -- src/main/resources/` 로 키 문자열이 어느 커밋에도 없는지 확인한다.
> `.gitignore` 에 `application-local.yml` 을 넣어 두는 것(Task 1)이 1차 방어선이고, 이 재검이 2차다.
> **PUBLIC 전환 시점과 전환 전 재검은 Phase 6 계획서의 필수 항목으로 넣는다.**

- [ ] **Step 6: Phase 마감 시 push 하는 규칙을 확정한다**

이후 각 Phase 의 마지막 Step 에서 다음을 실행한다. 설계서 §9.2의 "커밋 로그 자체가 포트폴리오"가
실제 날짜로 쌓이게 하는 것이 목적이다 — 마지막에 한 번 몰아 push 하면 이력이 한 시점에 뭉친다.

```powershell
git push origin main --follow-tags
```

`--follow-tags` 를 쓰는 이유: 주석 태그(`git tag -a`)를 함께 보낸다. 없으면 Phase 태그가 로컬에만 남는다.

#### Task 0C: WSL2 · Docker Desktop 설치 (재부팅 포함)

> **Task 4 시작 전까지만 끝내면 된다.** Task 1~3 은 DB 를 쓰지 않는다.

- [ ] **Step 1: WSL2 를 설치한다 (관리자 PowerShell)**

현재 상태: `wsl --status` 가 실패하고 `HypervisorPresent` 가 `False` 다 — WSL 기능이 꺼져 있다.

```powershell
wsl --install --no-distribution
```

- [ ] **Step 2: ★ 재부팅한다**

가상화 기능 활성화는 재부팅 없이 적용되지 않는다.

재부팅 후 확인:

```powershell
wsl --status
(Get-CimInstance Win32_ComputerSystem).HypervisorPresent
```

기대: `wsl --status` 가 기본 버전 정보를 출력하고, `HypervisorPresent` 가 `True`.

`False` 가 계속 나오면 BIOS/UEFI 에서 가상화(Intel VT-x / AMD-V)가 꺼져 있는 것이다. BIOS 설정이 필요하다.

- [ ] **Step 3: Docker Desktop 을 설치한다**

```powershell
winget install --id Docker.DockerDesktop --exact --accept-package-agreements --accept-source-agreements
```

- [ ] **Step 4: ★ 사용자가 Docker Desktop 을 한 번 실행해 초기 설정을 완료한다**

> 최초 실행 시 라이선스 동의 화면이 뜨고, 이후 백그라운드 엔진이 기동될 때까지 1~2분이 걸린다.
> **엔진이 뜨기 전에는 `docker` 명령이 `error during connect` 로 실패한다** — 설치 실패가 아니다.

- [ ] **Step 5: Docker 를 검증한다**

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
docker --version
docker compose version
docker run --rm hello-world
```

기대: 세 명령 모두 성공하고 마지막이 `Hello from Docker!` 를 출력한다.

- [ ] **Step 6: Task 0 완료 기준을 확인한다**

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
java -version; mvn -version; docker compose version; gh auth status; git remote -v
```

- [ ] `java -version` = 17.x, `JAVA_HOME` 설정됨
- [ ] `mvn -version` = 3.9.x, Java 17 보고
- [ ] `docker compose version` 성공, `hello-world` 통과
- [ ] `gh auth status` = 로그인됨
- [ ] `git remote -v` 에 `origin` 존재, `main` 이 `origin/main` 추적
- [ ] GitHub 저장소가 PRIVATE

---

### Task 1: 저장소 골격과 사전 점검

**Files:**
- Create: `.gitignore`
- Create: `.gitattributes`
- Create: `README.md`

- [ ] **Step 1: Task 0A 가 끝났는지만 확인한다**

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
java -version
mvn -version
git --version
```

기대: `java` 17.x, `mvn` 3.9.x. Spring Boot 3은 Java 17 미만에서 뜨지 않는다.
실패하면 Task 0A 로 돌아간다. (Docker 는 Task 4 부터 필요하므로 여기서 확인하지 않는다.)

- [ ] **Step 2: 작업 브랜치를 만든다**

```powershell
git switch -c feat/phase-0-bootstrap
```

- [ ] **Step 3: `.gitignore` 를 만든다**

```gitignore
# 빌드 산출물
target/
*.war

# IDE
.idea/
*.iml
*.ipr
*.iws
.settings/
.classpath
.project
.vscode/

# 로그
*.log
logs/

# 로컬 설정 · 비밀값 (Phase 3의 Anthropic API 키가 여기 들어간다)
src/main/resources/application-local.yml
.env

# OS
Thumbs.db
.DS_Store
```

- [ ] **Step 3b: `.gitattributes` 를 만든다**

이 PC 의 `core.autocrlf` 는 `true` 라 지금은 저장소에 LF 로 저장된다. 그러나
**`autocrlf` 는 로컬 설정이지 저장소 속성이 아니다** — `false` 로 설정된 다른 PC 에서 커밋하면
CRLF 가 저장소에 들어가고, Phase 6 의 Linux 컨테이너에서 `mvnw` 나 셸 스크립트가 깨진다.
줄바꿈 규칙을 저장소에 고정한다.

```gitattributes
# 기본: 텍스트는 저장소에 LF 로 저장한다
* text=auto eol=lf

# Windows 전용 스크립트는 작업 사본에서 CRLF 여야 한다
*.cmd  text eol=crlf
*.bat  text eol=crlf
*.ps1  text eol=crlf

# Linux 에서 실행되는 것은 반드시 LF (CRLF 면 bad interpreter 로 죽는다)
mvnw       text eol=lf
*.sh       text eol=lf
Dockerfile text eol=lf

# 이진 파일은 변환하지 않는다
*.jar   binary
*.war   binary
*.png   binary
*.jpg   binary
*.ico   binary
*.woff  binary
*.woff2 binary
```

- [ ] **Step 4: `README.md` 골격을 만든다**

설계서 §12.1이 확정한 제목·부제를 그대로 쓴다.

```markdown
# FlowMate

### AI 사전점검 그룹웨어 — 전자결재 · 근태관리

AI가 결재 반려를 미리 막아주는 사내 그룹웨어.

- 설계서: [docs/superpowers/specs/2026-08-05-flowmate-design.md](docs/superpowers/specs/2026-08-05-flowmate-design.md)
- 구현 로드맵: [docs/superpowers/plans/2026-08-05-flowmate-roadmap.md](docs/superpowers/plans/2026-08-05-flowmate-roadmap.md)

## 기술 스택

Java 17 · Spring Boot 3.2 (WAR) · JSP + JSTL + jQuery · MyBatis 3 · PostgreSQL 16 · Spring Security 6 · Maven · Docker

## 실행 방법

작성 예정 (Phase 0 Task 5에서 채운다)

## 설계 판단 기록

작성 예정 (Phase 6)
```

- [ ] **Step 5: 커밋한다**

> **★ Windows PowerShell 주의:** 커밋 메시지 본문에 큰따옴표가 들어가면
> PowerShell 5.1 의 네이티브 인자 전달이 깨져 git 이 메시지 조각을 pathspec 으로 오인한다
> (`error: pathspec '...' did not match any file(s)`).
> **여러 줄 메시지는 파일에 쓴 뒤 `git commit -F <파일>` 로 넘긴다.**

```powershell
git add .gitignore .gitattributes README.md
# 메시지를 임시 파일에 쓴 뒤:
git commit -F <메시지파일경로>
```

메시지 내용:

```
chore: 저장소 골격 추가 - gitignore, gitattributes, README 뼈대

WAR 산출물과 로컬 비밀값 파일을 추적 대상에서 제외한다.
Phase 3의 Anthropic API 키가 application-local.yml 로 들어갈 예정이므로 미리 등록한다.

줄바꿈 규칙을 gitattributes 로 저장소에 고정한다. core.autocrlf 는 로컬 설정이라
저장소 속성이 아니고, Phase 6 은 Linux 컨테이너에 배포하므로 mvnw 와 셸 스크립트가
CRLF 로 커밋되면 bad interpreter 로 죽는다.
```

---

### Task 2: Maven WAR 프로젝트와 Spring Boot 부팅

**중요:** 이 Task의 `pom.xml`에는 **MyBatis·PostgreSQL·Security 의존성을 넣지 않는다.**
MyBatis 스타터는 `DataSource` 자동설정을 켜므로 DB가 없으면 애플리케이션이 뜨지 않고,
Security 스타터는 기본 Basic 인증으로 모든 URL을 막아 Task 3의 JSP 검증을 방해한다.
각각 Task 5, Task 11에서 추가한다.

**Files:**
- Create: `pom.xml`
- Create: `.mvn/jvm.config`
- Create: `src/main/java/com/flowmate/FlowmateApplication.java`
- Create: `src/main/java/com/flowmate/ServletInitializer.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/flowmate/FlowmateApplicationIT.java`

- [ ] **Step 1: `pom.xml` 을 만든다**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.flowmate</groupId>
    <artifactId>flowmate</artifactId>
    <version>1.0.0</version>
    <packaging>war</packaging>
    <name>flowmate</name>
    <description>AI 사전점검 그룹웨어 — 전자결재 · 근태관리</description>

    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jstl-api.version>3.0.0</jstl-api.version>
        <jstl-impl.version>3.0.1</jstl-impl.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 산출물은 WAR이고 외부 Tomcat 10.1에 배포한다. 내장 Tomcat은 개발 실행용이므로 provided. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- JSP 컴파일러(Jasper). 외부 Tomcat이 제공하므로 provided. 이게 없으면 JSP가 404가 된다. -->
        <dependency>
            <groupId>org.apache.tomcat.embed</groupId>
            <artifactId>tomcat-embed-jasper</artifactId>
            <scope>provided</scope>
        </dependency>

        <!--
          JSTL: Jakarta EE 10 좌표를 써야 한다 (설계서 §3.1).
          javax 시절의 jstl:jstl 이나 taglibs-standard 를 쓰면 taglib URI가 안 맞아
          태그가 그대로 문자열로 출력된다. Tomcat은 JSTL을 제공하지 않으므로 provided가 아니다.
        -->
        <dependency>
            <groupId>jakarta.servlet.jsp.jstl</groupId>
            <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
            <version>${jstl-api.version}</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.web</groupId>
            <artifactId>jakarta.servlet.jsp.jstl</artifactId>
            <version>${jstl-impl.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <!-- 산출물 이름을 flowmate.war 로 고정한다 (설계서 §12.1) -->
        <finalName>flowmate</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <!--
                  spring-boot:run 은 별도의 애플리케이션 JVM 을 띄우므로
                  .mvn/jvm.config 도 Surefire argLine 도 물려받지 않는다.
                  JDK 17 은 file.encoding 을 플랫폼 문자셋(이 PC 는 MS949)에서 가져오고
                  UTF-8 기본값(JEP 400)은 JDK 18 부터다. 개발 실행 JVM 도 UTF-8 로 고정한다.
                -->
                <configuration>
                    <jvmArguments>-Dfile.encoding=UTF-8</jvmArguments>
                </configuration>
            </plugin>
            <!--
              *Test.java 는 Surefire(mvn test), *IT.java 는 Failsafe(mvn verify)가 실행한다.
              DB가 필요한 테스트를 mvn test 에서 분리하기 위한 배선이다.

              argLine 으로 문자셋을 고정하는 이유: 이 개발 PC 의 플랫폼 인코딩이 MS949 이고,
              테스트 JVM 은 Maven JVM(.mvn/jvm.config)의 설정을 물려받지 않는 별도 포크다.
            -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-Dfile.encoding=UTF-8</argLine>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <argLine>-Dfile.encoding=UTF-8</argLine>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Maven Wrapper 를 생성하고 JVM 문자셋을 고정한다**

```powershell
mvn -N wrapper:wrapper -Dmaven=3.9.16
```

기대: `mvnw.cmd`, `mvnw`, `.mvn/wrapper/maven-wrapper.properties` 생성.
이후 모든 명령은 `.\mvnw.cmd` 를 쓴다 (Maven 버전이 고정되어 다른 PC에서도 같은 결과가 나온다).

이어서 `.mvn/jvm.config` 를 만든다 (파일 내용은 아래 한 줄뿐이다):

```
-Dfile.encoding=UTF-8
```

이 PC 의 Maven 이 `platform encoding: MS949` 를 보고하기 때문이다. 지금 당장 깨지는 것은 없지만
Phase 3 의 `PromptRepository` 가 UTF-8 프롬프트 파일을 읽으므로 기본 문자셋을 여기서 고정한다.
**UTF-8 을 세 곳에 따로 걸어야 하는 이유** — JVM 이 세 개고 서로 설정을 물려받지 않는다.

| JVM | 무엇이 담당하는가 |
|---|---|
| Maven 자신 | `.mvn/jvm.config` |
| 테스트 포크 (Surefire · Failsafe) | `pom.xml` 의 각 플러그인 `argLine` |
| 애플리케이션 (`spring-boot:run`) | `spring-boot-maven-plugin` 의 `jvmArguments` |

이 PC 의 JDK 17 은 `java -XshowSettings:properties -version` 에서 `file.encoding = MS949` 를 보고한다.
**UTF-8 기본값(JEP 400)은 JDK 18 부터**이므로 Java 17 에서는 명시하지 않으면 플랫폼 문자셋을 쓴다.

세 곳 중 하나를 빠뜨리면 **"테스트는 통과하는데 실제 실행만 깨지는"** 함정이 된다.
Phase 3 의 `PromptRepository` 가 UTF-8 프롬프트 파일을 읽으므로 여기서 셋 다 막는다.

- [ ] **Step 2b: `mvnw` 에 실행 권한을 준다**

`mvn wrapper:wrapper` 가 만든 `mvnw` 는 Windows 에서 mode `100644` 로 커밋된다.
**Linux/macOS 에서 클론하면 `./mvnw` 가 `Permission denied` 로 죽는다** — 빌드 오류가 아니라
처음 실행하는 명령부터 실패하는 상태다. Phase 6 에서 저장소를 공개하고 Linux 컨테이너에 배포하므로 지금 고친다.

```powershell
git update-index --chmod=+x mvnw
git ls-files -s mvnw mvnw.cmd
```

기대: `mvnw` 가 `100755`, `mvnw.cmd` 는 `100644` 유지 (Windows 배치 파일이라 실행 비트가 필요없다).

- [ ] **Step 3: 애플리케이션 진입점 두 개를 만든다**

`src/main/java/com/flowmate/FlowmateApplication.java`:

```java
package com.flowmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlowmateApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowmateApplication.class, args);
    }
}
```

`src/main/java/com/flowmate/ServletInitializer.java`:

```java
package com.flowmate;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 외부 Tomcat 10.1 에 WAR 로 배포될 때의 진입점.
 * main() 은 개발 중 실행에만 쓰이고, 컨테이너 배포 시에는 이 클래스가 사용된다.
 * WAR 패키징에서 이 클래스가 없으면 컨테이너가 Spring 컨텍스트를 시작하지 못한다.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(FlowmateApplication.class);
    }
}
```

- [ ] **Step 4: `application.yml` 을 만든다**

```yaml
spring:
  application:
    name: flowmate
  mvc:
    view:
      prefix: /WEB-INF/views/
      suffix: .jsp

server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      force: true

logging:
  level:
    com.flowmate: DEBUG
```

- [ ] **Step 5: 컨텍스트 로드 테스트로 부팅을 검증한다**

콘솔에서 `Tomcat started` 를 눈으로 확인하는 대신 자동화된 테스트로 고정한다. 회귀 테스트로 남고,
서버를 띄워 두는 블로킹 명령이 필요 없다.

`src/test/java/com/flowmate/FlowmateApplicationIT.java`:

```java
package com.flowmate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 컨텍스트가 끝까지 로드되는지만 확인한다.
 *
 * 이름이 *Tests 가 아니라 *IT 인 이유:
 * Task 5 에서 DataSource 를 추가하면 이 테스트가 DB 연결을 요구하게 된다.
 * *Tests 였다면 그 순간부터 `mvnw test` 가 Docker 없이 실패해
 * "단위 테스트는 Docker 없이 돈다" 는 규칙이 깨진다. Failsafe(*IT)에 두어 경계를 지킨다.
 */
@SpringBootTest
class FlowmateApplicationIT {

    @Test
    @DisplayName("Spring 컨텍스트가 로드된다")
    void contextLoads() {
        // 컨텍스트 로딩 자체가 검증 대상이다. 실패하면 예외로 터진다.
    }
}
```

실행:

```powershell
.\mvnw.cmd clean verify
```

기대: `BUILD SUCCESS`, `Tests run: 1` (Failsafe), `target/flowmate.war` 생성.

> **`~/.m2` 가 비어 있으면 첫 실행이 수백 MB 를 내려받는다.** 수 분이 걸릴 수 있으며 정상이다.

- [ ] **Step 5b: 개발용 실행 명령을 확인한다 (선택)**

```powershell
.\mvnw.cmd spring-boot:run
```

기대: 콘솔에 `Tomcat started on port 8080` 과 `Started FlowmateApplication in ...`. `Ctrl+C` 로 종료.
Task 3 에서 브라우저 확인이 필요하므로 이 명령이 동작하는지는 알아 둔다.

> `packaging`이 `war`이고 `spring-boot-starter-tomcat`이 `provided`여도 `spring-boot:run`은 동작한다.
> 플러그인이 provided 스코프를 실행 클래스패스에 포함시킨다. IDE에서 `main()`을 직접 실행할 때는
> 실행 구성이 provided 의존성을 포함하는지 확인해야 하므로, **개발 중에는 `spring-boot:run`을 표준 명령으로 쓴다.**

- [ ] **Step 6: 커밋한다**

```powershell
git add pom.xml mvnw mvnw.cmd .mvn src/main/java src/main/resources/application.yml
git commit -m "feat: Spring Boot 3.2 WAR 프로젝트 배선

산출물을 WAR로 고정하고 내장 Tomcat과 Jasper를 provided 로 둔다.
외부 Tomcat 배포가 회사 환경과 동일한 산출물이기 때문이다.
DataSource 자동설정이 켜지면 DB 없이 뜨지 않으므로 MyBatis 의존성은 아직 넣지 않는다."
```

---

### Task 3: JSP + Jakarta JSTL 배선 검증 ★ 최대 리스크

> 설계서 §3.1의 유일한 목표. **4시간 초과 시 탈출 조건 발동.**
>
> **★ 착수 전 확인한 사실 — 설계서 §3.1 의 위험 서술을 정정한다.**
>
> `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` jar 안의 TLD 를 직접 열어 확인한 결과,
> 이 jar 은 **두 URI 계열을 모두 등록한다.**
>
> | TLD 파일 | 등록된 URI |
> |---|---|
> | `c.tld` | `jakarta.tags.core` |
> | `c-1_2.tld` | `http://java.sun.com/jsp/jstl/core` |
> | `fmt.tld` / `fmt-1_1.tld` | `jakarta.tags.fmt` / `http://java.sun.com/jsp/jstl/fmt` |
> | `fn.tld` / `fn-1_1.tld` | `jakarta.tags.functions` / `http://java.sun.com/jsp/jstl/functions` |
>
> 따라서 설계서가 말한 "기존 URI 를 쓰면 태그가 문자열로 출력되거나 500 발생" 은 **이 artifact 에서는 성립하지 않는다.**
> 구형 URI 도 해석된다. 실제 실패 원인은 URI 가 아니라 **artifact 선택**이다.
>
> **진짜 실패 모드 세 가지 (혼동하면 엉뚱한 곳을 고치게 된다):**
>
> | 증상 | 실제 원인 |
> |---|---|
> | 500 `The absolute uri [...] cannot be resolved` | JSTL **구현체 jar 자체가 없다** (API 만 있음). URI 오타여도 이 증상 |
> | `NoClassDefFoundError: javax/servlet/jsp/...` | `javax` 시절 artifact(`javax.servlet:jstl` 등)를 Tomcat 10.1 에서 썼다. **이것이 설계서가 경계한 진짜 함정** |
> | 태그가 문자열로 그대로 출력 | `<%@ taglib %>` **선언을 빠뜨렸다**. URI 문제가 아니다 |
>
> 그래도 `jakarta.tags.*` 를 쓴다 — 구형 URI 는 하위호환용 잔존물이고 Jakarta EE 10 의 정식 URI 가 이것이다.
> **위험도가 설계서 예상보다 낮으므로 탈출 조건까지 갈 가능성은 작다.**

**Files:**
- Create: `src/main/java/com/flowmate/org/controller/HomeController.java`
- Create: `src/main/webapp/WEB-INF/views/home.jsp`

- [ ] **Step 1: `HomeController` 를 만든다**

```java
package com.flowmate.org.controller;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        // serverTime 이 java.util.Date 인 이유: <fmt:formatDate> 가 java.time 타입을 받지 못한다.
        // 이 화면은 fmt 태그리브가 동작하는지 확인하는 용도까지 겸한다.
        model.addAttribute("serverTime", new Date());
        model.addAttribute("modules", List.of("전자결재", "근태관리"));
        return "home";
    }
}
```

- [ ] **Step 2: `home.jsp` 를 만든다**

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>FlowMate</title>
</head>
<body>
<h1>FlowMate</h1>
<p>서버 시각: <fmt:formatDate value="${serverTime}" pattern="yyyy-MM-dd HH:mm:ss"/></p>
<ul>
    <c:forEach items="${modules}" var="m">
        <li><c:out value="${m}"/></li>
    </c:forEach>
</ul>
</body>
</html>
```

- [ ] **Step 3: 브라우저에서 확인한다**

```powershell
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080/` 접속.

**통과 기준 (셋 다 만족해야 한다):**
1. `서버 시각: 2026-08-06 14:23:11` 형태의 **포맷된 날짜**가 보인다 (`<fmt:formatDate .../>` 문자열이 아니다)
2. `전자결재`, `근태관리` 두 개의 `<li>` 가 보인다
3. 한글이 깨지지 않는다

**실패 시 진단 순서:**

| 증상 | 원인 | 조치 |
|---|---|---|
| 404 | `tomcat-embed-jasper` 누락 또는 `spring.mvc.view.prefix` 오타 | pom과 application.yml 확인 |
| 500 `The absolute uri [...] cannot be resolved` | JSTL **구현체** (`org.glassfish.web:jakarta.servlet.jsp.jstl`) 누락. API 만 있으면 이 증상이다. URI 오타여도 같은 증상 | pom 의 구현체 의존성과 `<%@ taglib %>` 의 URI 철자 확인 |
| `NoClassDefFoundError: javax/servlet/jsp/...` | **`javax` 시절 artifact** 를 Tomcat 10.1 에서 썼다 (`javax.servlet:jstl`, `taglibs-standard` 등). 설계서 §3.1이 경계한 진짜 함정 | pom 에서 `jakarta.*` 좌표만 남긴다 |
| 태그가 문자열로 그대로 출력 | `<%@ taglib %>` **선언 누락**. URI 문제가 아니다 | JSP 상단의 taglib 지시자 확인 |
| 500 `Unable to compile class for JSP` | Jasper가 provided인데 실행 클래스패스에 없음 | `spring-boot:run` 으로 실행하는지 확인 |
| 한글 깨짐 | `pageEncoding` 누락 또는 `server.servlet.encoding.force` 미설정 | 둘 다 확인 |

- [ ] **Step 4: 커밋한다**

```powershell
git add src/main/java/com/flowmate/org/controller/HomeController.java src/main/webapp
git commit -m "feat: JSP + Jakarta JSTL 배선 확인

Boot 3은 Jakarta 네임스페이스를 쓰므로 taglib URI가 jakarta.tags.* 로 바뀌었다.
javax 시절 URI를 쓰면 500이 아니라 태그가 문자열로 조용히 출력되므로
core와 fmt 두 태그리브가 실제로 동작하는 화면으로 고정해 둔다."
```

---

### Task 4: PostgreSQL 컨테이너

> **★ 실행 순서 변경 (2026-08-06).** Task 0C(WSL2 + Docker Desktop)가 관리자 권한과 재부팅을 요구하는
> 사용자 작업이라 미완인 상태다. **Task 4 · 5 · 6 · 10 · 11 · 12 는 DB 를 요구하므로 여기서 멈춘다.**
>
> 대신 DB 가 필요 없는 Task 를 먼저 소화했다: **Task 7 → 8 → 9**.
> (`Page<T>` 와 `EmployeeSearchCond` 는 순수 로직, 공통 레이아웃 조각은 JSP·CSS·jQuery 뿐이다.)
>
> Docker 가 준비되면 **Task 4 → 5 → 6 → 10 → 11 → 12 → 13** 순서로 재개한다.
> Task 9 가 먼저 실행되면 `home.jsp` 에 `${dbInfo}` 가 빈 값으로 렌더링되는데 **정상이다** —
> Task 5 가 `DbHealthService` 를 붙이면 채워진다.

**Files:**
- Create: `docker-compose.yml`
- Create: `docker/postgres/init/00-extension.sql`

- [ ] **Step 1: `docker-compose.yml` 을 만든다**

```yaml
# Phase 6 에서 Tomcat 서비스를 추가한다. 지금은 DB만 띄운다.
services:
  postgres:
    image: postgres:16-alpine
    container_name: flowmate-postgres
    environment:
      POSTGRES_DB: flowmate
      POSTGRES_USER: flowmate
      POSTGRES_PASSWORD: flowmate
      TZ: Asia/Seoul
      PGTZ: Asia/Seoul
      # 한글 정렬·비교를 위해 초기화 시 로케일을 지정한다
      POSTGRES_INITDB_ARGS: "--encoding=UTF8 --locale=C"
    ports:
      - "5432:5432"
    volumes:
      # 알파벳 순으로 실행된다. 데이터 볼륨이 비어 있을 때만 실행되는 점에 주의.
      - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
      - flowmate-pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U flowmate -d flowmate"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  flowmate-pgdata:
```

- [ ] **Step 2: `docker/postgres/init/00-extension.sql` 을 만든다**

```sql
-- 시드 비밀번호를 SQL 안에서 BCrypt 해시로 만들기 위해 필요하다.
-- pgcrypto 의 crypt(pw, gen_salt('bf', 10)) 은 $2a$10$... 형식을 생성하고,
-- Spring Security 의 BCryptPasswordEncoder 가 그 형식을 그대로 검증한다.
-- 이 확장이 없으면 11-seed-org.sql 이 실패한다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

- [ ] **Step 3: 컨테이너를 띄우고 확장이 설치됐는지 확인한다**

```powershell
docker compose up -d postgres
docker compose ps
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "SELECT extname FROM pg_extension WHERE extname = 'pgcrypto';"
```

기대: `ps` 의 STATUS 가 `healthy`, 마지막 명령이 `pgcrypto` 한 행을 반환.

- [ ] **Step 4: pgcrypto 가 Spring 이 검증할 수 있는 형식을 만드는지 눈으로 확인한다**

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "SELECT crypt('flowmate1!', gen_salt('bf', 10));"
```

기대: `$2a$10$` 으로 시작하는 60자 문자열.

> **★ 검증 완료 (2026-08-06). 이 전제는 확인된 사실이다 — 추측이 아니다.**
>
> Postgres 가 자기 해시를 검증하는 것과 **Java 가 그 해시를 검증하는 것은 별개 문제**이므로,
> 시드 20건을 만들기 전에 교차 검증했다. pgcrypto 해시 3개를 뽑아
> `spring-security-crypto:6.2.4` 를 클래스패스에 올린 단독 프로그램으로 확인한 결과:
>
> | 검사 | 결과 |
> |---|---|
> | `BCrypt.checkpw("flowmate1!", pgHash)` | 3건 모두 `true` |
> | `BCrypt.checkpw("wrong", pgHash)` | 3건 모두 `false` |
> | `new BCryptPasswordEncoder().matches("flowmate1!", pgHash)` | 3건 모두 `true` |
> | `matches("wrong", pgHash)` | 3건 모두 `false` |
> | 역방향 — Postgres 가 Java 생성 해시 검증 | `t` / 오답 `f` |
>
> 접두사 `$2a$10$`, 길이 60자. **양방향 완전 호환.**
> 따라서 Task 6 의 시드는 `crypt()` 방식을 그대로 쓴다.
>
> `BCryptPasswordEncoder` 는 생성자에서 commons-logging 을 참조하므로 단독 실행 시
> `spring-jcl` 도 클래스패스에 있어야 한다 (`NoClassDefFoundError: org/apache/commons/logging/LogFactory`).
> 애플리케이션 안에서는 `spring-core` 가 이미 끌고 오므로 문제되지 않는다.

만약 `$2a$` 가 아닌 접두사가 나오는 환경이라면 대안은
`BCryptPasswordEncoder().encode(...)` 결과를 시드 SQL 에 직접 박는 것이다.

- [ ] **Step 4b: 타임존 · 인코딩 · 정렬 규칙이 적용됐는지 확인한다**

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "SHOW timezone; SHOW server_encoding;"
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "SELECT datname, datcollate, datctype FROM pg_database WHERE datname = 'flowmate';"
```

기대: `Asia/Seoul`, `UTF8`, `flowmate | C | C` (실측 확인됨).

> **`SHOW lc_collate` 는 `postgres:16-alpine` 에서 동작하지 않는다.**
> musl libc 기반 이미지에서 세션 GUC 로 노출되지 않아
> `ERROR: unrecognized configuration parameter "lc_collate"` 가 난다.
> **설정 오류가 아니므로 고치려 들지 말고** 위처럼 `pg_database` 로 확인한다.
>
> 로케일을 `C` 로 고정한 이유: Task 10 의 조직도 재귀 CTE 가 `sort_path` **문자열 비교**로
> 계층을 정렬한다. 정렬 규칙이 환경마다 달라지면 조직도 순서가 흔들린다.

- [ ] **Step 5: 커밋한다**

```powershell
git add docker-compose.yml docker/
git commit -m "feat: PostgreSQL 16 컨테이너 구성

init 스크립트를 번호 접두사로 관리해 Phase별 스키마를 순서대로 적용한다.
시드 비밀번호 해시를 손으로 복사하지 않기 위해 pgcrypto 를 먼저 설치한다."
```

---

### Task 5: MyBatis 배선 — DB 값을 JSP에 출력

**Files:**
- Modify: `pom.xml` (의존성 2개 추가)
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/flowmate/common/mapper/DbHealthMapper.java`
- Create: `src/main/java/com/flowmate/common/service/DbHealthService.java`
- Modify: `src/main/java/com/flowmate/org/controller/HomeController.java`
- Modify: `src/main/webapp/WEB-INF/views/home.jsp`
- Modify: `README.md`

- [ ] **Step 1: `pom.xml` 에 MyBatis 와 PostgreSQL 드라이버를 추가한다**

`spring-boot-starter-test` 앞에 넣는다.

```xml
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```

`mybatis-spring-boot-starter` 3.0.x 가 Spring Boot 3.x 대응 라인이다. 2.x 를 쓰면 `javax` 네임스페이스라 뜨지 않는다.

- [ ] **Step 2: `application.yml` 에 DataSource 와 MyBatis 설정을 추가한다**

`spring:` 블록에 `datasource:` 를 추가하고, 파일 끝에 `mybatis:` 블록을 붙인다.

```yaml
spring:
  application:
    name: flowmate
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/flowmate
    username: flowmate
    password: flowmate
  mvc:
    view:
      prefix: /WEB-INF/views/
      suffix: .jsp

server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      force: true

mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  # 재귀 스캔이므로 클래스 단순명이 전 패키지에서 유일해야 한다 (로드맵 §3.2)
  type-aliases-package: com.flowmate
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.flowmate: DEBUG
```

`map-underscore-to-camel-case: true` 가 `dept_id` → `deptId` 매핑을 처리한다. 이게 없으면 모든 조회 결과가 null이 된다.

- [ ] **Step 3: `DbHealthMapper` 를 만든다**

```java
package com.flowmate.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * DB 연결 상태 확인용. 화면에 DB 이름과 버전을 노출해
 * "애플리케이션이 떴다"와 "DB까지 연결됐다"를 구분한다.
 */
@Mapper
public interface DbHealthMapper {

    @Select("SELECT current_database() || ' / ' || split_part(version(), ' ', 2)")
    String selectDbInfo();
}
```

- [ ] **Step 3b: `DbHealthService` 를 만든다**

> **이 클래스를 건너뛰고 `HomeController` 에 `DbHealthMapper` 를 직접 주입하면 안 된다.**
> 설계서 §4.3 은 "Controller는 Service만 호출. Mapper 직접 호출 금지 — **위반하면 리뷰에서 반려**" 다.
> 호출이 사소하다는 이유로 예외를 두면, 그것이 계층 규칙이 무너지는 시작점이 된다.
> 이 프로젝트의 **첫 DB 접근**이므로 여기서 규칙을 지키는 모습이 이후 전부의 기준이 된다.

```java
package com.flowmate.common.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.common.mapper.DbHealthMapper;

/**
 * DB 연결 상태 조회.
 *
 * 하는 일이 위임 한 줄뿐이지만 Service 계층을 두는 이유는 설계서 §4.3 때문이다.
 * Controller 가 Mapper 를 직접 부르지 않는다는 규칙에 예외를 만들지 않는다.
 */
@Service
public class DbHealthService {

    private final DbHealthMapper dbHealthMapper;

    public DbHealthService(DbHealthMapper dbHealthMapper) {
        this.dbHealthMapper = dbHealthMapper;
    }

    @Transactional(readOnly = true)
    public String findDbInfo() {
        return dbHealthMapper.selectDbInfo();
    }
}
```

- [ ] **Step 4: `HomeController` 가 DB 정보를 모델에 담게 수정한다**

```java
package com.flowmate.org.controller;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.flowmate.common.service.DbHealthService;

@Controller
public class HomeController {

    private final DbHealthService dbHealthService;

    public HomeController(DbHealthService dbHealthService) {
        this.dbHealthService = dbHealthService;
    }

    @GetMapping("/")
    public String home(Model model) {
        // serverTime 이 java.util.Date 인 이유: <fmt:formatDate> 가 java.time 타입을 받지 못한다.
        model.addAttribute("serverTime", new Date());
        model.addAttribute("modules", List.of("전자결재", "근태관리"));
        model.addAttribute("dbInfo", dbHealthService.findDbInfo());
        return "home";
    }
}
```

- [ ] **Step 5: `home.jsp` 에 DB 정보 한 줄을 추가한다**

`<ul>` 블록 뒤에 넣는다.

```jsp
<p>DB: <c:out value="${dbInfo}"/></p>
```

- [ ] **Step 6: 실행해서 DB 값이 화면에 보이는지 확인한다**

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080/` 접속. 기대: `DB: flowmate / 16.x`

이 한 줄이 설계서 Phase 0 Day 1.5의 완료 기준("DB 값 하나를 JSP 화면에 출력한다")이다.

- [ ] **Step 7: `README.md` 의 실행 방법을 채운다**

```markdown
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
```

- [ ] **Step 8: 커밋하고 Phase 0 을 마감한다**

```powershell
git add pom.xml src/main README.md
git commit -m "feat: MyBatis 배선 후 DB 값을 JSP에 출력

애플리케이션 기동과 DB 연결을 화면에서 구분하기 위해 현재 DB명과 버전을 홈에 노출한다.
map-underscore-to-camel-case 를 켜지 않으면 이후 모든 조회 결과가 null이 되므로 여기서 설정한다."

git switch main
git merge --no-ff feat/phase-0-bootstrap -m "merge: Phase 0 환경 구축 완료"
git tag -a phase-0-bootstrap -m "Phase 0: JSP + JSTL + MyBatis + PostgreSQL 배선 완료"
git push origin main --follow-tags
```

push 결과 확인:

```powershell
git status -sb
```

기대: `## main...origin/main` (ahead/behind 표시 없음)

**Phase 0 완료 기준 확인:**
- [ ] 브라우저에서 JSP가 뜨고 JSTL `c`/`fmt` 태그가 동작한다
- [ ] DB에서 읽은 값이 화면에 보인다
- [ ] `.\mvnw.cmd clean package` 로 `target/flowmate.war` 가 생성된다

---

## Phase 1 — 토대: 조직 · 사용자 (3.0일)

> **작업 순서가 설계서 §9와 다르다.** 설계서는 사원 목록(Day 2) → 공통 레이아웃(Day 4.5) 순서인데,
> 이 계획은 **공통 레이아웃(Task 9)을 사원 목록(Task 10)보다 먼저** 만든다.
> 설계서 §4.4.1이 직접 경고한 것("공통 조각을 늦게 만들면 이미 만든 화면 전부를 고쳐야 한다")을
> §9의 Day 순서가 어기고 있기 때문이다. 순서를 바꾸면 사원 목록 재작성 작업이 사라진다.
> (로드맵 Deviation D1)

- [ ] **Phase 1 작업 브랜치를 만든다**

```powershell
git switch -c feat/phase-1-org-user
```

---

### Task 6: 조직 스키마와 시드 데이터

**Files:**
- Create: `docker/postgres/init/10-schema-org.sql`
- Create: `docker/postgres/init/11-seed-org.sql`

- [ ] **Step 1: `10-schema-org.sql` 을 만든다**

설계서 §5.1 그대로. `COMMENT ON` 과 인덱스만 추가했다.

```sql
CREATE TABLE department (
    dept_id        BIGSERIAL    PRIMARY KEY,
    parent_dept_id BIGINT       REFERENCES department(dept_id),
    dept_name      VARCHAR(100) NOT NULL,
    dept_code      VARCHAR(20)  NOT NULL UNIQUE,
    sort_order     INT          NOT NULL DEFAULT 0,
    use_yn         CHAR(1)      NOT NULL DEFAULT 'Y'
);
COMMENT ON TABLE  department            IS '부서 (자기참조 계층 구조)';
COMMENT ON COLUMN department.sort_order IS '같은 부모 아래 형제 부서의 표시 순서';

CREATE TABLE position (
    position_id    BIGSERIAL   PRIMARY KEY,
    position_name  VARCHAR(50) NOT NULL,
    position_level INT         NOT NULL
);
COMMENT ON TABLE  position                IS '직급';
COMMENT ON COLUMN position.position_level IS '1(사원)~6(이사). 결재선 정책과 부서장 판정이 참조한다';

CREATE TABLE employee (
    emp_id        BIGSERIAL    PRIMARY KEY,
    emp_no        VARCHAR(20)  NOT NULL UNIQUE,
    emp_name      VARCHAR(50)  NOT NULL,
    dept_id       BIGINT       NOT NULL REFERENCES department(dept_id),
    position_id   BIGINT       NOT NULL REFERENCES position(position_id),
    email         VARCHAR(100),
    hire_date     DATE         NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    use_yn        CHAR(1)      NOT NULL DEFAULT 'Y'
);
COMMENT ON TABLE  employee               IS '사원 (로그인 주체)';
COMMENT ON COLUMN employee.emp_no        IS '사원번호. 로그인 아이디로 사용한다';
COMMENT ON COLUMN employee.password_hash IS 'BCrypt';
COMMENT ON COLUMN employee.role          IS 'USER / MANAGER / ADMIN';

CREATE INDEX idx_employee_dept ON employee(dept_id);
CREATE INDEX idx_employee_name ON employee(emp_name);
```

- [ ] **Step 2: `11-seed-org.sql` 을 만든다**

부서를 7개로 늘렸다(로드맵 D2). 3단 계층이 있어야 재귀 CTE의 `depth`가 의미를 갖고,
Phase 4의 "하위 부서까지 집계"를 시연할 수 있다.

```sql
-- 부서: 대표이사실 → 본부 2 → 팀 4 (3단 계층)
INSERT INTO department (dept_id, parent_dept_id, dept_name, dept_code, sort_order) VALUES
    (1, NULL, '대표이사실',   'CEO',      1),
    (2, 1,    '경영지원본부', 'HQ_MGMT',  1),
    (3, 1,    '사업본부',     'HQ_BIZ',   2),
    (4, 2,    '인사팀',       'TEAM_HR',  1),
    (5, 2,    '재무팀',       'TEAM_FIN', 2),
    (6, 3,    '마케팅팀',     'TEAM_MKT', 1),
    (7, 3,    '개발팀',       'TEAM_DEV', 2);

INSERT INTO position (position_id, position_name, position_level) VALUES
    (1, '사원', 1),
    (2, '대리', 2),
    (3, '과장', 3),
    (4, '차장', 4),
    (5, '부장', 5),
    (6, '이사', 6);

-- 사원 20명.
-- 비밀번호는 전원 'flowmate1!' 이다. pgcrypto 의 bf 방식은 $2a$ 형식을 만들고
-- Spring Security 의 BCryptPasswordEncoder 가 이를 그대로 검증한다.
-- ASCII 비밀번호만 쓴다 (pgcrypto bf 의 8bit 문자 처리 이슈 회피).
--
-- ★ 부서마다 position_level 최고값이 정확히 1명씩만 나오도록 배치했다.
--   Phase 2의 ApprovalLinePolicy 가 "같은 부서의 최고 직급"으로 부서장을 판정하기 때문이다.
--   이 배치를 바꾸면 결재선이 비결정적으로 바뀐다 (로드맵 Q2).
INSERT INTO employee (emp_id, emp_no, emp_name, dept_id, position_id, email, hire_date, password_hash, role) VALUES
    ( 1, '2015001', '정도현', 1, 6, 'dohyun.jeong@flowmate.co.kr',  '2015-03-02', crypt('flowmate1!', gen_salt('bf', 10)), 'ADMIN'),
    ( 2, '2016001', '김성일', 2, 5, 'sungil.kim@flowmate.co.kr',    '2016-01-04', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    ( 3, '2016002', '박현주', 3, 5, 'hyunju.park@flowmate.co.kr',   '2016-03-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    ( 4, '2017001', '최민석', 4, 4, 'minseok.choi@flowmate.co.kr',  '2017-02-01', crypt('flowmate1!', gen_salt('bf', 10)), 'ADMIN'),
    ( 5, '2018001', '한지우', 4, 2, 'jiwoo.han@flowmate.co.kr',     '2018-07-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    ( 6, '2020001', '서다인', 4, 1, 'dain.seo@flowmate.co.kr',      '2020-03-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    ( 7, '2017002', '오세훈', 5, 3, 'sehoon.oh@flowmate.co.kr',     '2017-09-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    ( 8, '2019001', '임채원', 5, 2, 'chaewon.lim@flowmate.co.kr',   '2019-04-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    ( 9, '2021001', '강태윤', 5, 1, 'taeyoon.kang@flowmate.co.kr',  '2021-01-04', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (10, '2016003', '윤서영', 6, 4, 'seoyoung.yoon@flowmate.co.kr', '2016-06-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    (11, '2018002', '이준호', 6, 2, 'junho.lee@flowmate.co.kr',     '2018-03-05', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (12, '2020002', '조미래', 6, 1, 'mirae.jo@flowmate.co.kr',      '2020-09-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (13, '2021002', '배성우', 6, 1, 'sungwoo.bae@flowmate.co.kr',   '2021-07-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (14, '2016004', '신동혁', 7, 3, 'donghyuk.shin@flowmate.co.kr', '2016-11-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    (15, '2017003', '문가영', 7, 2, 'gayoung.moon@flowmate.co.kr',  '2017-05-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (16, '2018003', '황준영', 7, 2, 'junyoung.hwang@flowmate.co.kr','2018-08-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (17, '2019002', '노은지', 7, 2, 'eunji.no@flowmate.co.kr',      '2019-10-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (18, '2020003', '곽수빈', 7, 1, 'subin.kwak@flowmate.co.kr',    '2020-03-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (19, '2021003', '유하람', 7, 1, 'haram.yu@flowmate.co.kr',      '2021-03-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (20, '2022001', '심재현', 7, 1, 'jaehyun.shim@flowmate.co.kr',  '2022-01-03', crypt('flowmate1!', gen_salt('bf', 10)), 'USER');

-- ★ BIGSERIAL 시퀀스를 시드 최대값으로 밀어 둔다.
--   PK 를 명시해서 INSERT 하면 시퀀스가 여전히 1 에 머물러 있어
--   다음 INSERT 가 dept_id=1 을 시도해 PK 충돌이 난다.
--   pg_get_serial_sequence() 를 쓰면 시퀀스 이름을 손으로 적지 않아도 된다.
SELECT setval(pg_get_serial_sequence('department', 'dept_id'),     (SELECT MAX(dept_id)     FROM department));
SELECT setval(pg_get_serial_sequence('position',   'position_id'), (SELECT MAX(position_id) FROM position));
SELECT setval(pg_get_serial_sequence('employee',   'emp_id'),      (SELECT MAX(emp_id)      FROM employee));
```

- [ ] **Step 3: 볼륨을 지우고 다시 올려 스키마와 시드를 적용한다**

```powershell
docker compose down -v
docker compose up -d postgres
```

- [ ] **Step 4: 적용 결과를 검증한다**

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "SELECT (SELECT COUNT(*) FROM department) AS depts, (SELECT COUNT(*) FROM position) AS positions, (SELECT COUNT(*) FROM employee) AS employees;"
```

기대: `depts=7`, `positions=6`, `employees=20`.

부서장 판정이 부서마다 유일한지 확인한다 (Phase 2가 이 전제에 의존한다):

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "SELECT e.dept_id, COUNT(*) AS top_level_count FROM employee e JOIN position p ON p.position_id = e.position_id WHERE p.position_level = (SELECT MAX(p2.position_level) FROM employee e2 JOIN position p2 ON p2.position_id = e2.position_id WHERE e2.dept_id = e.dept_id) GROUP BY e.dept_id ORDER BY e.dept_id;"
```

기대: 7개 행 전부 `top_level_count = 1`. (실측 확인: `1:1 2:1 3:1 4:1 5:1 6:1 7:1`)
2 이상인 부서가 있으면 시드의 `position_id` 를 조정한다.

- [ ] **Step 4b: 시퀀스가 밀렸는지, 다음 INSERT 가 충돌하지 않는지 확인한다**

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -c "BEGIN; INSERT INTO department (dept_name, dept_code) VALUES ('테스트팀','TEST_TMP') RETURNING dept_id; ROLLBACK;"
```

기대: 반환된 `dept_id` 가 `8`.

> **★ PostgreSQL 시퀀스는 트랜잭션에 묶이지 않는다.** 위 `ROLLBACK` 은 행만 되돌리고
> 이미 소비한 `nextval` 은 되돌리지 않는다. 그래서 이 검증을 한 번 돌리면
> `department_dept_id_seq.last_value` 가 `7` 이 아니라 `8` 로 남는다.
>
> **정상이며 고칠 필요가 없다.** 대리키에 갭이 생기는 것은 모든 시퀀스 기반 PK 의 정상 동작이고,
> `docker compose down -v` 로 컨테이너를 다시 만들면 `7` 로 돌아간다.
> 나중에 시퀀스 값이 `8` 인 것을 보고 시드가 잘못됐다고 오판하지 않기 위해 적어 둔다.

- [ ] **Step 4c: 한글이 깨지지 않고 저장됐는지 확인한다**

**콘솔로 바로 보면 안 된다** — 이 PC 의 콘솔은 CP949 이므로 정상 데이터도 깨져 보인다.
파일로 받아 UTF-8 로 명시 디코딩한다.

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -t -A -c "SELECT string_agg(dept_name, ', ' ORDER BY dept_id) FROM department;" > "$env:TEMP\d.txt"
[System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("$env:TEMP\d.txt")).Trim()
```

기대: `대표이사실, 경영지원본부, 사업본부, 인사팀, 재무팀, 마케팅팀, 개발팀`

- [ ] **Step 4d: 비밀번호 해시 형식을 확인한다**

> **`LIKE '\$2a\$10\$%'` 처럼 백슬래시로 이스케이프하면 PowerShell 에서 틀린 결과가 나온다.**
> PowerShell 은 백슬래시를 특별하게 처리하지 않으므로 리터럴 백슬래시가 SQL 패턴에 들어가
> 매칭이 0건이 된다. **PowerShell 에서는 백틱(`` ` ``)으로 `$` 를 이스케이프한다.**

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -t -A -c "SELECT 'total='||COUNT(*)||' bcrypt='||COUNT(*) FILTER (WHERE password_hash LIKE '`$2a`$10`$%')||' len60='||COUNT(*) FILTER (WHERE LENGTH(password_hash)=60)||' distinct='||COUNT(DISTINCT password_hash) FROM employee;"
```

기대: `total=20 bcrypt=20 len60=20 distinct=20`.

`distinct=20` 이 중요하다 — 20명이 같은 비밀번호를 쓰지만 `gen_salt` 가 매 행 다른 솔트를 만들므로
해시는 전부 달라야 한다. `distinct` 가 1이면 솔트가 고정된 것이고 그건 BCrypt 를 쓰는 의미가 없다.

> `position` 테이블 생성이 실패했다면 로드맵 Q3의 상황이다.
> 테이블명을 `job_position` 으로 바꾸고 로드맵 Q3 표를 갱신한다.

- [ ] **Step 5: 커밋한다**

```powershell
git add docker/postgres/init/
git commit -m "feat: 조직 스키마와 시드 20명 추가

부서를 설계서의 4개가 아니라 7개(3단 계층)로 둔다.
4개로는 재귀 CTE의 depth가 2단에서 끝나 조직도 계층 집계를 시연할 수 없다.

부서마다 최고 직급이 1명씩만 나오도록 배치했다.
Phase 2의 결재선 정책이 '같은 부서 최고 직급'으로 부서장을 판정하므로
동급이 둘이면 결재선이 비결정적으로 바뀐다."
```

---

### Task 7: 공통 페이징 객체 `Page<T>` (TDD)

DB도 화면도 필요 없는 순수 계산 로직이다. 여기서부터 테스트를 먼저 쓴다.

**Files:**
- Create: `src/test/java/com/flowmate/common/web/PageTest.java`
- Create: `src/main/java/com/flowmate/common/web/Page.java`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/flowmate/common/web/PageTest.java`:

```java
package com.flowmate.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    @DisplayName("결과가 0건이면 전체 페이지는 1이고 첫 페이지이면서 마지막 페이지다")
    void emptyResultHasOnePage() {
        Page<String> page = new Page<>(Collections.emptyList(), 1, 10, 0);

        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isTrue();
        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("20건을 10건씩 나누면 2페이지가 된다")
    void exactDivisionHasNoExtraPage() {
        Page<String> page = new Page<>(List.of("a"), 1, 10, 20);

        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isFalse();
    }

    @Test
    @DisplayName("21건을 10건씩 나누면 3페이지가 된다 (나머지가 한 페이지를 더 만든다)")
    void remainderCreatesOneMorePage() {
        Page<String> page = new Page<>(List.of("a"), 3, 10, 21);

        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    @DisplayName("첫 블록에서는 시작 페이지가 1이고 이전 블록이 없다")
    void firstBlockHasNoPrevious() {
        Page<String> page = new Page<>(List.of("a"), 1, 10, 250);

        assertThat(page.getStartPage()).isEqualTo(1);
        assertThat(page.getEndPage()).isEqualTo(10);
        assertThat(page.isHasPrevBlock()).isFalse();
        assertThat(page.isHasNextBlock()).isTrue();
        assertThat(page.getNextBlockPage()).isEqualTo(11);
    }

    @Test
    @DisplayName("11페이지는 두 번째 블록이므로 11~20을 보여주고 이전 블록은 10으로 간다")
    void secondBlockRange() {
        Page<String> page = new Page<>(List.of("a"), 11, 10, 250);

        assertThat(page.getStartPage()).isEqualTo(11);
        assertThat(page.getEndPage()).isEqualTo(20);
        assertThat(page.isHasPrevBlock()).isTrue();
        assertThat(page.getPrevBlockPage()).isEqualTo(10);
        assertThat(page.getNextBlockPage()).isEqualTo(21);
    }

    @Test
    @DisplayName("블록의 끝 페이지는 전체 페이지 수를 넘지 않는다")
    void endPageIsClampedToTotalPages() {
        Page<String> page = new Page<>(List.of("a"), 1, 10, 35);

        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getEndPage()).isEqualTo(4);
        assertThat(page.isHasNextBlock()).isFalse();
    }

    @Test
    @DisplayName("페이지 번호가 1보다 작으면 생성 시점에 거부한다")
    void rejectsPageBelowOne() {
        assertThatThrownBy(() -> new Page<>(List.of("a"), 0, 10, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    @DisplayName("totalPagesOf 는 Service 가 요청 페이지를 보정할 때 쓰는 것과 같은 값을 준다")
    void totalPagesOfMatchesInstanceMethod() {
        assertThat(Page.totalPagesOf(0, 10)).isEqualTo(1);
        assertThat(Page.totalPagesOf(20, 10)).isEqualTo(2);
        assertThat(Page.totalPagesOf(21, 10)).isEqualTo(3);
        assertThat(new Page<>(List.of("a"), 1, 10, 21).getTotalPages())
                .isEqualTo(Page.totalPagesOf(21, 10));
    }

    @Test
    @DisplayName("전체 페이지를 넘는 페이지를 넘기면 시작 페이지가 끝 페이지보다 커진다 - Service 가 미리 막아야 한다")
    void pageBeyondLastLeavesStartGreaterThanEnd() {
        // 11페이지를 보던 중 검색을 좁혀 totalCount 가 20으로 줄어든 상황.
        // Page 는 넘겨받은 값을 그대로 계산한다. <c:forEach begin=11 end=2> 는 예외 없이
        // 링크를 0개 그리므로 페이징이 조용히 죽는다.
        // 따라서 EmployeeService 가 Page 를 만들기 전에 page 를 totalPages 로 보정한다.
        Page<String> overshoot = new Page<>(List.of(), 11, 10, 20);

        assertThat(overshoot.getTotalPages()).isEqualTo(2);
        assertThat(overshoot.getStartPage()).isEqualTo(11);
        assertThat(overshoot.getEndPage()).isEqualTo(2);
        assertThat(overshoot.getStartPage()).isGreaterThan(overshoot.getEndPage());
    }
}
```

> **★ 이 마지막 테스트는 버그를 고정하는 것이 아니라 `Page` 의 책임 경계를 문서화한다.**
> `Page` 는 넘겨받은 값을 계산만 하고 검증하지 않는다(생성자 검증은 1 미만 같은 명백한 오류만).
> **범위를 넘는 페이지는 Task 11 의 `EmployeeService` 가 막는다** — 아래 Task 11 참조.

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd test -Dtest=PageTest
```

기대: 컴파일 실패 — `cannot find symbol: class Page`.

- [ ] **Step 3: `Page<T>` 를 구현한다**

`src/main/java/com/flowmate/common/web/Page.java`:

```java
package com.flowmate.common.web;

import java.util.List;

/**
 * 목록 화면의 페이징 상태를 담는다.
 * JSP 의 common/pagination.jsp 가 이 객체의 getter 만 보고 링크를 그린다.
 *
 * 페이지 번호는 1부터 시작한다. DB의 OFFSET 계산은 검색 조건 객체가 담당하고,
 * 이 클래스는 "몇 페이지짜리이고 어느 블록을 보여줄지"만 계산한다.
 */
public class Page<T> {

    /** 페이징 링크에 한 번에 보여주는 페이지 개수 */
    private static final int BLOCK_SIZE = 10;

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalCount;

    public Page(List<T> content, int page, int size, long totalCount) {
        if (content == null) {
            throw new IllegalArgumentException("content는 null일 수 없습니다");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page는 1 이상이어야 합니다: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount는 0 이상이어야 합니다: " + totalCount);
        }
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalCount = totalCount;
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * 전체 페이지 수 계산. Service 가 Page 를 만들기 전에 요청 페이지를 보정할 때도 필요하므로
     * static 으로 빼 둔다. 같은 식을 Service 에 복사하면 두 곳이 어긋날 수 있다.
     *
     * 결과가 0건이어도 1페이지로 본다. 화면에 "1 / 0" 같은 표시가 나오지 않게 한다.
     */
    public static int totalPagesOf(long totalCount, int size) {
        if (totalCount == 0) {
            return 1;
        }
        return (int) ((totalCount + size - 1) / size);
    }

    public int getTotalPages() {
        return totalPagesOf(totalCount, size);
    }

    public boolean isFirst() {
        return page <= 1;
    }

    public boolean isLast() {
        return page >= getTotalPages();
    }

    public int getStartPage() {
        return ((page - 1) / BLOCK_SIZE) * BLOCK_SIZE + 1;
    }

    public int getEndPage() {
        return Math.min(getStartPage() + BLOCK_SIZE - 1, getTotalPages());
    }

    public boolean isHasPrevBlock() {
        return getStartPage() > 1;
    }

    public boolean isHasNextBlock() {
        return getEndPage() < getTotalPages();
    }

    public int getPrevBlockPage() {
        return Math.max(getStartPage() - 1, 1);
    }

    public int getNextBlockPage() {
        return Math.min(getEndPage() + 1, getTotalPages());
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd test -Dtest=PageTest
```

기대: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 커밋한다**

```powershell
git add src/main/java/com/flowmate/common/web/Page.java src/test/java/com/flowmate/common/web/PageTest.java
git commit -m "feat: 공통 페이징 객체 Page 추가

페이지 블록 계산을 JSP나 Controller가 아니라 이 객체 하나에 모은다.
DB 없이 도는 순수 로직이므로 경계값(0건, 나머지 있는 나눗셈, 블록 끝 clamp)을
전부 단위 테스트로 고정한다."
```

---

### Task 8: 검색 조건 객체 `EmployeeSearchCond` (TDD)

Controller가 받은 요청 파라미터를 SQL이 안전하게 쓸 수 있는 값으로 정규화한다.
페이지 번호 clamp, 빈 검색어의 null 변환이 여기서 끝나야 SQL의 `<if>` 가 단순해진다.

**Files:**
- Create: `src/test/java/com/flowmate/org/domain/EmployeeSearchCondTest.java`
- Create: `src/main/java/com/flowmate/org/domain/EmployeeSearchCond.java`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.flowmate.org.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmployeeSearchCondTest {

    @Test
    @DisplayName("아무것도 설정하지 않으면 1페이지 10건, offset 0이다")
    void defaultsToFirstPage() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        assertThat(cond.getPage()).isEqualTo(1);
        assertThat(cond.getSize()).isEqualTo(10);
        assertThat(cond.getOffset()).isEqualTo(0);
        assertThat(cond.getLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("페이지 번호가 0이나 음수로 들어오면 1로 보정한다")
    void clampsPageToAtLeastOne() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setPage(0);
        assertThat(cond.getPage()).isEqualTo(1);

        cond.setPage(-5);
        assertThat(cond.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("size가 상한을 넘으면 100으로 자르고 0 이하면 기본값 10으로 되돌린다")
    void clampsSizeToAllowedRange() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setSize(1000);
        assertThat(cond.getSize()).isEqualTo(100);

        cond.setSize(0);
        assertThat(cond.getSize()).isEqualTo(10);

        cond.setSize(30);
        assertThat(cond.getSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 제거하고, 공백만 있으면 null로 만든다")
    void normalizesKeyword() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setKeyword("  곽수빈  ");
        assertThat(cond.getKeyword()).isEqualTo("곽수빈");

        cond.setKeyword("   ");
        assertThat(cond.getKeyword()).isNull();

        cond.setKeyword("");
        assertThat(cond.getKeyword()).isNull();

        cond.setKeyword(null);
        assertThat(cond.getKeyword()).isNull();
    }

    @Test
    @DisplayName("offset은 (페이지 - 1) * size 다")
    void offsetFollowsPageAndSize() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setPage(3);
        cond.setSize(10);

        assertThat(cond.getOffset()).isEqualTo(20);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd test -Dtest=EmployeeSearchCondTest
```

기대: 컴파일 실패 — `cannot find symbol: class EmployeeSearchCond`.

- [ ] **Step 3: `EmployeeSearchCond` 를 구현한다**

```java
package com.flowmate.org.domain;

/**
 * 사원 목록 검색 조건. Spring MVC 가 요청 파라미터를 이 객체의 setter 로 바인딩하고,
 * MyBatis 가 getter 로 #{keyword} · #{limit} · #{offset} 을 읽는다.
 *
 * 값 보정을 setter 에서 끝내는 이유:
 * 잘못된 page=0 이나 size=100000 이 SQL 까지 흘러가지 않게 막는 곳을 한 군데로 모은다.
 *
 * 참고: select 의 "전체" 옵션은 value="" 로 보내는데, Spring 의 String→Long 변환기가
 * 빈 문자열을 null 로 바꿔주므로 deptId 에 별도 처리가 필요하지 않다.
 */
public class EmployeeSearchCond {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private int page = 1;
    private int size = DEFAULT_SIZE;
    private String keyword;
    private Long deptId;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size < 1) {
            this.size = DEFAULT_SIZE;
            return;
        }
        this.size = Math.min(size, MAX_SIZE);
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            this.keyword = null;
            return;
        }
        this.keyword = keyword.trim();
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    /** SQL 의 LIMIT 값 */
    public int getLimit() {
        return size;
    }

    /**
     * SQL 의 OFFSET 값.
     *
     * long 으로 계산하는 이유: page 는 위쪽 상한이 없다(요청 파라미터를 손으로 고치면
     * 얼마든 커진다). int 로 곱하면 Java 는 예외 없이 음수로 감싸고, 그 값이
     * OFFSET 으로 들어가도 오류가 나지 않아 조용히 빈 결과가 된다.
     * 오버플로 방지를 이 객체가 책임진다 — 호출하는 Service 의 순서에 의존하지 않는다.
     */
    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
```

> **캐스트 위치가 중요하다.** `(long) (page - 1) * size` 는 곱하기 전에 승격한다.
> `(long) ((page - 1) * size)` 로 쓰면 int 로 먼저 오버플로한 뒤 이미 틀린 값을 넓히므로 의미가 없다.
>
> `?page=300000000` 이면 `(300000000-1)*10 = 2999999990` 인데 `Integer.MAX_VALUE` 는 `2147483647` 이다.
> int 로는 `-1294967306` 으로 감싸고, **음수 OFFSET 은 SQL 오류가 아니라 조용한 빈 결과**가 된다.

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd test
```

기대: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` (Page 9건 + SearchCond 6건)

- [ ] **Step 5: 커밋한다**

```powershell
git add src/main/java/com/flowmate/org/domain src/test/java/com/flowmate/org/domain
git commit -m "feat: 사원 검색 조건 객체 추가

page/size 보정과 빈 검색어의 null 변환을 setter 에서 끝낸다.
잘못된 값이 SQL 까지 흘러가는 경로를 한 군데로 모으고,
결과적으로 매퍼 XML 의 <if> 조건이 null 검사 하나로 단순해진다."
```

---

### Task 9: 공통 레이아웃 조각 5종과 클래스 명명 규칙 고정 ★

> 설계서 §4.4.1의 결론을 실행하는 Task다. **이 Task가 끝나기 전에는 어떤 업무 화면도 만들지 않는다.**
> 여기서 만든 5개 파일이 이후 모든 화면의 원본이 된다.
>
> `header.jsp` 는 `${loginEmployee}` 를 참조하지만 그 타입은 Task 11에서 만든다.
> JSP EL 은 런타임에 해석되고 `<c:if test="${not empty loginEmployee}">` 로 감싸 두므로,
> Security 가 없는 지금은 블록이 실행되지 않고 **Task 11 이후에 자동으로 표시된다.**
> Task 11에서 `header.jsp` 를 다시 열 필요가 없다.

**Files:**
- Create: `src/main/webapp/static/js/jquery-3.7.1.min.js`
- Create: `src/main/webapp/static/js/common.js`
- Create: `src/main/webapp/static/css/style.css`
- Create: `src/main/java/com/flowmate/config/WebMvcConfig.java`
- Create: `src/main/webapp/WEB-INF/views/common/head.jsp`
- Create: `src/main/webapp/WEB-INF/views/common/header.jsp`
- Create: `src/main/webapp/WEB-INF/views/common/sidebar.jsp`
- Create: `src/main/webapp/WEB-INF/views/common/footer.jsp`
- Create: `src/main/webapp/WEB-INF/views/common/pagination.jsp`
- Modify: `src/main/webapp/WEB-INF/views/home.jsp`

- [ ] **Step 1: jQuery 를 로컬에 내려받는다**

CDN을 쓰지 않는다. 사내 그룹웨어는 외부망이 막힌 환경에서 동작해야 한다.

```powershell
New-Item -ItemType Directory -Force src\main\webapp\static\js
New-Item -ItemType Directory -Force src\main\webapp\static\css
curl.exe -L -o src\main\webapp\static\js\jquery-3.7.1.min.js https://code.jquery.com/jquery-3.7.1.min.js
```

확인:

```powershell
(Get-Item src\main\webapp\static\js\jquery-3.7.1.min.js).Length
```

기대: 약 87000 바이트. 수백 바이트라면 오류 페이지를 받은 것이므로 다시 내려받는다.

- [ ] **Step 2: `WebMvcConfig` 로 정적 자원 경로를 노출한다**

```java
package com.flowmate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * src/main/webapp/static/** 를 /static/** 로 노출한다.
     *
     * addResourceLocations 의 "/static/" 는 classpath 가 아니라
     * ServletContext(웹 애플리케이션 루트) 기준 경로다. classpath: 접두사를 붙이면
     * src/main/resources/static 을 찾아 404가 된다.
     *
     * 설계서 §4.4.2 가 CSS 위치를 webapp/static 으로 정했으므로 이 배선이 필요하다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("/static/");
    }
}
```

- [ ] **Step 3: `common.js` 를 만든다**

```javascript
/*
 * FlowMate 공용 스크립트.
 * 화면별 스크립트는 각 JSP 하단에 두고, 여기에는 모든 화면에 적용되는 것만 둔다.
 */
$(function () {

    /*
     * CSRF: Spring Security 는 POST/PUT/DELETE 에 토큰을 요구한다.
     * <form> 은 각 JSP 가 hidden input 으로 직접 넣지만, AJAX 는 헤더로 보내야 한다.
     * head.jsp 의 meta 태그에서 값을 읽어 모든 AJAX 요청에 자동으로 붙인다.
     * (Phase 5 의 AI 호출이 전부 AJAX 이므로 여기서 한 번 배선해 둔다.)
     */
    var csrfToken = $('meta[name="_csrf"]').attr('content');
    var csrfHeader = $('meta[name="_csrf_header"]').attr('content');
    if (csrfToken && csrfHeader) {
        $.ajaxSetup({
            beforeSend: function (xhr) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            }
        });
    }

    /*
     * 페이징: common/pagination.jsp 가 그린 링크를 가로채
     * 같은 화면의 #searchForm 을 다시 submit 한다.
     *
     * 링크에 검색 조건을 직접 붙이지 않는 이유:
     * 조건이 늘어날 때마다 URL 조립과 인코딩을 손봐야 하고, 그 작업이 화면마다 반복된다.
     * 폼을 다시 보내면 조건이 몇 개든 pagination.jsp 를 고치지 않는다.
     */
    $(document).on('click', '.pagination__link[data-page]', function (event) {
        event.preventDefault();
        var targetPage = $(this).data('page');
        var $form = $('#searchForm');
        if ($form.length === 0) {
            return;
        }
        $form.find('input[name="page"]').val(targetPage);
        $form.trigger('submit');
    });
});
```

- [ ] **Step 4: `style.css` 를 만든다 — 내용은 비우고 클래스 목록만 유지한다**

설계서 §4.4의 원칙("구조는 처음에, 외양은 마지막에")을 실행 가능하게 만드는 장치다.
**각 Phase 종료 시 새로 만든 클래스명을 이 목록에 추가한다.** Phase 6은 이 목록을 위에서 아래로 채우는 작업이 된다.

```css
/*
 * FlowMate 전체 스타일 — 단일 파일로 유지한다 (설계서 §4.4.2).
 * UI 프레임워크는 쓰지 않는다.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │ 이 파일은 Phase 6 에서 채운다.                               │
 * │ Phase 1~5 에서는 아래 "클래스 목록"만 갱신한다.               │
 * │                                                             │
 * │ 규칙 (설계서 §4.4.2):                                        │
 * │   영역은 명사        .doc-list  .ai-panel  .approval-line    │
 * │   상태는 -- 접미     .status--pending  .status--rejected      │
 * │   버튼은 .btn + 역할 .btn--primary  .btn--danger              │
 * │   폼은 고정 3종      .form-row  .form-label  .form-input      │
 * │   시각 기반 금지     .blue-box .big-text .red 를 쓰지 않는다  │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ── 클래스 목록 (Phase 1) ────────────────────────────────────────
 * 레이아웃   .layout  .content  .page-title
 * 상단       .gnb  .gnb__brand  .gnb__user  .gnb__user-name  .gnb__user-org  .gnb__logout
 * 좌측       .lnb  .lnb__group  .lnb__group-title  .lnb__link  .lnb__link--active
 * 하단       .footer  .footer__name  .footer__desc
 * 폼         .form-row  .form-label  .form-input  .search-form
 * 버튼       .btn  .btn--primary  .btn--plain
 * 알림       .alert  .alert--error  .alert--info
 * 페이징     .pagination  .pagination__link  .pagination__link--current
 *            .pagination__link--prev  .pagination__link--next
 * 로그인     .login-page  .login-box  .login-box__title  .login-box__subtitle
 * 사원목록   .emp-list  .emp-list__empty  .result-count
 * 조직도     .dept-tree  .dept-tree__item  .dept-tree__item--depth1 ~ --depth5
 *            .dept-tree__code  .dept-tree__name  .dept-tree__count
 * 홈         .home-panel  .home-panel__title  .home-panel__item
 *
 * ── 클래스 목록 (Phase 2) ────────────────────────────────────────
 * (전자결재 화면 작업 시 추가)
 */
```

- [ ] **Step 5: `common/head.jsp` 를 만든다**

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  <head> 전체를 담당한다. 각 화면은 아래처럼 제목을 넘긴다.
    <jsp:include page="../common/head.jsp">
        <jsp:param name="pageTitle" value="사원 목록"/>
    </jsp:include>

  _csrf meta 두 개는 static/js/common.js 가 AJAX 헤더로 쓴다.
  Security 배선 전에는 값이 비어 있고, 배선 후 자동으로 채워진다.
--%>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="_csrf" content="${_csrf.token}">
    <meta name="_csrf_header" content="${_csrf.headerName}">
    <title><c:out value="${param.pageTitle}"/> · FlowMate</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/style.css">
    <script src="${pageContext.request.contextPath}/static/js/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/static/js/common.js"></script>
</head>
```

- [ ] **Step 6: `common/header.jsp` 를 만든다**

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  loginEmployee 는 LoginEmployeeAdvice(Task 11)가 모든 화면 모델에 넣어준다.
  Security 배선 전에는 비어 있어 사용자 영역이 렌더링되지 않는다.
  이 파일을 Task 11 에서 다시 열지 않기 위해 미리 완성해 둔다.
--%>
<header class="gnb">
    <a class="gnb__brand" href="${pageContext.request.contextPath}/">FlowMate</a>
    <c:if test="${not empty loginEmployee}">
        <div class="gnb__user">
            <span class="gnb__user-name"><c:out value="${loginEmployee.empName}"/></span>
            <span class="gnb__user-org">
                <c:out value="${loginEmployee.deptName}"/> · <c:out value="${loginEmployee.positionName}"/>
            </span>
            <form class="gnb__logout" method="post" action="${pageContext.request.contextPath}/logout">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <button class="btn btn--plain" type="submit">로그아웃</button>
            </form>
        </div>
    </c:if>
</header>
```

- [ ] **Step 7: `common/sidebar.jsp` 를 만든다**

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<nav class="lnb">
    <ul class="lnb__group">
        <li class="lnb__group-title">조직</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/employees">사원 목록</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/dept-tree">조직도</a></li>
    </ul>
    <%--
      Phase 2 에서 '전자결재' 그룹(기안 작성 · 내 결재함)을,
      Phase 4 에서 '근태관리' 그룹(출퇴근 · 부서 현황)을 여기에 추가한다.
      존재하지 않는 화면 링크를 미리 두지 않는다 — 404 가 데모에서 그대로 보인다.
    --%>
</nav>
```

- [ ] **Step 8: `common/footer.jsp` 를 만든다**

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<footer class="footer">
    <span class="footer__name">FlowMate</span>
    <span class="footer__desc">AI 사전점검 그룹웨어 — 전자결재 · 근태관리</span>
</footer>
```

- [ ] **Step 9: `common/pagination.jsp` 를 만든다**

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  공통 페이징 조각. 사용 조건 두 가지:

    1) request 스코프에 com.flowmate.common.web.Page 객체가 "paging" 이름으로 있어야 한다.
    2) 같은 화면에 <form id="searchForm"> 이 있고 그 안에 <input type="hidden" name="page"> 가 있어야 한다.

  링크 클릭은 static/js/common.js 가 가로채 searchForm 을 다시 submit 한다.
  그래서 이 파일은 검색 조건이 몇 개로 늘어나도 고치지 않는다.

  모델 이름을 "page" 가 아니라 "paging" 으로 쓰는 이유:
  page 는 JSP 스크립팅 암묵 객체 이름과 겹쳐 혼동을 부른다.
--%>
<c:if test="${paging.totalPages > 1}">
    <nav class="pagination">
        <c:if test="${paging.hasPrevBlock}">
            <a class="pagination__link pagination__link--prev" href="#" data-page="${paging.prevBlockPage}">이전</a>
        </c:if>

        <c:forEach begin="${paging.startPage}" end="${paging.endPage}" var="i">
            <c:choose>
                <c:when test="${i eq paging.page}">
                    <strong class="pagination__link pagination__link--current">${i}</strong>
                </c:when>
                <c:otherwise>
                    <a class="pagination__link" href="#" data-page="${i}">${i}</a>
                </c:otherwise>
            </c:choose>
        </c:forEach>

        <c:if test="${paging.hasNextBlock}">
            <a class="pagination__link pagination__link--next" href="#" data-page="${paging.nextBlockPage}">다음</a>
        </c:if>
    </nav>
</c:if>
```

- [ ] **Step 10: `home.jsp` 를 공통 조각 구조로 다시 쓴다**

이게 이후 모든 화면이 복사할 골격이다.

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="common/head.jsp">
    <jsp:param name="pageTitle" value="홈"/>
</jsp:include>
<body>
<jsp:include page="common/header.jsp"/>
<div class="layout">
    <jsp:include page="common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">홈</h2>

        <section class="home-panel">
            <h3 class="home-panel__title">시스템 상태</h3>
            <p class="home-panel__item">
                서버 시각 <fmt:formatDate value="${serverTime}" pattern="yyyy-MM-dd HH:mm:ss"/>
            </p>
            <p class="home-panel__item">DB <c:out value="${dbInfo}"/></p>
        </section>

        <section class="home-panel">
            <h3 class="home-panel__title">모듈</h3>
            <c:forEach items="${modules}" var="m">
                <p class="home-panel__item"><c:out value="${m}"/></p>
            </c:forEach>
        </section>
    </main>
</div>
<jsp:include page="common/footer.jsp"/>
</body>
</html>
```

- [ ] **Step 11: 정적 자원이 실제로 서비스되는지 확인한다**

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

다른 터미널에서:

```powershell
curl.exe -s -o NUL -w "css=%{http_code}`n" http://localhost:8080/static/css/style.css
curl.exe -s -o NUL -w "jquery=%{http_code}`n" http://localhost:8080/static/js/jquery-3.7.1.min.js
curl.exe -s -o NUL -w "common=%{http_code}`n" http://localhost:8080/static/js/common.js
```

기대: 세 줄 모두 `200`.
404가 나오면 `WebMvcConfig` 의 `addResourceLocations` 에 `classpath:` 가 붙어 있는지 확인한다.

브라우저에서 `http://localhost:8080/` 접속 후 개발자도구 콘솔에 JS 오류가 없는지 확인한다.
`$ is not defined` 가 보이면 jQuery 파일이 제대로 내려오지 않은 것이다.

- [ ] **Step 12: 커밋한다**

```powershell
git add src/main/java/com/flowmate/config/WebMvcConfig.java src/main/webapp
git commit -m "feat: 공통 레이아웃 조각 5종과 클래스 명명 규칙 고정

설계서 §4.4.1 에 따라 업무 화면보다 먼저 만든다.
JSP 는 마크업과 데이터 바인딩이 한 파일에 있고 jQuery 가 DOM 구조에 직접 의존하므로
공통 조각을 나중에 만들면 그때까지의 화면 전부를 고쳐야 한다.

style.css 는 비워 두고 클래스 목록 주석만 유지한다.
Phase 6 의 CSS 작업이 '화면을 뒤져 클래스를 수집하는 일'이 아니라
'목록을 위에서 아래로 채우는 일'이 되게 하기 위한 장치다.

pagination.jsp 는 검색 조건을 URL 로 조립하지 않고 #searchForm 재전송 방식을 쓴다.
조건이 늘어도 이 파일을 고치지 않는다."
```

---

### Task 10: 조직도 트리 — 재귀 CTE

> 사원 목록보다 먼저 만든다. 사원 목록의 부서 선택 상자가 이 조회 결과를 그대로 쓰기 때문에,
> 순서를 반대로 하면 부서 조회 쿼리를 두 번 만들게 된다.
>
> **중첩 `<ul>` 이 아니라 플랫 리스트 + `--depth{n}` 클래스로 렌더링한다** (로드맵 D4).
> JSP 의 `<jsp:include>` 재귀는 `<c:set scope="request">` 로 변수를 덮어써 부모 루프가 조용히 깨진다.
> 들여쓰기는 CSS 의 일이고, 이 방식이 설계서 §4.4.2 와 정확히 맞는다.

**Files:**
- Create: `src/main/java/com/flowmate/org/domain/DeptTreeItem.java`
- Create: `src/main/java/com/flowmate/org/mapper/DepartmentMapper.java`
- Create: `src/main/resources/mapper/org/DepartmentMapper.xml`
- Create: `src/main/java/com/flowmate/org/service/DepartmentService.java`
- Create: `src/main/java/com/flowmate/org/controller/DepartmentController.java`
- Create: `src/main/webapp/WEB-INF/views/org/dept-tree.jsp`
- Test: `src/test/java/com/flowmate/org/mapper/DepartmentMapperIT.java`
- Create: `docs/oracle-mapping.md`

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`*IT.java` 이므로 `mvnw.cmd test` 가 아니라 `mvnw.cmd verify` 로 돈다. Docker PostgreSQL 기동이 전제다.

```java
package com.flowmate.org.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.DeptTreeItem;

/**
 * 재귀 CTE 검증. 시드 데이터(부서 7개, 3단 계층)를 전제로 한다.
 * 쓰기가 있는 테스트는 @Transactional 로 롤백된다.
 */
@SpringBootTest
@Transactional
class DepartmentMapperIT {

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("조직도는 깊이 우선으로, 형제는 sort_order 순으로 정렬되어 나온다")
    void returnsDepthFirstOrderedBySortOrder() {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(tree).extracting(DeptTreeItem::getDeptName)
                .containsExactly("대표이사실", "경영지원본부", "인사팀", "재무팀",
                                 "사업본부", "마케팅팀", "개발팀");
    }

    @Test
    @DisplayName("루트는 depth 1, 본부는 2, 팀은 3이다")
    void assignsDepthByLevel() {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(findByName(tree, "대표이사실").getDepth()).isEqualTo(1);
        assertThat(findByName(tree, "경영지원본부").getDepth()).isEqualTo(2);
        assertThat(findByName(tree, "인사팀").getDepth()).isEqualTo(3);
    }

    @Test
    @DisplayName("부서별 사원 수를 함께 반환한다")
    void includesEmployeeCountPerDepartment() {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(findByName(tree, "개발팀").getEmpCount()).isEqualTo(7);
        assertThat(findByName(tree, "대표이사실").getEmpCount()).isEqualTo(1);
        // 본부에는 소속 사원이 1명씩만 있고 팀 인원은 합산하지 않는다
        assertThat(findByName(tree, "경영지원본부").getEmpCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용하지 않는 부서는 그 하위까지 조직도에서 빠진다")
    void excludesUnusedDepartmentAndItsDescendants() {
        jdbcTemplate.update("UPDATE department SET use_yn = 'N' WHERE dept_code = 'HQ_MGMT'");

        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(tree).extracting(DeptTreeItem::getDeptName)
                .doesNotContain("경영지원본부", "인사팀", "재무팀")
                .contains("사업본부", "개발팀");
    }

    private DeptTreeItem findByName(List<DeptTreeItem> tree, String name) {
        return tree.stream()
                .filter(item -> name.equals(item.getDeptName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("부서를 찾지 못했습니다: " + name));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
docker compose up -d postgres
.\mvnw.cmd verify -Dit.test=DepartmentMapperIT
```

기대: 컴파일 실패 — `cannot find symbol: class DepartmentMapper`.

- [ ] **Step 3: `DeptTreeItem` 을 만든다**

```java
package com.flowmate.org.domain;

/**
 * 조직도 조회 결과 한 행. 재귀 CTE 가 계산한 depth 를 그대로 담는다.
 *
 * 중첩 구조(children)를 만들지 않는 이유:
 * 화면이 depth 값을 CSS 클래스(dept-tree__item--depth3)로 바꿔 들여쓰기를 표현하므로
 * Java 쪽에서 트리를 조립할 필요가 없다.
 */
public class DeptTreeItem {

    private Long deptId;
    private Long parentDeptId;
    private String deptName;
    private String deptCode;
    private int depth;
    private int empCount;

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getParentDeptId() {
        return parentDeptId;
    }

    public void setParentDeptId(Long parentDeptId) {
        this.parentDeptId = parentDeptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getEmpCount() {
        return empCount;
    }

    public void setEmpCount(int empCount) {
        this.empCount = empCount;
    }
}
```

- [ ] **Step 4: `DepartmentMapper` 인터페이스를 만든다**

```java
package com.flowmate.org.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.flowmate.org.domain.DeptTreeItem;

@Mapper
public interface DepartmentMapper {

    /** 사용 중인 부서 전체를 계층 순서(깊이 우선, 형제는 sort_order 순)로 반환한다 */
    List<DeptTreeItem> findDeptTree();
}
```

- [ ] **Step 5: `DepartmentMapper.xml` 을 만든다**

`src/main/resources/mapper/org/DepartmentMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.flowmate.org.mapper.DepartmentMapper">

    <!--
      조직도 계층 조회 (재귀 CTE).

      설계서 §6.1 의 원본은 path 를 dept_id 로만 만들어 형제 정렬이 id 순이 된다.
      department.sort_order 를 NOT NULL 로 선언한 이유가 형제 정렬이므로
      sort_order 를 앞에 두고 dept_id 를 tie-breaker 로 붙인다.

      LPAD 로 자릿수를 맞추는 이유: 문자열 비교이므로 '10' < '9' 가 되는 것을 막는다.

      Oracle 대응은 docs/oracle-mapping.md 참조 (WITH RECURSIVE → CONNECT BY PRIOR).
    -->
    <select id="findDeptTree" resultType="DeptTreeItem">
        WITH RECURSIVE dept_tree AS (
            SELECT d.dept_id,
                   d.parent_dept_id,
                   d.dept_name,
                   d.dept_code,
                   1 AS depth,
                   LPAD(CAST(d.sort_order AS VARCHAR(5)), 5, '0') || '-' ||
                   LPAD(CAST(d.dept_id    AS VARCHAR(10)), 10, '0') AS sort_path
              FROM department d
             WHERE d.parent_dept_id IS NULL
               AND d.use_yn = 'Y'
            UNION ALL
            SELECT c.dept_id,
                   c.parent_dept_id,
                   c.dept_name,
                   c.dept_code,
                   t.depth + 1,
                   t.sort_path || '>' ||
                   LPAD(CAST(c.sort_order AS VARCHAR(5)), 5, '0') || '-' ||
                   LPAD(CAST(c.dept_id    AS VARCHAR(10)), 10, '0')
              FROM department c
              JOIN dept_tree t ON c.parent_dept_id = t.dept_id
             WHERE c.use_yn = 'Y'
        )
        SELECT t.dept_id,
               t.parent_dept_id,
               t.dept_name,
               t.dept_code,
               t.depth,
               (SELECT COUNT(*)
                  FROM employee e
                 WHERE e.dept_id = t.dept_id
                   AND e.use_yn = 'Y') AS emp_count
          FROM dept_tree t
         ORDER BY t.sort_path
    </select>

</mapper>
```

- [ ] **Step 6: `DepartmentService` 를 만든다**

```java
package com.flowmate.org.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.DeptTreeItem;
import com.flowmate.org.mapper.DepartmentMapper;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    /**
     * 조직도 화면과 사원 목록의 부서 선택 상자가 같은 결과를 쓴다.
     * 부서 수가 수십 개 수준이라 전체를 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public List<DeptTreeItem> findDeptTree() {
        return departmentMapper.findDeptTree();
    }
}
```

- [ ] **Step 7: 통합 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd verify -Dit.test=DepartmentMapperIT
```

기대: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

실패 시 진단:

| 증상 | 원인 |
|---|---|
| 모든 필드가 null | `map-underscore-to-camel-case` 미설정 |
| `Invalid bound statement` | XML의 `namespace` 가 인터페이스 FQCN과 불일치, 또는 `mapper-locations` 경로 오류 |
| 순서가 다름 | `LPAD`/`CAST` 누락으로 문자열 비교가 자릿수를 안 맞춤 |
| `relation "department" does not exist` | Task 6의 `docker compose down -v` 를 하지 않았다 |

- [ ] **Step 8: `DepartmentController` 와 `dept-tree.jsp` 를 만든다**

```java
package com.flowmate.org.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.org.service.DepartmentService;

@Controller
@RequestMapping("/org")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/dept-tree")
    public String deptTree(Model model) {
        model.addAttribute("deptTree", departmentService.findDeptTree());
        return "org/dept-tree";
    }
}
```

`src/main/webapp/WEB-INF/views/org/dept-tree.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="조직도"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">조직도</h2>

        <%--
          들여쓰기는 depth 클래스로만 표현하고 CSS(Phase 6)가 여백을 넣는다.
          중첩 <ul> 을 만들지 않는 이유는 로드맵 D4 참조.
        --%>
        <ul class="dept-tree">
            <c:forEach items="${deptTree}" var="node">
                <li class="dept-tree__item dept-tree__item--depth${node.depth}">
                    <span class="dept-tree__code"><c:out value="${node.deptCode}"/></span>
                    <span class="dept-tree__name"><c:out value="${node.deptName}"/></span>
                    <span class="dept-tree__count">${node.empCount}명</span>
                </li>
            </c:forEach>
        </ul>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
```

- [ ] **Step 9: 화면을 확인한다**

```powershell
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080/org/dept-tree` 접속.

기대: 7개 부서가 `대표이사실 → 경영지원본부 → 인사팀 → 재무팀 → 사업본부 → 마케팅팀 → 개발팀` 순서로,
각 줄에 부서코드·부서명·사원 수가 보인다. (CSS 가 없으므로 들여쓰기는 아직 없다 — 정상이다.)

- [ ] **Step 10: `docs/oracle-mapping.md` 를 만든다**

설계서 §5.6은 이 문서를 Phase 6에 쓰라고 했지만, **여기서 만들고 PostgreSQL 전용 문법을 쓸 때마다 그 자리에서 추가한다** (로드맵 D8).
나중에 몰아 쓰면 "어디서 무엇을 썼는지" 재조사가 필요하고, 그 재조사는 0.5일에 끝나지 않는다.

```markdown
# PostgreSQL → Oracle 문법 대응표

FlowMate 는 PostgreSQL 16 으로 개발하지만, 목표 환경(그룹웨어 커스터마이징)은 Oracle 이 표준이다.
**이 문서는 사후에 작성하지 않는다.** PostgreSQL 전용 문법을 쓸 때마다 그 자리에서 한 줄 추가한다.

## 1. 공통 대응 (설계서 §5.6)

| PostgreSQL | Oracle |
|---|---|
| `BIGSERIAL` | `NUMBER` + `SEQUENCE.NEXTVAL` |
| `WITH RECURSIVE` | `CONNECT BY PRIOR` 또는 Oracle 11gR2+ 의 `WITH ... ` 재귀 |
| `LIMIT n OFFSET m` | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` (12c+) / `ROWNUM` 서브쿼리 (11g 이하) |
| `COALESCE` | `NVL` (`COALESCE` 도 동작한다) |
| `CURRENT_TIMESTAMP` | `SYSTIMESTAMP` |
| `TEXT` | `CLOB` |
| `||` 문자열 결합 | 동일 |
| `CAST(x AS VARCHAR(n))` | `TO_CHAR(x)` (`CAST` 도 동작하지만 `VARCHAR2` 를 쓴다) |
| `VARCHAR(n)` | `VARCHAR2(n)` |
| `CHAR(1)` | 동일 |
| `NUMERIC(p,s)` | `NUMBER(p,s)` |
| `pgcrypto` 의 `crypt()` / `gen_salt()` | 대응 없음. 시드 해시를 애플리케이션에서 생성해 INSERT 문에 박는다 |

## 2. 실제 사용 위치별 대응

### 2.1 조직도 계층 조회 — `mapper/org/DepartmentMapper.xml#findDeptTree`

PostgreSQL:

```sql
WITH RECURSIVE dept_tree AS (
    SELECT d.dept_id, d.parent_dept_id, d.dept_name, d.dept_code, 1 AS depth,
           LPAD(CAST(d.sort_order AS VARCHAR(5)), 5, '0') || '-' ||
           LPAD(CAST(d.dept_id AS VARCHAR(10)), 10, '0') AS sort_path
      FROM department d
     WHERE d.parent_dept_id IS NULL AND d.use_yn = 'Y'
    UNION ALL
    SELECT c.dept_id, c.parent_dept_id, c.dept_name, c.dept_code, t.depth + 1,
           t.sort_path || '>' || LPAD(...) || '-' || LPAD(...)
      FROM department c JOIN dept_tree t ON c.parent_dept_id = t.dept_id
     WHERE c.use_yn = 'Y'
)
SELECT ... FROM dept_tree t ORDER BY t.sort_path
```

Oracle:

```sql
SELECT d.dept_id, d.parent_dept_id, d.dept_name, d.dept_code,
       LEVEL AS depth,
       SYS_CONNECT_BY_PATH(LPAD(TO_CHAR(d.sort_order), 5, '0') || '-' ||
                           LPAD(TO_CHAR(d.dept_id), 10, '0'), '>') AS sort_path
  FROM department d
 WHERE d.use_yn = 'Y'
 START WITH d.parent_dept_id IS NULL
CONNECT BY PRIOR d.dept_id = d.parent_dept_id
 ORDER SIBLINGS BY d.sort_order, d.dept_id
```

**차이가 나는 지점:**
- `depth` → Oracle 은 의사열 `LEVEL`
- `sort_path` → `SYS_CONNECT_BY_PATH`
- Oracle 은 `ORDER SIBLINGS BY` 로 형제 정렬을 직접 지원한다. **LPAD 문자열 조립이 필요 없다.**
- `use_yn = 'Y'` 를 `WHERE` 에 두면 Oracle 은 해당 노드만 빼고 하위는 남는다.
  PostgreSQL CTE 와 같은 "하위까지 제외" 동작을 원하면 `CONNECT BY` 절에 조건을 넣어야 한다:
  `CONNECT BY PRIOR d.dept_id = d.parent_dept_id AND d.use_yn = 'Y'`
```

이 시점의 문서는 §1 과 §2.1 까지다. §2.2 는 Task 11 Step 10 에서 이어 붙인다.

- [ ] **Step 11: 커밋한다**

```powershell
git add src/main/java/com/flowmate/org src/main/resources/mapper src/main/webapp/WEB-INF/views/org src/test/java/com/flowmate/org docs/oracle-mapping.md
git commit -m "feat: 재귀 CTE 로 조직도 계층 조회

설계서 §6.1 의 CTE 는 path 를 dept_id 로만 만들어 형제 정렬이 id 순이 된다.
sort_order 를 NOT NULL 로 선언한 이유가 형제 정렬이므로 sort_order 를 앞에 두고
dept_id 를 tie-breaker 로 붙인다. LPAD 는 문자열 비교에서 '10' < '9' 가 되는 것을 막는다.

화면은 중첩 ul 대신 depth 클래스를 붙인 플랫 리스트로 렌더링한다.
JSP 의 jsp:include 재귀는 c:set scope=request 로 변수를 덮어써 부모 루프가
에러 없이 조용히 깨지고, 들여쓰기는 어차피 CSS 의 일이다.

oracle-mapping.md 는 Phase 6 이 아니라 지금부터 증분 관리한다.
사후에 쓰면 어디서 무엇을 썼는지 재조사해야 한다."
```

---

### Task 11: 사원 목록 — 동적 SQL, 페이징, 검색

**Files:**
- Create: `src/main/java/com/flowmate/org/domain/Employee.java`
- Create: `src/main/java/com/flowmate/org/mapper/EmployeeMapper.java`
- Create: `src/main/resources/mapper/org/EmployeeMapper.xml`
- Create: `src/main/java/com/flowmate/org/service/EmployeeService.java`
- Create: `src/main/java/com/flowmate/org/controller/EmployeeController.java`
- Create: `src/main/webapp/WEB-INF/views/org/employee-list.jsp`
- Test: `src/test/java/com/flowmate/org/mapper/EmployeeMapperIT.java`
- Modify: `docs/oracle-mapping.md`

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

```java
package com.flowmate.org.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;

/**
 * 사원 조회 동적 SQL 검증. 시드 20명(개발팀 7명)을 전제로 한다.
 */
@SpringBootTest
@Transactional
class EmployeeMapperIT {

    private static final long DEPT_ID_DEV_TEAM = 7L;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Test
    @DisplayName("조건이 없으면 전체 20명 중 첫 페이지 10명만 반환한다")
    void returnsFirstPageWithoutConditions() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        List<Employee> result = employeeMapper.search(cond);

        assertThat(employeeMapper.countSearch(cond)).isEqualTo(20);
        assertThat(result).hasSize(10);
    }

    @Test
    @DisplayName("두 번째 페이지는 남은 10명을 반환하고 첫 페이지와 겹치지 않는다")
    void secondPageDoesNotOverlapFirst() {
        EmployeeSearchCond first = new EmployeeSearchCond();
        EmployeeSearchCond second = new EmployeeSearchCond();
        second.setPage(2);

        List<String> firstNos = employeeMapper.search(first).stream().map(Employee::getEmpNo).toList();
        List<String> secondNos = employeeMapper.search(second).stream().map(Employee::getEmpNo).toList();

        assertThat(secondNos).hasSize(10).doesNotContainAnyElementsOf(firstNos);
    }

    @Test
    @DisplayName("부서로 걸러면 그 부서 사원만 나온다")
    void filtersByDepartment() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setDeptId(DEPT_ID_DEV_TEAM);

        List<Employee> result = employeeMapper.search(cond);

        assertThat(employeeMapper.countSearch(cond)).isEqualTo(7);
        assertThat(result).hasSize(7)
                .allSatisfy(emp -> assertThat(emp.getDeptName()).isEqualTo("개발팀"));
    }

    @Test
    @DisplayName("검색어는 이름과 사원번호 양쪽에 부분 일치로 걸린다")
    void searchesByNameOrEmpNo() {
        EmployeeSearchCond byName = new EmployeeSearchCond();
        byName.setKeyword("곽수빈");
        assertThat(employeeMapper.search(byName)).extracting(Employee::getEmpNo)
                .containsExactly("2020003");

        EmployeeSearchCond byEmpNo = new EmployeeSearchCond();
        byEmpNo.setKeyword("2016");
        assertThat(employeeMapper.countSearch(byEmpNo)).isEqualTo(4);
    }

    @Test
    @DisplayName("조회 결과는 부서명과 직급명을 함께 담고, 목록 조회에는 비밀번호가 실리지 않는다")
    void joinsOrgLabelsAndHidesPassword() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setKeyword("정도현");

        Employee emp = employeeMapper.search(cond).get(0);

        assertThat(emp.getDeptName()).isEqualTo("대표이사실");
        assertThat(emp.getPositionName()).isEqualTo("이사");
        assertThat(emp.getPositionLevel()).isEqualTo(6);
        assertThat(emp.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("사원번호로 조회하면 로그인에 필요한 비밀번호 해시까지 반환한다")
    void findByEmpNoIncludesPasswordHash() {
        Employee emp = employeeMapper.findByEmpNo("2020003");

        assertThat(emp).isNotNull();
        assertThat(emp.getEmpName()).isEqualTo("곽수빈");
        assertThat(emp.getRole()).isEqualTo("USER");
        assertThat(emp.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    @DisplayName("없는 사원번호로 조회하면 null 이다")
    void findByEmpNoReturnsNullWhenAbsent() {
        assertThat(employeeMapper.findByEmpNo("9999999")).isNull();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd verify -Dit.test=EmployeeMapperIT
```

기대: 컴파일 실패 — `cannot find symbol: class Employee`.

- [ ] **Step 3b: `EmployeeSearchCond` 에 `getKeywordEscaped()` 를 추가한다**

> Task 8 에서 만들지 않고 여기서 추가하는 이유: 매퍼가 없으면 쓸 데가 없다(YAGNI).
> 매퍼를 쓰는 시점에 함께 넣는다.

`src/main/java/com/flowmate/org/domain/EmployeeSearchCond.java` 에 아래 메서드를 추가한다.
기존 `getKeyword()` 는 그대로 둔다 — 검색 폼에 다시 표시할 값은 원본이어야 한다.

```java
    /**
     * LIKE 패턴에 넣을 검색어. `\` `%` `_` 를 이스케이프한다.
     *
     * 이스케이프하지 않으면 사용자가 입력한 % 와 _ 가 와일드카드로 해석된다.
     * 사원번호에 밑줄이 있는 경우(EMP_2024_01) _ 가 "임의의 한 글자" 가 되어
     * 의도보다 넓은 결과가 나온다. 주입 위험은 없지만(바인딩 파라미터) 결과가 조용히 틀어진다.
     *
     * 화면 표시용은 getKeyword() 를 쓴다. 이스케이프된 값을 폼에 되돌리면
     * 사용자가 입력하지 않은 역슬래시가 보인다.
     *
     * `\` 를 가장 먼저 치환해야 한다. 나중에 하면 앞서 넣은 이스케이프 문자를 또 이스케이프한다.
     */
    public String getKeywordEscaped() {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_");
    }
```

단위 테스트를 `EmployeeSearchCondTest` 에 추가한다.

```java
    @Test
    @DisplayName("LIKE 와일드카드 문자를 이스케이프하고 원본 검색어는 그대로 유지한다")
    void escapesLikeWildcards() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setKeyword("EMP_2024");
        assertThat(cond.getKeyword()).isEqualTo("EMP_2024");
        assertThat(cond.getKeywordEscaped()).isEqualTo("EMP\\_2024");

        cond.setKeyword("50%");
        assertThat(cond.getKeywordEscaped()).isEqualTo("50\\%");

        // 역슬래시를 먼저 치환하지 않으면 이중 이스케이프가 깨진다
        cond.setKeyword("a\\b");
        assertThat(cond.getKeywordEscaped()).isEqualTo("a\\\\b");

        cond.setKeyword(null);
        assertThat(cond.getKeywordEscaped()).isNull();
    }
```

- [ ] **Step 3: `Employee` 도메인을 만든다**

```java
package com.flowmate.org.domain;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 사원.
 *
 * Serializable 인 이유: 로그인 사용자 정보가 이 객체를 감싼 채 HTTP 세션에 올라간다.
 * Tomcat 이 재시작하거나 세션을 디스크로 내릴 때 직렬화가 필요하다.
 *
 * deptName / positionName / positionLevel 은 department · position 조인 결과를 담는
 * 조회 표시용 파생 필드다. employee 테이블의 컬럼이 아니다.
 */
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long empId;
    private String empNo;
    private String empName;
    private Long deptId;
    private Long positionId;
    private String email;
    private LocalDate hireDate;
    private String passwordHash;
    private String role;
    private String useYn;

    // 조인 결과 (조회 표시용)
    private String deptName;
    private String positionName;
    private int positionLevel;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public int getPositionLevel() {
        return positionLevel;
    }

    public void setPositionLevel(int positionLevel) {
        this.positionLevel = positionLevel;
    }

    /** 재직 중인지 */
    public boolean isActive() {
        return "Y".equals(this.useYn);
    }
}
```

- [ ] **Step 4: `EmployeeMapper` 인터페이스를 만든다**

```java
package com.flowmate.org.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;

@Mapper
public interface EmployeeMapper {

    /** 목록 조회. 비밀번호 해시는 선택하지 않는다. */
    List<Employee> search(EmployeeSearchCond cond);

    /** 같은 조건의 전체 건수 (페이징 계산용) */
    long countSearch(EmployeeSearchCond cond);

    /** 로그인 인증용. 비밀번호 해시를 포함한다. 없으면 null. */
    Employee findByEmpNo(String empNo);
}
```

- [ ] **Step 5: `EmployeeMapper.xml` 을 만든다**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.flowmate.org.mapper.EmployeeMapper">

    <!--
      검색 조건 조각. search 와 countSearch 가 공유해
      "목록과 건수의 조건이 어긋나 페이징이 깨지는" 사고를 구조적으로 막는다.
      employee 테이블 별칭만 쓰므로 countSearch 는 조인 없이 재사용할 수 있다.
    -->
    <sql id="searchWhere">
        <where>
            e.use_yn = 'Y'
            <if test="deptId != null">
                AND e.dept_id = #{deptId}
            </if>
            <!--
              ESCAPE 를 붙이는 이유: 사용자가 입력한 % 나 _ 가 LIKE 의 와일드카드로 해석된다.
              사원번호에 밑줄이 있으면(EMP_2024_01) _ 가 "임의의 한 글자" 가 되어
              엉뚱한 행까지 걸린다. 주입 위험은 없지만(바인딩 파라미터) 결과가 조용히 넓어진다.
              바인딩 값 쪽은 keywordEscaped 가 담당한다 (Step 3b 참조).
              조건 판정은 원본 keyword 로, 값은 이스케이프된 것으로 쓴다.
            -->
            <if test="keyword != null">
                AND (e.emp_name LIKE '%' || #{keywordEscaped} || '%' ESCAPE '\'
                  OR e.emp_no   LIKE '%' || #{keywordEscaped} || '%' ESCAPE '\')
            </if>
        </where>
    </sql>

    <!--
      목록 조회. password_hash 는 선택하지 않는다 —
      목록 응답에 해시가 실릴 이유가 없고, 실리면 화면·로그로 새어나갈 경로가 생긴다.

      정렬은 직급 내림차순 → 입사일 → emp_id 다. emp_id 를 마지막에 두는 이유는
      동일 직급·동일 입사일이 있을 때 페이지마다 순서가 흔들리는 것을 막기 위한 것이다
      (정렬이 불안정하면 1페이지와 2페이지에 같은 사람이 중복으로 나온다).
    -->
    <select id="search" resultType="Employee">
        SELECT e.emp_id, e.emp_no, e.emp_name, e.dept_id, e.position_id,
               e.email, e.hire_date, e.role, e.use_yn,
               d.dept_name,
               p.position_name, p.position_level
          FROM employee e
          JOIN department d ON d.dept_id     = e.dept_id
          JOIN position   p ON p.position_id = e.position_id
        <include refid="searchWhere"/>
         ORDER BY p.position_level DESC, e.hire_date, e.emp_id
         LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countSearch" resultType="long">
        SELECT COUNT(*)
          FROM employee e
        <include refid="searchWhere"/>
    </select>

    <!-- 로그인 인증 전용. 여기서만 password_hash 를 선택한다. -->
    <select id="findByEmpNo" resultType="Employee">
        SELECT e.emp_id, e.emp_no, e.emp_name, e.dept_id, e.position_id,
               e.email, e.hire_date, e.password_hash, e.role, e.use_yn,
               d.dept_name,
               p.position_name, p.position_level
          FROM employee e
          JOIN department d ON d.dept_id     = e.dept_id
          JOIN position   p ON p.position_id = e.position_id
         WHERE e.emp_no = #{empNo}
    </select>

</mapper>
```

- [ ] **Step 6: `EmployeeService` 를 만든다**

```java
package com.flowmate.org.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.common.web.Page;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;
import com.flowmate.org.mapper.EmployeeMapper;

@Service
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    public EmployeeService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    /**
     * 목록과 건수를 한 트랜잭션에서 읽어 Page 로 조립한다.
     * 건수 조회를 Controller 가 따로 부르지 않게 하는 것이 목적이다 —
     * 두 쿼리가 갈라지면 조건이 어긋나 페이징이 깨진다.
     */
    @Transactional(readOnly = true)
    public Page<Employee> search(EmployeeSearchCond cond) {
        long totalCount = employeeMapper.countSearch(cond);

        // ★ 목록 조회 전에 요청 페이지를 실제 마지막 페이지로 보정한다.
        //
        // 11페이지를 보던 중 검색을 좁히면 pagination.jsp 가 #searchForm 을 재전송하면서
        // page=11 을 그대로 보낸다. totalCount 가 20으로 줄면 startPage(11) > endPage(2) 가 되어
        // <c:forEach begin=11 end=2> 가 예외 없이 링크를 0개 그린다 — 페이징이 조용히 죽는다.
        //
        // 건수 조회가 목록 조회보다 먼저이므로 여기서 보정하면 재조회가 필요없다.
        int totalPages = Page.totalPagesOf(totalCount, cond.getSize());
        if (cond.getPage() > totalPages) {
            cond.setPage(totalPages);
        }

        List<Employee> content = employeeMapper.search(cond);
        return new Page<>(content, cond.getPage(), cond.getSize(), totalCount);
    }
}
```

> `cond` 를 그 자리에서 수정하는 이유: 이 객체는 요청 하나에 묶인 바인딩 객체이고,
> Controller 가 같은 인스턴스를 화면 모델(`cond`)로 되돌려 검색 폼과 hidden `page` 값을 채운다.
> 보정된 값이 화면에도 반영되어야 다음 클릭이 정상 범위에서 출발한다.

- [ ] **Step 7: 통합 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd verify
```

기대: 단위 16건 + 통합 11건(Department 4 + Employee 7) 전부 통과.

- [ ] **Step 8: `EmployeeController` 와 `employee-list.jsp` 를 만든다**

```java
package com.flowmate.org.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.org.domain.EmployeeSearchCond;
import com.flowmate.org.service.DepartmentService;
import com.flowmate.org.service.EmployeeService;

@Controller
@RequestMapping("/org")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    public EmployeeController(EmployeeService employeeService, DepartmentService departmentService) {
        this.employeeService = employeeService;
        this.departmentService = departmentService;
    }

    /**
     * cond 는 요청 파라미터가 바인딩된 뒤 화면으로 되돌려져 검색 폼의 값을 유지한다.
     * 모델 이름을 "paging" 으로 쓰는 이유는 common/pagination.jsp 의 규약이다.
     */
    @GetMapping("/employees")
    public String list(EmployeeSearchCond cond, Model model) {
        model.addAttribute("paging", employeeService.search(cond));
        model.addAttribute("cond", cond);
        model.addAttribute("deptOptions", departmentService.findDeptTree());
        return "org/employee-list";
    }
}
```

`src/main/webapp/WEB-INF/views/org/employee-list.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="사원 목록"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">사원 목록</h2>

        <%--
          id="searchForm" 과 hidden page 는 common/pagination.jsp 의 사용 조건이다.
          이름을 바꾸면 페이징 링크가 에러 없이 조용히 동작하지 않는다.
        --%>
        <form id="searchForm" class="search-form" method="get"
              action="${pageContext.request.contextPath}/org/employees">
            <input type="hidden" name="page" value="${paging.page}">
            <div class="form-row">
                <label class="form-label" for="deptId">부서</label>
                <select class="form-input" id="deptId" name="deptId">
                    <option value="">전체</option>
                    <c:forEach items="${deptOptions}" var="d">
                        <option value="${d.deptId}" ${d.deptId eq cond.deptId ? 'selected' : ''}>
                            <c:out value="${d.deptName}"/>
                        </option>
                    </c:forEach>
                </select>

                <label class="form-label" for="keyword">검색</label>
                <input class="form-input" type="text" id="keyword" name="keyword"
                       value="${fn:escapeXml(cond.keyword)}" placeholder="이름 또는 사원번호">

                <button class="btn btn--primary" type="submit">검색</button>
            </div>
        </form>

        <p class="result-count">전체 <strong>${paging.totalCount}</strong>명</p>

        <table class="emp-list">
            <thead>
            <tr>
                <th>사원번호</th>
                <th>이름</th>
                <th>부서</th>
                <th>직급</th>
                <th>입사일</th>
                <th>이메일</th>
            </tr>
            </thead>
            <tbody>
            <%--
              "결과 없음" 판정은 paging.empty 가 아니라 totalCount 로 한다.
              Service 가 페이지를 보정하므로 지금은 둘이 같은 뜻이지만,
              totalCount 가 "검색 조건에 맞는 행이 정말 없다" 를 직접 말하는 값이다.
              paging.empty 는 "이 페이지에 행이 없다" 이므로 페이지 보정이 빠지면 거짓을 말한다.
            --%>
            <c:choose>
                <c:when test="${paging.totalCount == 0}">
                    <tr>
                        <td class="emp-list__empty" colspan="6">조회 결과가 없습니다.</td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${paging.content}" var="emp">
                        <tr>
                            <td><c:out value="${emp.empNo}"/></td>
                            <td><c:out value="${emp.empName}"/></td>
                            <td><c:out value="${emp.deptName}"/></td>
                            <td><c:out value="${emp.positionName}"/></td>
                            <%-- LocalDate.toString() 이 이미 yyyy-MM-dd 이므로 fmt 태그가 필요 없다 --%>
                            <td>${emp.hireDate}</td>
                            <td><c:out value="${emp.email}"/></td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>

        <jsp:include page="../common/pagination.jsp"/>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
```

- [ ] **Step 9: 화면에서 검색과 페이징을 직접 확인한다**

```powershell
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080/org/employees` 접속 후 넷 다 확인한다:

1. 전체 20명, 10명씩 표시되고 페이징 링크에 `1 2` 가 보인다
2. `2` 를 누르면 나머지 10명이 나오고 **검색 폼의 값이 유지된다**
3. 부서를 `개발팀` 으로 고르고 검색 → 7명, 페이징 링크가 사라진다
4. 검색어에 `2016` 을 넣으면 4명이 나온다

2번이 안 되면 `common.js` 의 페이징 위임이 동작하지 않는 것이다.
개발자도구 콘솔에서 `$('#searchForm').length` 가 `1` 인지 확인한다.

- [ ] **Step 10: `docs/oracle-mapping.md` 에 §2.2 와 §2.3 을 이어 붙인다**

파일 끝(§2.1 의 Oracle 주의사항 뒤)에 추가한다.

```markdown
### 2.2 사원 목록 페이징 — `mapper/org/EmployeeMapper.xml#search`

PostgreSQL:

```sql
 ORDER BY p.position_level DESC, e.hire_date, e.emp_id
 LIMIT #{limit} OFFSET #{offset}
```

Oracle 12c 이상:

```sql
 ORDER BY p.position_level DESC, e.hire_date, e.emp_id
 OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY
```

Oracle 11g 이하:

```sql
SELECT * FROM (
    SELECT inner_q.*, ROWNUM AS rn FROM (
        SELECT ... ORDER BY p.position_level DESC, e.hire_date, e.emp_id
    ) inner_q
     WHERE ROWNUM <= #{offset} + #{limit}
)
 WHERE rn > #{offset}
```

**주의:** 11g 방식은 `ROWNUM` 을 정렬 뒤에 매기기 위해 인라인 뷰가 두 겹 필요하다.
한 겹으로 쓰면 정렬 전에 번호가 매겨져 엉뚱한 행이 나온다.

### 2.3 부분 일치 검색 — 같은 파일 `searchWhere`

```sql
e.emp_name LIKE '%' || #{keyword} || '%'
```

`||` 결합은 Oracle 에서 동일하게 동작한다. 변환이 필요 없다.
단, Oracle 에서 `#{keyword}` 가 빈 문자열이면 `NULL` 로 취급되어 조건 전체가 `NULL` 이 된다.
FlowMate 는 `EmployeeSearchCond` 의 setter 가 빈 문자열을 `null` 로 바꾸고
매퍼가 `<if test="keyword != null">` 로 감싸므로 이 경로에 들어가지 않는다.
```

- [ ] **Step 11: 커밋한다**

```powershell
git add src/main/java/com/flowmate/org src/main/resources/mapper/org/EmployeeMapper.xml src/main/webapp/WEB-INF/views/org/employee-list.jsp src/test/java/com/flowmate/org/mapper/EmployeeMapperIT.java docs/oracle-mapping.md
git commit -m "feat: 사원 목록 - 동적 SQL 검색과 페이징

목록 조회와 건수 조회가 <sql id=searchWhere> 조각을 공유한다.
두 쿼리의 조건이 갈라지면 총 건수와 실제 결과가 어긋나 페이징이 깨지는데,
조각을 공유하면 그 사고가 구조적으로 불가능해진다.

정렬 마지막에 emp_id 를 붙인 이유는 안정 정렬이다.
동일 직급·동일 입사일이 있으면 정렬이 흔들려 1페이지와 2페이지에 같은 사람이 나온다.

목록 조회는 password_hash 를 선택하지 않는다. 해시가 실릴 이유가 없고
실리면 화면과 로그로 새는 경로가 생긴다. findByEmpNo 만 예외로 둔다."
```

---

### Task 12: Spring Security 로그인

> **여기서 처음 Security 의존성을 추가한다.** 더 일찍 넣으면 기본 Basic 인증이 모든 URL을 막아
> Task 3~11의 화면 확인이 불가능해진다.
>
> `common/header.jsp` 는 Task 9에서 이미 `${loginEmployee}` 블록을 완성해 두었다.
> **이 Task 에서 그 파일을 다시 열지 않는다** — 설계서 §4.4.1의 원칙이 실제로 성립하는지의 첫 검증이다.

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/flowmate/org/security/EmployeeUserDetailsServiceTest.java`
- Create: `src/main/java/com/flowmate/org/security/LoginEmployee.java`
- Create: `src/main/java/com/flowmate/org/security/EmployeeUserDetailsService.java`
- Create: `src/main/java/com/flowmate/org/security/LoginEmployeeAdvice.java`
- Create: `src/main/java/com/flowmate/config/SecurityConfig.java`
- Create: `src/main/java/com/flowmate/org/controller/LoginController.java`
- Create: `src/main/webapp/WEB-INF/views/login.jsp`
- Test: `src/test/java/com/flowmate/org/security/LoginIT.java`

- [ ] **Step 1: `pom.xml` 에 Security 의존성을 추가한다**

`spring-boot-starter-web` 바로 뒤에:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```

`spring-boot-starter-test` 바로 뒤에:

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 실패하는 단위 테스트를 쓴다 (DB 없이, Mockito)**

`src/test/java/com/flowmate/org/security/EmployeeUserDetailsServiceTest.java`:

```java
package com.flowmate.org.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;

@ExtendWith(MockitoExtension.class)
class EmployeeUserDetailsServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeUserDetailsService userDetailsService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setEmpId(18L);
        employee.setEmpNo("2020003");
        employee.setEmpName("곽수빈");
        employee.setDeptId(7L);
        employee.setDeptName("개발팀");
        employee.setPositionName("사원");
        employee.setPositionLevel(1);
        employee.setHireDate(LocalDate.of(2020, 3, 2));
        employee.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        employee.setRole("USER");
        employee.setUseYn("Y");
    }

    @Test
    @DisplayName("권한 문자열에 ROLE_ 접두사를 붙인다")
    void prefixesRoleWithRoleUnderscore() {
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("사원번호가 아이디이고 비밀번호 해시를 그대로 전달한다")
    void usesEmpNoAsUsernameAndPassesHash() {
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.getUsername()).isEqualTo("2020003");
        assertThat(loaded.getPassword()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
        assertThat(loaded.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("없는 사원번호면 UsernameNotFoundException 을 던진다")
    void throwsWhenEmpNoNotFound() {
        when(employeeMapper.findByEmpNo("9999999")).thenReturn(null);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("9999999"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("퇴직 처리된 사원은 계정이 비활성으로 표시된다")
    void retiredEmployeeIsDisabled() {
        employee.setUseYn("N");
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("상단 메뉴가 쓸 사원명·부서·직급을 함께 노출한다")
    void exposesOrgLabelsForHeader() {
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.getEmpId()).isEqualTo(18L);
        assertThat(loaded.getEmpName()).isEqualTo("곽수빈");
        assertThat(loaded.getDeptId()).isEqualTo(7L);
        assertThat(loaded.getDeptName()).isEqualTo("개발팀");
        assertThat(loaded.getPositionName()).isEqualTo("사원");
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd test -Dtest=EmployeeUserDetailsServiceTest
```

기대: 컴파일 실패 — `cannot find symbol: class EmployeeUserDetailsService`.

- [ ] **Step 4: `LoginEmployee` 를 만든다**

```java
package com.flowmate.org.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.flowmate.org.domain.Employee;

/**
 * 세션에 올라가는 로그인 사용자.
 *
 * Employee 를 감싸기만 하고 복제하지 않는다.
 * empName/deptName/positionName 을 노출하는 이유는 common/header.jsp 가
 * 매 화면에서 DB 조회 없이 사용자 정보를 표시할 수 있게 하기 위한 것이다.
 *
 * Controller 는 @AuthenticationPrincipal LoginEmployee 로 이 객체를 받고
 * Service 에는 필요한 식별자(empId 등)만 넘긴다.
 * Service 가 HttpSession 이나 SecurityContext 를 모르게 유지하기 위한 규약이다 (설계서 §4.3).
 */
public class LoginEmployee implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Employee employee;

    public LoginEmployee(Employee employee) {
        this.employee = employee;
    }

    public Long getEmpId() {
        return employee.getEmpId();
    }

    public String getEmpName() {
        return employee.getEmpName();
    }

    public Long getDeptId() {
        return employee.getDeptId();
    }

    public String getDeptName() {
        return employee.getDeptName();
    }

    public String getPositionName() {
        return employee.getPositionName();
    }

    public int getPositionLevel() {
        return employee.getPositionLevel();
    }

    public String getRole() {
        return employee.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // DB의 role 은 USER/MANAGER/ADMIN 이고 hasRole("ADMIN") 은 ROLE_ADMIN 을 찾는다.
        // 접두사를 여기서 붙이지 않으면 URL 인가가 항상 실패한다.
        return List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole()));
    }

    @Override
    public String getPassword() {
        return employee.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return employee.getEmpNo();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return employee.isActive();
    }
}
```

- [ ] **Step 5: `EmployeeUserDetailsService` 를 만든다**

```java
package com.flowmate.org.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;

/**
 * 사원번호를 아이디로 쓰는 인증 원천.
 * 비밀번호 비교는 DaoAuthenticationProvider 가 PasswordEncoder 로 수행하므로 여기서 하지 않는다.
 */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeMapper employeeMapper;

    public EmployeeUserDetailsService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String empNo) throws UsernameNotFoundException {
        Employee employee = employeeMapper.findByEmpNo(empNo);
        if (employee == null) {
            throw new UsernameNotFoundException("사원번호를 찾을 수 없습니다: " + empNo);
        }
        // 퇴직자(use_yn='N')는 예외를 던지지 않고 isEnabled()=false 로 넘긴다.
        // 계정 상태 판단을 Spring Security 의 표준 흐름에 맡기기 위한 것이다.
        return new LoginEmployee(employee);
    }
}
```

- [ ] **Step 6: 단위 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd test
```

기대: `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0` (Page 9 + SearchCond 7 + UserDetailsService 5)

- [ ] **Step 7: `LoginEmployeeAdvice` 를 만든다**

```java
package com.flowmate.org.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면 모델에 loginEmployee 를 넣는다. common/header.jsp 가 이 값을 쓴다.
 *
 * 화면마다 Controller 가 직접 담지 않는 이유:
 * 화면이 늘어날 때마다 같은 코드를 복사해야 하고, 한 곳을 빼먹으면
 * 그 화면에서만 상단 사용자 정보가 조용히 사라진다.
 *
 * 미인증 요청에서는 principal 이 null 이라 header.jsp 의 c:if 가 블록을 건너뛴다.
 */
@ControllerAdvice
public class LoginEmployeeAdvice {

    @ModelAttribute("loginEmployee")
    public LoginEmployee loginEmployee(@AuthenticationPrincipal LoginEmployee principal) {
        return principal;
    }
}
```

- [ ] **Step 8: `SecurityConfig` 를 만든다**

```java
package com.flowmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 6 최소 구성.
 * WebSecurityConfigurerAdapter 는 제거되었으므로 SecurityFilterChain 빈으로 배선한다.
 *
 * CSRF 는 끄지 않는다. JSP 폼은 hidden input 으로, jQuery AJAX 는
 * static/js/common.js 의 $.ajaxSetup 이 헤더로 토큰을 보낸다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 시드 비밀번호는 pgcrypto 의 crypt(pw, gen_salt('bf', 10)) 으로 만들어져
     * $2a$10$ 형식이다. BCryptPasswordEncoder 가 그 형식을 그대로 검증한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/static/**", "/login", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("empNo")
                .passwordParameter("password")
                // true 를 주는 이유: 저장된 이전 요청으로 돌아가지 않고 항상 홈으로 보낸다.
                // 데모 중 예상 못한 화면으로 튀는 것을 막는다.
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
```

- [ ] **Step 9: `LoginController` 와 `login.jsp` 를 만든다**

```java
package com.flowmate.org.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /** POST /login 은 Spring Security 필터가 처리한다. 여기는 화면만 담당한다. */
    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }
}
```

`src/main/webapp/WEB-INF/views/login.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="common/head.jsp">
    <jsp:param name="pageTitle" value="로그인"/>
</jsp:include>
<body class="login-page">
<div class="login-box">
    <h1 class="login-box__title">FlowMate</h1>
    <p class="login-box__subtitle">AI 사전점검 그룹웨어</p>

    <c:if test="${param.error != null}">
        <p class="alert alert--error">사원번호 또는 비밀번호가 올바르지 않습니다.</p>
    </c:if>
    <c:if test="${param.logout != null}">
        <p class="alert alert--info">로그아웃되었습니다.</p>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/login">
        <%--
          Spring Security 6 은 일반 <form> 에 CSRF 토큰을 자동 주입하지 않는다.
          이 hidden input 이 없으면 로그인 POST 가 403 으로 막힌다.
        --%>
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>

        <div class="form-row">
            <label class="form-label" for="empNo">사원번호</label>
            <input class="form-input" type="text" id="empNo" name="empNo" autofocus required>
        </div>
        <div class="form-row">
            <label class="form-label" for="password">비밀번호</label>
            <input class="form-input" type="password" id="password" name="password" required>
        </div>
        <button class="btn btn--primary" type="submit">로그인</button>
    </form>
</div>
</body>
</html>
```

- [ ] **Step 10: 실패하는 로그인 통합 테스트를 쓴다**

`src/test/java/com/flowmate/org/security/LoginIT.java`:

```java
package com.flowmate.org.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 흐름 검증.
 *
 * 이 테스트가 통과하는 것은 pgcrypto 로 만든 $2a$ 해시가
 * BCryptPasswordEncoder 로 실제 검증된다는 뜻이기도 하다 (로드맵 D5의 전제).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미인증 상태로 보호된 화면에 접근하면 로그인 페이지로 리다이렉트된다")
    void unauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/org/employees"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("시드 계정으로 로그인하면 인증되고 홈으로 이동한다")
    void loginSucceedsWithSeedAccount() throws Exception {
        mockMvc.perform(post("/login")
                        .param("empNo", "2020003")
                        .param("password", "flowmate1!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("2020003").withRoles("USER"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 인증되지 않고 실패 URL로 돌아간다")
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/login")
                        .param("empNo", "2020003")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("USER 권한으로 인증된 사용자는 관리자 영역에서 403 을 받는다")
    @WithMockUser(username = "2020003", roles = "USER")
    void nonAdminIsForbiddenFromAdminArea() throws Exception {
        // /admin/** 에는 아직 화면이 없다. 404 가 아니라 403 이 나오는 것이
        // 곧 인가 규칙이 컨트롤러보다 먼저 걸렸다는 뜻이다.
        mockMvc.perform(get("/admin/anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 권한으로 인증된 사용자는 관리자 영역에서 403 을 받지 않는다")
    @WithMockUser(username = "2015001", roles = "ADMIN")
    void adminPassesAdminAreaAuthorization() throws Exception {
        // 인가는 통과하므로 매핑이 없어서 404 가 된다. 403 이 아니라는 점이 검증 대상이다.
        mockMvc.perform(get("/admin/anything"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 11: 통합 테스트를 실행한다**

```powershell
docker compose up -d postgres
.\mvnw.cmd verify
```

기대: 단위 21건 + 통합 16건(Department 4 + Employee 7 + Login 5) 전부 통과.

실패 시 진단:

| 증상 | 원인 |
|---|---|
| 로그인이 `redirectedUrl("/login?error")` 로 감 | pgcrypto 해시 형식이 `$2a$` 가 아니다. Task 4 Step 4를 다시 확인한다 |
| `403 Forbidden` | `.with(csrf())` 누락 |
| `withRoles("USER")` 실패 | `LoginEmployee.getAuthorities()` 의 `ROLE_` 접두사 누락 |
| 컨텍스트 로딩 실패 | `spring-security-test` 의존성 누락 |

> **해시가 `$2a$` 가 아니라면** 대안은 시드 SQL의 `crypt(...)` 를 애플리케이션에서 만든 해시 문자열로 바꾸는 것이다.
> `BCryptPasswordEncoder` 로 `flowmate1!` 을 인코딩한 결과 한 개를 20개 행에 같이 쓰면 된다
> (BCrypt 해시에는 솔트가 포함되어 있어 같은 값을 재사용해도 검증은 정상 동작한다).

- [ ] **Step 12: 브라우저에서 전체 흐름을 확인한다**

```powershell
.\mvnw.cmd spring-boot:run
```

1. `http://localhost:8080/org/employees` → 로그인 페이지로 튕긴다
2. `2020003` / `flowmate1!` 로 로그인 → 홈으로 이동
3. **상단에 `곽수빈 · 개발팀 · 사원` 이 보인다** — Task 9에서 만든 `header.jsp` 를 열지 않고 동작한다
4. 좌측 메뉴에서 사원 목록·조직도 이동
5. 로그아웃 → `로그아웃되었습니다.` 메시지가 보인다
6. 틀린 비밀번호 → `사원번호 또는 비밀번호가 올바르지 않습니다.`

3번이 안 되면 `LoginEmployeeAdvice` 가 등록되지 않은 것이다 (`@ControllerAdvice` 누락 또는 패키지 스캔 범위 밖).

- [ ] **Step 13: 커밋한다**

```powershell
git add pom.xml src/main/java src/main/webapp/WEB-INF/views/login.jsp src/test/java/com/flowmate/org/security
git commit -m "feat: Spring Security formLogin 인증

사원번호를 아이디로 쓰고 비밀번호는 BCrypt 로 검증한다.
Security 의존성을 Phase 1 마지막에 추가하는 이유는, 더 일찍 넣으면
기본 Basic 인증이 모든 URL 을 막아 화면 확인이 불가능해지기 때문이다.

퇴직자는 예외를 던지지 않고 isEnabled()=false 로 넘겨 계정 상태 판단을
Spring Security 표준 흐름에 맡긴다.

CSRF 는 끄지 않는다. Boot 3 + JSP 조합에서는 일반 form 에 토큰이 자동 주입되지 않으므로
JSP 는 hidden input 으로, AJAX 는 common.js 의 ajaxSetup 이 헤더로 보낸다.

header.jsp 는 Task 9 에서 완성해 두어 이 커밋에서 열지 않았다.
설계서 §4.4.1 의 '구조를 먼저'가 실제로 성립하는지의 첫 검증이다."
```

---

### Task 13: Phase 1 마감

**Files:**
- Modify: `README.md`
- Modify: `src/main/webapp/static/css/style.css` (클래스 목록 확인)

- [ ] **Step 1: 전체 테스트를 처음부터 돌린다**

```powershell
docker compose down -v
docker compose up -d postgres
.\mvnw.cmd clean verify
```

기대: `BUILD SUCCESS`, 단위 21건 + 통합 16건.
**`clean` 과 `down -v` 를 넣는 이유:** 빈 상태에서 시작해도 재현되는지 확인한다.
여기서 실패하면 어딘가에 "내 PC에서만 되는" 상태가 남아 있다는 뜻이다.

- [ ] **Step 2: WAR 산출물을 확인한다**

```powershell
.\mvnw.cmd clean package
Get-Item target\flowmate.war | Select-Object Name, Length
```

기대: `flowmate.war` 존재.

- [ ] **Step 3: `style.css` 의 클래스 목록이 실제와 맞는지 대조한다**

```powershell
Select-String -Path src\main\webapp\WEB-INF\views\*.jsp, src\main\webapp\WEB-INF\views\**\*.jsp -Pattern 'class="([^"]+)"' -AllMatches |
    ForEach-Object { $_.Matches } |
    ForEach-Object { $_.Groups[1].Value -split '\s+' } |
    Sort-Object -Unique
```

출력된 클래스가 `style.css` 상단 목록에 모두 있는지 확인하고, 빠진 것을 추가한다.
**이 대조가 Phase 6 CSS 작업의 유일한 입력이다.** 목록이 실제와 어긋나면 Phase 6에서 JSP를 다시 열어야 한다.

시각 기반 이름(`blue-*`, `big-*`, `red`, `left`, `bold` 등)이 하나라도 있으면 지금 고친다.
설계서 §4.4.2 위반이고, Phase 6에 발견되면 그때는 고칠 시간이 없다.

- [ ] **Step 4: `README.md` 에 데모 계정과 구현 현황을 추가한다**

`## 실행 방법` 뒤에 넣는다.

```markdown
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

부서마다 최고 직급이 1명씩만 배치되어 있다. Phase 2의 결재선 정책이
"같은 부서 최고 직급"으로 부서장을 판정하기 때문이다.

## 구현 현황

- [x] Phase 0 — 환경 구축 (JSP + Jakarta JSTL + MyBatis + PostgreSQL)
- [x] Phase 1 — 조직 · 사용자 (로그인, 사원 목록, 조직도, 공통 레이아웃)
- [ ] Phase 2 — 전자결재 코어
- [ ] Phase 3 — AI 게이트웨이
- [ ] Phase 4 — 근태 + 연동
- [ ] Phase 5 — AI 기능
- [ ] Phase 6 — 마감 (CSS · Docker 배포 · README)
```

- [ ] **Step 5: 커밋하고 머지하고 태그를 붙인다**

```powershell
git add README.md src/main/webapp/static/css/style.css
git commit -m "docs: Phase 1 데모 계정과 조직 구조 기록

부서마다 최고 직급이 1명씩인 배치가 Phase 2 결재선 판정의 전제이므로 README 에 남긴다."

git switch main
git merge --no-ff feat/phase-1-org-user -m "merge: Phase 1 조직 · 사용자 토대 완료"
git tag -a phase-1-org-user -m "Phase 1: 로그인 · 사원 목록 · 조직도 · 공통 레이아웃 골격"
git push origin main --follow-tags
```

push 결과 확인:

```powershell
git status -sb
gh repo view --json url -q .url
```

기대: `## main...origin/main` (ahead 표시 없음), 저장소 URL 출력.

- [ ] **Step 6: Phase 1 완료 기준을 확인한다**

설계서 §9 Phase 1의 완료 기준:

- [ ] 사원 목록이 화면에 뜬다 (검색·페이징 동작)
- [ ] 로그인/로그아웃이 되고 미인증 접근이 차단된다
- [ ] 부서 계층이 재귀 CTE로 조회되어 화면에 렌더링된다
- [ ] 이후 모든 화면이 복사할 수 있는 레이아웃 원본이 존재한다
- [ ] `id`/`class` 명명 규칙이 확정되고 `style.css` 목록에 기록됐다

이 계획서가 추가로 요구하는 것:

- [ ] `docker compose down -v` 후 `clean verify` 가 통과한다 (재현 가능)
- [ ] 단위 테스트 21건이 **Docker 없이** 통과한다
- [ ] `docs/oracle-mapping.md` 에 지금까지 쓴 PostgreSQL 전용 문법 3종이 기록됐다
- [ ] Task 12에서 `header.jsp` 를 열지 않았다 (구조 우선 원칙 검증)

---

## 다음 단계

Phase 2 계획서를 작성한다. 그 전에 이 Phase에서 확정된 것을 로드맵에 반영한다.

- [ ] 로드맵 §5 Q1(Spring Boot 3.2 EOL)에 대해 사용자 판단을 받는다
- [ ] 로드맵 §5 Q3(`position` 테이블명)를 Task 6 결과로 확정 처리한다
- [ ] 로드맵 §6 진행 상황 표에서 계획서 1을 "완료"로 바꾼다
- [ ] `docs/superpowers/plans/phase-2-approval-core.md` 작성
  — 상태 기계 단위 테스트 8건, `ApprovalLinePolicy` 2종, 반려 유형 화면이 핵심

---

## 부록 — 이 계획서의 설계서 대응 확인

설계서의 Phase 0·1 관련 요구사항이 어느 Task에서 처리되는지.

| 설계서 항목 | Task |
|---|---|
| §3.1 JSP + Jakarta JSTL 배선 (taglib URI, WAR, Jasper provided) | 2, 3 |
| §3.1 탈출 조건 (4시간 초과 시 Boot 2.7 하향) | 3 |
| §4.1 요청 흐름 (Controller → Service → Mapper) | 10, 11 |
| §4.2 패키지 구조 | 전체. `MyBatisConfig` 는 제외 (로드맵 D6) |
| §4.3 계층 규칙 (Controller는 Service만, Service는 세션 모름, private + getter) | 11, 12 |
| §4.4 공통 조각 5종 | 9. `ai-panel.jsp` 는 Phase 5 (로드맵 D7) |
| §4.4.1 구조는 처음에, 외양은 마지막에 | 9 (순서 변경 — 로드맵 D1), 12 Step 12로 검증 |
| §4.4.2 의미 기반 클래스 명명 | 9 Step 4, 13 Step 3에서 대조 |
| §5.1 조직·사용자 스키마 | 6 |
| §5.6 Oracle 대응표 | 10 Step 10, 11 Step 10 (Phase 6 → 증분으로 변경, 로드맵 D8) |
| §6.1 조직도 재귀 CTE | 10 (`sort_path` 개선 — 로드맵 D3) |
| §6.1 Spring Security 최소 구성 (formLogin, UserDetailsService, BCrypt, URL 인가) | 12 |
| §8 테스트 전략 (순수 로직 단위 + DB 통합 분리) | 2 (Failsafe 배선), 7·8·12 (단위), 10·11·12 (통합) |
| §9 Phase 0 완료 기준 | 3, 5 |
| §9 Phase 1 완료 기준 | 13 Step 6 |
| §9.2 Git 전략 (브랜치 · 태그 · 한국어 커밋) | 전체 |
| §12.1 명명 규칙 (`flowmate`, `com.flowmate`, `flowmate.war`, README 제목) | 1, 2 |

**이 계획서가 다루지 않는 설계서 항목** (해당 Phase 계획서에서 처리):
§5.2~5.5 스키마, §6.2 전자결재, §6.3 근태, §6.4 AI 계층, §7 커스터마이징 지점, §10 완료 기준, §11 리스크 R2~R7.
