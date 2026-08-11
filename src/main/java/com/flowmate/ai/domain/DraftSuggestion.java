package com.flowmate.ai.domain;

import java.io.Serializable;

/**
 * 모델이 채우는 부분. 필드가 {@code draft} 하나뿐이다.
 *
 * ★ 응답 타입({@link DraftHint})과 일부러 분리했다. 근거 건수({@code basedOn})는
 *   서버가 집계한 값이어야 하는데, 그 필드가 모델의 출력 타입에 들어 있으면
 *   두 가지가 잘못될 수 있다.
 *
 *   1) 모델이 그 자리에 임의의 숫자를 채워 보낸다. 서버가 덮어쓰면 되지만,
 *      "덮어쓰는 것을 잊지 않는다"에 기대는 설계가 된다.
 *   2) 더 나쁜 쪽 - 모델이 채워 보내는 순간 파싱이 죽는다. {@link RejectPattern}
 *      은 불변이라 기본 생성자가 없어서 Jackson 이 역직렬화할 수 없고, 그러면
 *      제안 전체가 버려진다. 실제로 테스트에서 그렇게 깨졌다.
 *
 *   모델이 채울 수 있는 타입에 그 필드를 아예 두지 않으면 둘 다 생기지 않는다.
 */
public class DraftSuggestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private String draft;

    public String getDraft() {
        return draft;
    }

    public void setDraft(String draft) {
        this.draft = draft;
    }
}
