package com.flowmate.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.ai.domain.AiResultCache;

@Mapper
public interface AiResultCacheMapper {

    /** 캐시 키로 조회. 없으면 null */
    AiResultCache findByCacheKey(@Param("cacheKey") String cacheKey);

    /**
     * 캐시 저장. cache_key 가 없으면 신규 INSERT(hit_count 는 DB 기본값 0),
     * 있으면(TTL 만료 후 갱신) UPDATE 로 덮어쓰고 hit_count 를
     * 0 으로 되돌린다 - {@code AiResultCacheMapper.xml} 의 {@code ON CONFLICT} 참고.
     */
    void insert(AiResultCache cache);

    /** 캐시가 실제로 쓰였음을 기록한다 */
    void incrementHitCount(@Param("cacheKey") String cacheKey);
}
