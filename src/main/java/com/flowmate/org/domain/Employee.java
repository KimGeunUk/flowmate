package com.flowmate.org.domain;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 사원.
 *
 * Serializable 인 이유: 로그인 사용자 정보가 이 객체를 감싼 채 HTTP 세션에 올라간다.
 * Tomcat 이 재시작하거나 세션을 디스크로 내릴 때 직렬화가 필요하다.
 *
 * deptName / positionName / positionLevel 은 department · position 조인 결과를 담는
 * 조회 표시용 파생 필드다. employee 테이블의 컬럼이 아니다.
 */
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long empId;
    private String empNo;
    private String empName;
    private Long deptId;
    private Long positionId;
    private String email;
    private LocalDate hireDate;
    private String passwordHash;
    private String role;
    private String useYn;

    // 조인 결과 (조회 표시용)
    private String deptName;
    private String positionName;
    private int positionLevel;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public int getPositionLevel() {
        return positionLevel;
    }

    public void setPositionLevel(int positionLevel) {
        this.positionLevel = positionLevel;
    }

    /** 재직 중인지 */
    public boolean isActive() {
        return "Y".equals(this.useYn);
    }
}
