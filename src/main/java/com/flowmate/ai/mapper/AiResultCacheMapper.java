package com.flowmate.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.ai.domain.AiResultCache;

@Mapper
public interface AiResultCacheMapper {

    /** 캐시 키로 조회. 없으면 null */
    AiResultCache findByCacheKey(@Param("cacheKey") String cacheKey);

    /** 신규 캐시 저장. hit_count 는 DB 기본값 0 으로 시작한다 */
    void insert(AiResultCache cache);

    /** 캐시가 실제로 쓰였음을 기록한다 */
    void incrementHitCount(@Param("cacheKey") String cacheKey);
}
