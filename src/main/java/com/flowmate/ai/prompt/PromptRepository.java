package com.flowmate.ai.prompt;

/**
 * 기능별 프롬프트를 조회한다.
 */
public interface PromptRepository {

    /** 없으면 IllegalArgumentException. 조용히 빈 문자열을 돌려주지 않는다 — 프롬프트 없는 호출은 버그다 */
    String load(String feature, String version);
}
