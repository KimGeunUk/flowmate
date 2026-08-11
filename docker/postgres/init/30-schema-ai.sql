-- AI 게이트웨이 스키마
--
-- ★ 이 파일은 두 경로에서 실행된다:
--   1) 새 환경: docker compose up 이 빈 볼륨에서 자동 실행한다
--   2) 기존 환경: docker exec ... psql -f 로 손으로 한 번 적용한다
--
-- 그래서 IF NOT EXISTS 가 필수다. 없으면 2번 경로에서 에러가 난다.
-- init 스크립트는 빈 볼륨에서만 돌기 때문에, 스키마를 추가할 때마다
-- docker compose down -v 로 볼륨을 비우는 것이 유일한 방법처럼 보이지만
-- 그렇게 하면 Phase 마다 데이터를 잃는다. 이 파일은 그 함정을 피한 방식이다.

CREATE TABLE IF NOT EXISTS ai_result_cache (
    cache_key      VARCHAR(64) PRIMARY KEY,
    feature        VARCHAR(30) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    result_json    TEXT        NOT NULL,
    model          VARCHAR(50) NOT NULL,
    input_tokens   INT,
    output_tokens  INT,
    hit_count      INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_result_cache                IS 'AI 호출 결과 캐시';
COMMENT ON COLUMN ai_result_cache.cache_key      IS 'SHA-256(feature:promptVersion:input) 의 hex 64자';
COMMENT ON COLUMN ai_result_cache.prompt_version IS '캐시 키에 포함된다. 프롬프트를 고치면 옛 캐시가 자동으로 무효가 된다';
COMMENT ON COLUMN ai_result_cache.hit_count      IS '캐시가 실제로 쓰였는지 측정한다. 0 이 많으면 캐싱이 무의미하다는 신호다';

CREATE TABLE IF NOT EXISTS ai_call_log (
    log_id         BIGSERIAL PRIMARY KEY,
    feature        VARCHAR(30) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    emp_id         BIGINT,
    approval_id    BIGINT,
    input_tokens   INT,
    output_tokens  INT,
    latency_ms     INT,
    success_yn     CHAR(1)     NOT NULL,
    error_msg      VARCHAR(500),
    called_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_call_log             IS 'AI 호출 로그. 성공과 실패를 모두 남긴다';
COMMENT ON COLUMN ai_call_log.success_yn  IS 'Y/N. 실패도 기록해야 폴백이 얼마나 발동하는지 알 수 있다';
COMMENT ON COLUMN ai_call_log.error_msg   IS '실패 사유. 500자로 자른다 — 스택트레이스를 통째로 넣지 않는다';
COMMENT ON COLUMN ai_call_log.latency_ms  IS '호출에 걸린 시간. 타임아웃 값을 조정할 근거가 된다';

-- emp_id / approval_id 에 FK 를 걸지 않는 이유:
-- 로그는 원본이 지워져도 남아야 한다. FK 를 걸면 사원 삭제가 로그 삭제를 강요하고,
-- 그러면 "누가 언제 무엇을 호출했는가"의 이력이 사라진다.
CREATE INDEX IF NOT EXISTS idx_ai_log_feature   ON ai_call_log (feature, called_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_log_approval  ON ai_call_log (approval_id);
CREATE INDEX IF NOT EXISTS idx_ai_cache_feature ON ai_result_cache (feature, prompt_version);
