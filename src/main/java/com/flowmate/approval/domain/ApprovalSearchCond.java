package com.flowmate.approval.domain;

/**
 * 내 결재함 검색 조건. Phase 1 의 EmployeeSearchCond 와 같은 규약을 따른다.
 * 보정을 setter 에서 끝내 잘못된 값이 SQL 까지 흘러가지 않게 한다.
 */
public class ApprovalSearchCond {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    /** drafted | pending | done | rejected */
    private String tab = "drafted";
    private Long empId;
    private String docType;
    private String keyword;
    private int page = 1;
    private int size = DEFAULT_SIZE;

    public String getTab() {
        return tab;
    }

    /** 알 수 없는 탭 값은 기본 탭으로 떨어뜨린다. 화면에서 넘어온 값을 신뢰하지 않는다 */
    public void setTab(String tab) {
        if ("pending".equals(tab) || "done".equals(tab) || "rejected".equals(tab)) {
            this.tab = tab;
            return;
        }
        this.tab = "drafted";
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = (docType == null || docType.trim().isEmpty()) ? null : docType.trim();
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
    }

    /** LIKE 패턴용. 원본은 getKeyword() 가 돌려주므로 폼에 되돌릴 때는 그것을 쓴다 */
    public String getKeywordEscaped() {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size < 1) {
            this.size = DEFAULT_SIZE;
            return;
        }
        this.size = Math.min(size, MAX_SIZE);
    }

    public int getLimit() {
        return size;
    }

    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
