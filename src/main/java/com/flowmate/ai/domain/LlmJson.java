package com.flowmate.ai.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 이 돌려준 JSON 을 우리 타입으로 읽을 때 쓰는 ObjectMapper.
 *
 * ★ 모르는 필드를 무시한다 — 실측으로 확인한 뒤 넣은 설정이다.
 *
 *   요약 프롬프트는 summary 와 keyFacts 만 요구하는데, Gemini 가 응답에 title 을
 *   하나 더 붙여 보냈다:
 *
 *     {"title": "개발용 모니터 4대 구매",
 *      "summary": [...], "keyFacts": {...}}
 *
 *   Jackson 은 기본값이 FAIL_ON_UNKNOWN_PROPERTIES=true 라 이 응답 전체를
 *   거부했다. 그 결과 요약 기능이 항상 503 이 됐다 — 모델은 우리가 원한 두
 *   필드를 **정확히 채워 보냈는데도** 덤으로 붙인 필드 하나 때문에 전부
 *   버려진 것이다.
 *
 *   프롬프트로 "필드를 더 붙이지 말라"고 적을 수는 있고 실제로 적어 두었지만,
 *   그것을 지킬 거라고 믿고 설계할 수는 없다. 모델이 무엇을 덧붙일지는
 *   우리가 통제하는 값이 아니다. 그래서 "우리가 읽는 필드만 읽고 나머지는
 *   무시한다"를 파서 쪽 규약으로 못박는다.
 *
 *   반대 방향(우리가 기대하는 필드가 없는 경우)은 그대로 둔다 - 그 필드는
 *   null 이 되고, 화면은 이미 null 을 다룰 수 있다(요약이 비면 안내 문구).
 *   즉 "덜 온 것"은 부분적으로 쓰고 "더 온 것"은 무시한다.
 *
 * 설계서 §6.4.3 의 폴백 원칙과 같은 정신이다 - 완벽하지 않은 응답을 이유로
 * 기능 전체를 죽이지 않는다.
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
