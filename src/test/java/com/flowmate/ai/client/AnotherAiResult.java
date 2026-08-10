package com.flowmate.ai.client;

/**
 * {@link SampleAiResult} 와 모양이 다른 두 번째 테스트 전용 POJO. A2 회귀 테스트
 * (같은 입력·같은 프롬프트 버전인데 outputType 만 다르면 캐시가 미스한다)에서
 * "다른 타입" 쪽으로 쓴다.
 */
public class AnotherAiResult {

    private int count;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
