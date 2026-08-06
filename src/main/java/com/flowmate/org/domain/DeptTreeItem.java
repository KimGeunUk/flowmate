package com.flowmate.org.domain;

/**
 * 조직도 조회 결과 한 행. 재귀 CTE 가 계산한 depth 를 그대로 담는다.
 *
 * 중첩 구조(children)를 만들지 않는 이유:
 * 화면이 depth 값을 CSS 클래스(dept-tree__item--depth3)로 바꿔 들여쓰기를 표현하므로
 * Java 쪽에서 트리를 조립할 필요가 없다.
 */
public class DeptTreeItem {

    private Long deptId;
    private Long parentDeptId;
    private String deptName;
    private String deptCode;
    private int depth;
    private int empCount;

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getParentDeptId() {
        return parentDeptId;
    }

    public void setParentDeptId(Long parentDeptId) {
        this.parentDeptId = parentDeptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getEmpCount() {
        return empCount;
    }

    public void setEmpCount(int empCount) {
        this.empCount = empCount;
    }
}
