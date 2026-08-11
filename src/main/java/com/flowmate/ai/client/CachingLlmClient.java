package com.flowmate.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.AiResultCache;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mapper.AiResultCacheMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * ★ 캐시 키에 promptVersion 을 넣는 이유:
 *   프롬프트를 고쳤는데 캐시가 옛 결과를 돌려주면, 프롬프트를 고친 사람은
 *   "왜 안 바뀌지"를 한참 디버깅하게 된다. 버전이 키에 있으면 자동으로 무효화된다.
 *
 * 사전점검(PREFLIGHT)과 기안 제안(DRAFT_HINT)은 캐시하지 않는다 — 둘 다 내용을 고친 뒤
 * 다시 부르는 것이 정상 동작이라, 캐시가 맞으면 오히려 옛 답이 나온다.
 *
 * 체인의 가장 바깥이다. 히트하면 마스킹도 실제 API 호출도 일어나지 않으므로
 * 비용이 0이다 - 단, 그러려면 이 데코레이터가 MaskingLlmClient 보다 바깥에 있어야
 * 한다. 뒤집히면 캐시 테이블에 원문이 그대로 저장된다 (LlmChainIT 가 이 순서를 단정한다).
 *
 * ★ 기능별 TTL: 요약은 완료된 문서가 바뀌지 않으므로 무기한, 연차 맥락은 팀 부재
 *   현황이 시시각각 바뀌므로 1시간이다. {@link #TTL_BY_FEATURE} 에 없는 feature 는
 *   무기한으로 본다. 만료 판정은 created_at 하나로 하고, 만료된 행은 미스와 같은
 *   경로(위임 → 재저장)를 탄다.
 */
public class CachingLlmClient implements LlmClient {

    /** 고쳐서 다시 부르는 것이 정상인 기능 - 캐시가 맞으면 옛 답이 나온다 */
    private static final Set<String> NEVER_CACHED =
            Set.of(AiFeature.PREFLIGHT, AiFeature.DRAFT_HINT);

    private static final Map<String, Duration> TTL_BY_FEATURE =
            Map.of(AiFeature.LEAVE_CONTEXT, Duration.ofHours(1));

    private final LlmClient delegate;
    private final AiResultCacheMapper cacheMapper;
    private final String modelKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param modelKey 이 캐시를 채우는 제공자의 신원. {@code LlmConfig} 가
     *                 {@code ai.enabled}·{@code ai.provider}·{@code ai.model} 로 만든다.
     *                 캐시 키에 들어가므로, 제공자를 바꾸면 옛 결과가 자동으로 무효가 된다.
     */
    public CachingLlmClient(LlmClient delegate, AiResultCacheMapper cacheMapper, String modelKey) {
        this.delegate = delegate;
        this.cacheMapper = cacheMapper;
        this.modelKey = modelKey;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        if (NEVER_CACHED.contains(request.getFeature())) {
            return delegate.complete(request);
        }

        String cacheKey = computeCacheKey(request);
        AiResultCache cached = cacheMapper.findByCacheKey(cacheKey);
        if (cached != null && !isExpired(cached)) {
            cacheMapper.incrementHitCount(cacheKey);
            return Optional.of(toResponse(cached));
        }

        Optional<LlmResponse> result = delegate.complete(request);
        // ★ cached != null && isExpired 인 경로에서는 이미 존재하는 cache_key 를
        //   다시 저장한다(같은 입력이므로 해시가 같다) - store() 가 부르는
        //   AiResultCacheMapper.insert 는 그래서 갱신(upsert)까지 해야 한다
        //   (AiResultCacheMapper.xml 의 ON CONFLICT 참고). 만료 전 무기한 캐시
        //   경로(SUMMARY)에서는 cached 가 애초에 null 이 아니면 이 줄까지 오지
        //   않으므로(위 if 에서 이미 반환) 영향이 없다.
        result.ifPresent(response -> store(cacheKey, request, response));
        return result;
    }

    /**
     * TTL 이 없는 feature(예: SUMMARY)는 절대 만료되지 않는다. TTL 이 있는데
     * created_at 이 없다면(방어적 - 정상 경로에서는 DB 기본값 NOW() 가 항상 채운다)
     * 만료로 보지 않는다 - 알 수 없는 상태를 캐시 미스가 아닌 히트 쪽으로 두는 것이
     * "이상하면 사용자에게 옛 답을 준다"보다 안전하다(캐시 미스는 재호출·재과금으로
     * 이어질 뿐 오답으로 이어지지 않는다는 뜻은 아니지만, 이 경우는 데이터 정합성
     * 문제이지 TTL 로 고칠 문제가 아니다).
     */
    private boolean isExpired(AiResultCache cached) {
        Duration ttl = TTL_BY_FEATURE.get(cached.getFeature());
        if (ttl == null) {
            return false;
        }
        if (cached.getCreatedAt() == null) {
            return false;
        }
        return cached.getCreatedAt().plus(ttl).isBefore(LocalDateTime.now());
    }

    private void store(String cacheKey, LlmRequest request, LlmResponse response) {
        AiResultCache row = new AiResultCache();
        row.setCacheKey(cacheKey);
        row.setFeature(request.getFeature());
        row.setPromptVersion(request.getPromptVersion());
        row.setResultJson(toJson(response));
        row.setModel(response.getModel());
        row.setInputTokens(response.getInputTokens());
        row.setOutputTokens(response.getOutputTokens());
        cacheMapper.insert(row);
    }

    private LlmResponse toResponse(AiResultCache cached) {
        try {
            return objectMapper.readValue(cached.getResultJson(), LlmResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("캐시된 AI 결과를 읽을 수 없습니다: " + cached.getCacheKey(), e);
        }
    }

    private String toJson(LlmResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 결과를 캐시용 JSON 으로 변환할 수 없습니다", e);
        }
    }

    /**
     * 키를 이루는 다섯 조각은 모두 "이것이 달라지면 결과도 달라진다"는 값이다.
     *
     * ★ outputType — 입력이 같아도 반환 타입이 다르면 결과의 JSON 모양이 다르다.
     *   키에 없으면 먼저 캐시된 옛 모양이 그대로 나오고, 화면은 새 필드를 읽다가
     *   예외 없이 null 을 받는다. 전체 스키마 대신 클래스의 정규화된 이름만 쓴다 -
     *   필드 변경은 보통 promptVersion 을 함께 올리는 흐름을 동반하므로, 여기서는
     *   기능 간 충돌만 막으면 된다. simpleName 이 아니라 getName() 인 것은 서로
     *   다른 패키지에 같은 이름의 POJO 가 생겨도 충돌하지 않게 하기 위해서다.
     *
     * ★ modelKey — 같은 프롬프트라도 누가 답했느냐에 따라 결과가 다르다. 이것이
     *   없으면 ai.enabled 를 켜도 FakeLlmClient 가 만들어 둔 고정 응답이 영원히
     *   나온다(claude ↔ gemini 를 바꾸는 경우도 같다). ai_result_cache 에 model
     *   컬럼은 있는데 키에는 없던 것이 원래 상태였다.
     */
    private String computeCacheKey(LlmRequest request) {
        String outputTypeName = request.getOutputType() != null
                ? request.getOutputType().getName()
                : "TEXT";
        String raw = request.getFeature() + ":" + request.getPromptVersion() + ":"
                + modelKey + ":" + outputTypeName + ":" + request.getPrompt();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }
}
