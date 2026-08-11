package com.flowmate.ai.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 사전 점검 구조화 출력.
 *
 * {@code LlmRequest.outputType} 에 이 클래스를 실어 보내면 {@code ClaudeLlmClient} 가
 * 이 POJO 에서 JSON 스키마를 뽑아 구조화 출력을 요청하고, 응답도 이 타입으로 강제한다
 * 이 클래스는 모델이 채울 필드만 갖는다 - resultId 같은 DB 행 식별자는
 * 여기 두지 않는다({@link PreflightRecord} 의 몫이다). 구조화 출력 스키마에 모델이
 * 채울 수 없는 필드(예: DB 가 나중에 매기는 PK)를 섞으면 모델이 그 값을 지어내려 하거나
 * null 로 채운 채 스키마만 지키는 무의미한 값이 나온다.
 *
 * findings 를 빈 리스트로 기본값을 두는 이유는 SummaryResult.keyFacts 와 같다 -
 * FakeLlmClient 가 기본 생성자로 만든 빈 인스턴스를 직렬화할 때도 null 이 아니라
 * 빈 배열이 나오게 한다.
 */
public class PreflightResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String verdict;
    private List<Finding> findings = new ArrayList<>();

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void setFindings(List<Finding> findings) {
        this.findings = findings;
    }
}
