-- AI 기능 스키마 (설계서 §5.5 ai_preflight_result, 계획서 5 Task 1 ai_prompt).
--
-- ★ 이 파일은 두 경로에서 실행된다 (계획서 5 D7, 계획서 3 D5·계획서 4 D8과 동일한 관례):
--   1) 새 환경: docker compose up 이 빈 볼륨에서 자동 실행한다
--   2) 기존 환경: docker exec ... psql -f 로 손으로 한 번 적용한다
--
-- 그래서 IF NOT EXISTS 가 필수다. docker compose down -v 로 볼륨을 비우는 것은
-- Phase 0~4 데이터를 전부 잃는 것이므로 금지한다(계획서 5 D7). 반드시 두 번 실행해
-- 멱등성을 확인한다 - 실패한 init 스크립트는 컨테이너를 죽이지 않고, 테이블 없이
-- healthy 한 컨테이너를 남기기 때문에 이 확인을 건너뛰면 새 환경에서 조용히 깨진다.

-- 경고를 무시하고 상신했는지 추적 -> 훗날 기능 유효성 측정 근거 (설계서 §5.5)
CREATE TABLE IF NOT EXISTS ai_preflight_result (
    result_id     BIGSERIAL   PRIMARY KEY,
    approval_id   BIGINT      NOT NULL REFERENCES approval_doc(approval_id),
    verdict       VARCHAR(10) NOT NULL,
    findings_json TEXT,
    ignored_yn    CHAR(1)     NOT NULL DEFAULT 'N',
    checked_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_preflight_result             IS '사전점검 결과. 설계서 §6.4.6 - 결재 문서 상신 직전 AI 점검 이력';
COMMENT ON COLUMN ai_preflight_result.verdict     IS 'PASS/WARN';
COMMENT ON COLUMN ai_preflight_result.ignored_yn  IS 'Y/N. WARN 인데도 "무시하고 상신"을 눌렀는지 - 이 기능이 실제로 쓰이는지 측정하는 근거가 된다';
COMMENT ON COLUMN ai_preflight_result.findings_json IS '구조화 출력(PreflightResult.findings)을 JSON 배열로 그대로 저장한다';

CREATE INDEX IF NOT EXISTS idx_preflight_approval ON ai_preflight_result (approval_id);

-- approval_id 에 FK 를 거는 이유는 ai_call_log/ai_result_cache 와 다르다:
-- 이 테이블은 "그 문서에 실제로 무슨 지적이 있었는가"의 감사 기록이므로 원본 문서가
-- 반드시 존재해야 의미가 있다. 문서가 삭제될 일이 없는 이 프로젝트(결재 문서는 CANCEL
-- 상태로 남지 삭제되지 않는다)에서는 FK 가 로그 보존을 막지 않는다.

-- 커스터마이징 지점 4(PromptRepository)의 두 번째 구현(DatabasePromptRepository, Task 8)이
-- 읽을 테이블. 지금은 배선만 해 두고 비워 둔다 - 그 구현이 생기기 전까지 이 테이블을
-- 읽는 코드가 없다.
CREATE TABLE IF NOT EXISTS ai_prompt (
    feature    VARCHAR(30) NOT NULL,
    version    VARCHAR(20) NOT NULL,
    body       TEXT        NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (feature, version)
);

COMMENT ON TABLE  ai_prompt        IS 'DatabasePromptRepository(Task 8) 가 읽는 프롬프트 저장소. File 구현과 같은 (feature, version) 키 규약을 쓴다';
COMMENT ON COLUMN ai_prompt.feature IS 'SUMMARY/PREFLIGHT/LEAVE_CONTEXT - ai_result_cache.feature 와 같은 값 집합';
COMMENT ON COLUMN ai_prompt.version IS 'FilePromptRepository 의 파일명 버전(v1 등)과 같은 규약 - 설정으로 구현체를 바꿔도 버전 의미가 달라지지 않는다';
