package com.flowmate.ai.domain;

import java.io.Serializable;

/**
 * 과거 반려 이력을 {@code reason_category} 별로 집계한 결과 (
 * 집계 결과다.
 *
 * ★ 이 클래스가 담는 것은 유형과 건수뿐이다 - {@code approval_reject_history.reason_text}
 * (반려 원문, 사람 이름과 금액이 들어 있다)는 여기 담지 않는다. {@code PreflightService}
 * 가 이 집계를 만드는 쿼리 자체가 {@code reason_category} 컬럼만 읽어 오므로,
 * reason_text 는 애초에 애플리케이션 메모리에 들어오지 않는다 - "보내지 않는다"를
 * 코드 리뷰로 지키는 대신 쿼리 설계로 구조적으로 불가능하게 만든 것이다.
 */
public class RejectPattern implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String reasonCategory;
    private final int count;

    public RejectPattern(String reasonCategory, int count) {
        this.reasonCategory = reasonCategory;
        this.count = count;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public int getCount() {
        return count;
    }
}
