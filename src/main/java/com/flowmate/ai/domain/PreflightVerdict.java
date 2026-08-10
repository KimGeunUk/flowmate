package com.flowmate.ai.domain;

/**
 * 사전 점검 판정 2종 (설계서 §6.4.6). {@code PreflightResult.verdict} /
 * {@code ai_preflight_result.verdict} 컬럼에 그대로 저장되는 값이다.
 *
 * DocType/RejectReason/AiFeature 와 같은 상수 클래스 관례를 따른다 - 매직 문자열을
 * 여러 클래스에 흩지 않는다.
 */
public final class PreflightVerdict {

    /** 지적할 것이 없다 - 모달 없이 바로 상신 */
    public static final String PASS = "PASS";
    /** 지적 사항이 있다 - 모달을 띄운다 */
    public static final String WARN = "WARN";

    private PreflightVerdict() {
    }
}
