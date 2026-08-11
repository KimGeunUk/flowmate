package com.flowmate.ai.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 기안 본문 제안 한 건. 화면으로 나가는 응답 타입이다.
 *
 * ★ 모델은 이 타입을 채우지 않는다. 모델이 채우는 것은 {@link DraftSuggestion}
 *   (draft 하나뿐)이고, {@code basedOn} 은 서버가 집계한 값을 여기서 붙인다.
 *   사전점검은 프롬프트로 "제시된 숫자를 그대로 옮겨 적으라"고 지시하고 평가셋으로
 *   확인하는 방식인데, 여기서는 모델이 그 필드에 손댈 수 있는 경로 자체를 없앴다 -
 *   이 숫자가 기능의 신뢰 근거이므로 모델이 만질 이유가 없다.
 */
public class DraftHint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 모델이 제안한 본문 초안 */
    private String draft;

    /** 이 제안이 근거로 삼은 과거 반려 유형과 건수. 서버가 채운다 */
    private List<RejectPattern> basedOn = new ArrayList<>();

    public String getDraft() {
        return draft;
    }

    public void setDraft(String draft) {
        this.draft = draft;
    }

    public List<RejectPattern> getBasedOn() {
        return basedOn;
    }

    public void setBasedOn(List<RejectPattern> basedOn) {
        this.basedOn = basedOn;
    }
}
