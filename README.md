# FlowMate

### AI 사전점검 그룹웨어 — 전자결재 · 근태관리

AI가 결재 반려를 미리 막아주는 사내 그룹웨어.

- 설계서: [docs/superpowers/specs/2026-08-05-flowmate-design.md](docs/superpowers/specs/2026-08-05-flowmate-design.md)
- 구현 로드맵: [docs/superpowers/plans/2026-08-05-flowmate-roadmap.md](docs/superpowers/plans/2026-08-05-flowmate-roadmap.md)

## 기술 스택

Java 17 · Spring Boot 3.2 (WAR) · JSP + JSTL + jQuery · MyBatis 3 · PostgreSQL 16 · Spring Security 6 · Maven · Docker

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

## 설계 판단 기록

작성 예정 (Phase 6)
