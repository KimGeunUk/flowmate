package com.flowmate.ai.client;

import com.flowmate.ai.domain.AiResultCache;
import com.flowmate.ai.mapper.AiResultCacheMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link AiResultCacheMapper} 의 손으로 만든 테스트 대역.
 *
 * Mockito 를 새로 들이지 않는다는 이 프로젝트의 관례를 따른다 - 이 클래스 하나면
 * CachingLlmClient 가 미스/히트/저장을 정확히 수행하는지 실제 DB 없이 검증할 수 있다.
 */
class FakeAiResultCacheMapper implements AiResultCacheMapper {

    private final Map<String, AiResultCache> store = new HashMap<>();
    private int insertCount;
    private int incrementHitCountCalls;

    @Override
    public AiResultCache findByCacheKey(String cacheKey) {
        return store.get(cacheKey);
    }

    @Override
    public void insert(AiResultCache cache) {
        insertCount++;
        store.put(cache.getCacheKey(), cache);
    }

    @Override
    public void incrementHitCount(String cacheKey) {
        incrementHitCountCalls++;
    }

    int getInsertCount() {
        return insertCount;
    }

    int getIncrementHitCountCalls() {
        return incrementHitCountCalls;
    }
}
