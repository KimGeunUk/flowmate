package com.flowmate.ai.client;

/**
 * 구조화 출력 배선 테스트 전용 POJO. 실제 기능(SummaryResult, PreflightResult 등)은
 * 이후 5 의 몫이다 - 여기서는 "outputType 이 클래스라는 사실" 자체만 검증하면 되므로
 * 필드 하나짜리 최소 POJO 로 충분하다.
 */
public class SampleAiResult {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
