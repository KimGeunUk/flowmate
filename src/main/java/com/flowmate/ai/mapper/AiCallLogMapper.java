package com.flowmate.ai.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.ai.domain.AiCallLog;

@Mapper
public interface AiCallLogMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다. 성공·실패 모두 이 메서드로 남긴다 */
    void insert(AiCallLog log);

    /**
     * {@code since} 이후(같은 시각 포함)의 호출 건수.
     *
     * ★ 성공·실패를 가리지 않고 센다. 실패한 호출도 API 를 이미 때린 것이라
     *   비용 방어 관점에서는 성공과 다르지 않다. {@code QuotaLlmClient} 가
     *   일일 상한을 판정할 때 쓴다.
     */
    long countSince(@Param("since") LocalDateTime since);
}
