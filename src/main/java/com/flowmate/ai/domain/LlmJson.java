package com.flowmate.ai.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 이 돌려준 JSON 을 우리 타입으로 읽을 때 쓰는 ObjectMapper.
 *
 * ★ 모르는 필드를 무시한다. 요약 프롬프트는 summary·keyFacts 만 요구하는데
 * Gemini 가 title 을 하나 더 붙여 보냈고, Jackson 기본값
 * (FAIL_ON_UNKNOWN_PROPERTIES=true)이 그 응답을 통째로 거부해 요약이 항상 503 이
 * 됐다. 모델이 무엇을 덧붙일지는 우리가 통제하는 값이 아니므로 프롬프트로
 * 막는 대신 파서 쪽 규약으로 못박는다.
 *
 * 반대 방향(기대한 필드가 없는 경우)은 그대로 둔다 - null 이 되고 화면은 이미
 * null 을 다룬다. 덜 온 것은 부분적으로 쓰고 더 온 것은 무시한다.
 */
public final class LlmJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private LlmJson() {
    }

    /** LLM 응답 파싱 전용. 저장·직렬화에는 각자의 ObjectMapper 를 쓴다 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
