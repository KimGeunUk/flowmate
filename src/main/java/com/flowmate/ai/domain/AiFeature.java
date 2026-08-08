package com.flowmate.ai.domain;

/**
 * AI 기능 3종 (설계서 §6.4). {@code ai_result_cache.feature} / {@code ai_call_log.feature}
 * 컬럼에 그대로 저장되는 값이다.
 */
public final class AiFeature {

    /** 문서 요약 */
    public static final String SUMMARY = "SUMMARY";
    /** 상신 전 사전점검 */
    public static final String PREFLIGHT = "PREFLIGHT";
    /** 연차 신청 맥락 보강 */
    public static final String LEAVE_CONTEXT = "LEAVE_CONTEXT";

    private AiFeature() {
    }
}
