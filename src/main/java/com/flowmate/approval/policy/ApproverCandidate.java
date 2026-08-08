package com.flowmate.approval.policy;

/**
 * 결재선 정책이 판정에 쓰는 후보 1명. 불변 값 객체다.
 *
 * Employee 를 그대로 넘기지 않는 이유:
 * 정책이 필요한 것은 사원번호·부서·직급뿐이고, Employee 에는 passwordHash 가 있다.
 * 정책 단위 테스트에서 Employee 전체를 조립하는 것도 불필요하게 무겁다.
 */
public class ApproverCandidate {

    private final Long empId;
    private final String empName;
    private final Long deptId;
    private final int positionLevel;
    private final String positionName;

    public ApproverCandidate(Long empId, String empName, Long deptId,
                             int positionLevel, String positionName) {
        this.empId = empId;
        this.empName = empName;
        this.deptId = deptId;
        this.positionLevel = positionLevel;
        this.positionName = positionName;
    }

    public Long getEmpId() {
        return empId;
    }

    public String getEmpName() {
        return empName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public int getPositionLevel() {
        return positionLevel;
    }

    public String getPositionName() {
        return positionName;
    }
}
