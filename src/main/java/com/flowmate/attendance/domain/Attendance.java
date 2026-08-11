package com.flowmate.attendance.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일별 근태. UNIQUE(emp_id, work_date) — 하루에 한 행이다.
 *
 * 출퇴근 등록과 연차 반영(DefaultLeaveApplyService)이 같은
 * 테이블을 UPSERT 로 함께 쓴다. 오전에 출근을 찍고 오후에 반차를 내는 경우처럼
 * 연차 반영이 이미 있는 행을 만날 수 있다 — 그때는 check_in/check_out/
 * work_minutes 는 그대로 두고 status/note 만 바꾼다.
 */
public class Attendance implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long attId;
    private Long empId;
    private LocalDate workDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private Integer workMinutes;
    private Integer overtimeMinutes;
    private String status;
    private String note;

    public Long getAttId() {
        return attId;
    }

    public void setAttId(Long attId) {
        this.attId = attId;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalDateTime getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDateTime checkIn) {
        this.checkIn = checkIn;
    }

    public LocalDateTime getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDateTime checkOut) {
        this.checkOut = checkOut;
    }

    public Integer getWorkMinutes() {
        return workMinutes;
    }

    public void setWorkMinutes(Integer workMinutes) {
        this.workMinutes = workMinutes;
    }

    public Integer getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public void setOvertimeMinutes(Integer overtimeMinutes) {
        this.overtimeMinutes = overtimeMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    // ── 판정 (JSP 는 이 boolean/label 만 읽는다) ──────────

    /** 출근 기록이 있는가 */
    public boolean isCheckedIn() {
        return checkIn != null;
    }

    /** 퇴근 기록이 있는가 */
    public boolean isCheckedOut() {
        return checkOut != null;
    }

    /** 화면 표시용 상태 한글명 */
    public String getStatusLabel() {
        return AttendanceStatus.labelOf(status);
    }
}
