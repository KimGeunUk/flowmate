package com.flowmate.ai.domain;

/**
 * 기안 본문 제안 요청. 화면이 지금 입력 중인 값을 그대로 보낸다.
 *
 * ★ approvalId 를 받지 않는다. 제안은 **저장하기 전에** 필요하므로 아직 문서가
 *   없을 수 있다. 그래서 화면이 보낸 값과 로그인 사원의 부서만으로 처리한다 -
 *   남의 문서를 들여다볼 경로가 애초에 생기지 않으므로 문서 열람 권한 검사도
 *   필요 없다(SummaryService/PreflightService 는 저장된 문서를 읽으므로
 *   ApprovalQueryService.findDoc 을 태운다).
 */
public class DraftHintCommand {

    private String docType;
    private String title;
    /** 지금까지 쓴 내용. 비어 있어도 된다 - 그때는 처음부터 제안한다 */
    private String content;

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
