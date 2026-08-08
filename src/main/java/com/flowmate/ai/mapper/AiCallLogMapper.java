package com.flowmate.ai.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.flowmate.ai.domain.AiCallLog;

@Mapper
public interface AiCallLogMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다. 성공·실패 모두 이 메서드로 남긴다 */
    void insert(AiCallLog log);
}
