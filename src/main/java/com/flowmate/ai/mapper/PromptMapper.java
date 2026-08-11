package com.flowmate.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code ai_prompt} 테이블 조회 (커스터마이징 지점 4의 두 번째 구현이 쓴다,
 * {@code (feature, version)} 이 PK다 - File 구현
 * ({@code classpath:prompts/{feature}.{version}.txt})과 같은 키 규약을 그대로 쓴다.
 */
@Mapper
public interface PromptMapper {

    /** feature+version 으로 프롬프트 본문을 조회한다. 없으면 null(예외 판정은 호출자 몫) */
    String findBody(@Param("feature") String feature, @Param("version") String version);
}
