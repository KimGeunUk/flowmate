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
import java.util.Optional;

/**
 * ★ 캐시 키에 promptVersion 을 넣는 이유 (설계서 §6.4.3):
 *   프롬프트를 고쳤는데 캐시가 옛 결과를 돌려주면, 프롬프트를 고친 사람은
 *   "왜 안 바뀌지"를 한참 디버깅하게 된다. 버전이 키에 있으면 자동으로 무효화된다.
 *
 * 사전점검(PREFLIGHT)은 캐시하지 않는다 — 수정 후 재실행이 정상 동작이다.
 *
 * 체인의 가장 바깥이다. 히트하면 마스킹도 실제 API 호출도 일어나지 않으므로
 * 비용이 0이다 - 단, 그러려면 이 데코레이터가 MaskingLlmClient 보다 바깥에 있어야
 * 한다. 뒤집히면 캐시 테이블에 원문이 그대로 저장된다 (LlmChainIT 가 이 순서를 단정한다).
 */
public class CachingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final AiResultCacheMapper cacheMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CachingLlmClient(LlmClient delegate, AiResultCacheMapper cacheMapper) {
        this.delegate = delegate;
        this.cacheMapper = cacheMapper;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        if (AiFeature.PREFLIGHT.equals(request.getFeature())) {
            return delegate.complete(request);
        }

        String cacheKey = computeCacheKey(request);
        AiResultCache cached = cacheMapper.findByCacheKey(cacheKey);
        if (cached != null) {
            cacheMapper.incrementHitCount(cacheKey);
            return Optional.of(toResponse(cached));
        }

        Optional<LlmResponse> result = delegate.complete(request);
        result.ifPresent(response -> store(cacheKey, request, response));
        return result;
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

    private String computeCacheKey(LlmRequest request) {
        // ★ outputType 을 키에 넣는 이유 (계획서 5 D3, Phase 3 부채 A2 - "잊으면 조용히
        //   틀린다"고 적어 둔 그 부채): 구조화 출력이 생기면 입력이 같아도 outputType 이
        //   다르면 결과의 모양(JSON 스키마)이 다르다. outputType 이 키에 없으면 먼저 캐시된
        //   옛 모양을 그대로 돌려주고, 화면은 새 필드를 읽다가 예외 없이 null 을 받는다.
        //
        //   전체 스키마를 해시하지 않고 클래스의 정규화된 이름(getName, 패키지 포함)만
        //   쓰는 이유: POJO 필드가 바뀌는 것은 코드 변경이고, 코드 변경은 보통 프롬프트
        //   버전을 함께 올리는 정상 흐름을 동반한다(promptVersion 이 이미 키에 있다).
        //   클래스 이름은 그 흐름을 방해하지 않으면서 "이 캐시 항목이 어떤 기능의 어떤
        //   반환 타입인지"만 구분해 기능 간 충돌을 막는 용도다 - 필드 단위 변경 추적까지
        //   흉내내려는 것이 아니다. simpleName 이 아니라 패키지를 포함한 getName() 을
        //   쓰는 이유는 서로 다른 패키지에 같은 단순 이름의 POJO 가 생겨도(예: 두 기능이
        //   각자 Result 라는 이름을 쓰는 경우) 캐시 키가 충돌하지 않게 하기 위해서다.
        String outputTypeName = request.getOutputType() != null
                ? request.getOutputType().getName()
                : "TEXT";
        String raw = request.getFeature() + ":" + request.getPromptVersion() + ":"
                + outputTypeName + ":" + request.getPrompt();
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
