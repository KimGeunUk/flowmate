package com.flowmate.ai.client;

import com.flowmate.ai.domain.AiResultCache;
import com.flowmate.ai.mapper.AiResultCacheMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link AiResultCacheMapper} 의 손으로 만든 테스트 대역.
 *
 * Mockito 를 새로 들이지 않는다는 이 프로젝트의 관례를 따른다 - 이 클래스 하나면
 * CachingLlmClient 가 미스/히트/저장을 정확히 수행하는지 실제 DB 없이 검증할 수 있다.
 *
 * ★ insert() 가 put(덮어쓰기)인 이유(계획서 5 Task 7, TTL): 실제 매퍼의
 *   {@code ON CONFLICT DO UPDATE} 와 같은 upsert 동작을 흉내낸다 - 만료된 캐시를
 *   같은 cache_key 로 다시 저장하는 경로를 이 대역으로도 검증할 수 있어야 한다.
 *   createdAt 이 비어 있으면 지금 시각을 채우는 것도 실제 DB 의 {@code created_at
 *   TIMESTAMP DEFAULT NOW()} 를 흉내낸 것이다 - CachingLlmClient 는 이 필드를
 *   직접 채우지 않는다(DB 가 채운다는 전제이므로).
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
        if (cache.getCreatedAt() == null) {
            cache.setCreatedAt(LocalDateTime.now());
        }
        cache.setHitCount(0);
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

    /** TTL 테스트용 - 저장된 모든 항목의 createdAt 을 과거로 되돌려 만료를 흉내낸다 */
    void ageAllEntries(Duration amount) {
        for (AiResultCache cache : store.values()) {
            cache.setCreatedAt(cache.getCreatedAt().minus(amount));
        }
    }
}
