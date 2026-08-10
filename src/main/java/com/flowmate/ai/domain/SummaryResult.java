package com.flowmate.ai.domain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 문서 요약 결과 (설계서 §6.4.5, 계획서 5 Task 3).
 *
 * {@code ClaudeLlmClient} 가 {@code outputConfig(SummaryResult.class)} 로 이 POJO 에서
 * JSON 스키마를 뽑아내고, 응답도 이 타입으로 강제한다(계획서 5 D2). 기본 생성자와
 * getter/setter 만으로 충분하다 - Jackson 이 그대로 읽고 쓴다.
 *
 * keyFacts 를 고정 필드(amount/period/counterparty)가 아니라 Map 으로 둔 이유:
 * 문서 유형마다 "결재 판단에 필요한 사실"이 다르다(설계서 §6.4.5 예시는 지출결의
 * 기준이다). GENERAL·CONTRACT 문서는 거래처가 없을 수 있고, 그런 문서에도 고정
 * 필드를 강제하면 프롬프트가 없는 값을 지어내라고 압박받는다. summary.v1.txt 가
 * "본문에 없으면 비워 두거나 생략하라"고 명시한 이유이기도 하다.
 */
public class SummaryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> summary;
    private Map<String, String> keyFacts = new LinkedHashMap<>();

    public List<String> getSummary() {
        return summary;
    }

    public void setSummary(List<String> summary) {
        this.summary = summary;
    }

    public Map<String, String> getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(Map<String, String> keyFacts) {
        this.keyFacts = keyFacts;
    }
}
