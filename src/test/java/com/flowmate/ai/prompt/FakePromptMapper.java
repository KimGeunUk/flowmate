package com.flowmate.ai.prompt;

import com.flowmate.ai.mapper.PromptMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link PromptMapper} 의 손으로 만든 테스트 대역 - Mockito 를 새로 들이지 않는다는
 * 이 프로젝트의 관례를 따른다(FakeAiResultCacheMapper 와 같은 이유).
 */
class FakePromptMapper implements PromptMapper {

    private final Map<String, String> bodies = new HashMap<>();
    private int findBodyCalls;

    @Override
    public String findBody(String feature, String version) {
        findBodyCalls++;
        return bodies.get(feature + "." + version);
    }

    void put(String feature, String version, String body) {
        bodies.put(feature + "." + version, body);
    }

    int getFindBodyCalls() {
        return findBodyCalls;
    }
}
