package com.flowmate.approval.domain;

/**
 * 내 결재함 탭 4종의 건수.
 *
 * ★ 이 숫자에는 검색 조건(유형·검색어)을 적용하지 않는다.
 *
 *   배지가 답해야 하는 질문은 "내가 처리해야 할 것이 몇 건인가"이지 "지금
 *   검색 결과에 몇 건이 걸렸는가"가 아니다. 검색어를 넣을 때마다 배지가
 *   흔들리면 원래 질문에 답하지 못한다.
 *
 *   목록 위의 건수(Page.totalCount)는 반대로 검색을 따라가야 맞다 —
 *   두 숫자는 서로 다른 질문에 답하므로 서로 다르게 움직이는 것이 정상이고,
 *   화면은 그 둘을 구분해서 보여야 한다(box.jsp).
 */
public class ApprovalBoxCounts {

    private long drafted;
    private long pending;
    private long done;
    private long rejected;

    /** 탭 코드로 건수를 꺼낸다. JSP 가 아니라 BoxTab.options() 가 쓴다 */
    public long countOf(String tab) {
        if (BoxTab.PENDING.equals(tab)) {
            return pending;
        }
        if (BoxTab.DONE.equals(tab)) {
            return done;
        }
        if (BoxTab.REJECTED.equals(tab)) {
            return rejected;
        }
        return drafted;
    }

    /**
     * 지금 내가 무언가 해야 할 것이 있는가.
     *
     * 대기(내 차례라 남이 나를 기다린다) + 반려(내가 고쳐 다시 올려야 한다).
     * 기안·완료는 지나간 기록이라 여기 들어가지 않는다.
     */
    public boolean isHasTodo() {
        return pending > 0 || rejected > 0;
    }

    public long getDrafted() {
        return drafted;
    }

    public void setDrafted(long drafted) {
        this.drafted = drafted;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getDone() {
        return done;
    }

    public void setDone(long done) {
        this.done = done;
    }

    public long getRejected() {
        return rejected;
    }

    public void setRejected(long rejected) {
        this.rejected = rejected;
    }
}
