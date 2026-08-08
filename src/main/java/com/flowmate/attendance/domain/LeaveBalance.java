package com.flowmate.attendance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 연차 잔여 (설계서 §5.4).
 *
 * 잔여일수를 컬럼으로 두지 않는다 — getRemainingDays() 가 (granted - used) 로
 * 매번 계산한다. 저장된 잔여값은 부여·사용 두 값과 어긋날 수 있고, 어긋나면
 * 어느 쪽이 맞는지 아무도 모르게 된다 (설계서 §5.4의 "불일치 방지").
 */
public class LeaveBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long empId;
    private int year;
    private BigDecimal grantedDays;
    private BigDecimal usedDays;
    private LocalDateTime updatedAt;

    /** 잔여일수 = 부여일수 - 사용일수. 저장하지 않고 매번 계산한다 */
    public BigDecimal getRemainingDays() {
        return grantedDays.subtract(usedDays);
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public BigDecimal getGrantedDays() {
        return grantedDays;
    }

    public void setGrantedDays(BigDecimal grantedDays) {
        this.grantedDays = grantedDays;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public void setUsedDays(BigDecimal usedDays) {
        this.usedDays = usedDays;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
