package com.flowmate.attendance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.WorkTimeResult;
import com.flowmate.attendance.mapper.AttendanceMapper;
import com.flowmate.attendance.policy.WorkTimePolicy;

/**
 * 출퇴근 등록 (계획서 4 Task 3).
 *
 * checkIn()/checkOut() 은 각각 "그날 하루" 단위로 동작한다 — 사원 1명이
 * 하루에 attendance 행을 1개만 갖는다(UNIQUE(emp_id, work_date)).
 */
@Service
public class AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final WorkTimePolicy workTimePolicy;

    public AttendanceService(AttendanceMapper attendanceMapper, WorkTimePolicy workTimePolicy) {
        this.attendanceMapper = attendanceMapper;
        this.workTimePolicy = workTimePolicy;
    }

    /**
     * 출근을 등록한다. 하루 1회다 — 그날 행이 이미 있으면 기존 값을 조용히
     * 유지하지 않고 예외를 던진다. 두 번 누른 사용자가 "됐나?"를 모르는
     * 것보다 "이미 등록됐습니다"를 보는 것이 낫다(계획서 4 Task 3).
     */
    @Transactional
    public void checkIn(Long empId) {
        LocalDate today = LocalDate.now();
        if (attendanceMapper.findByEmpIdAndWorkDate(empId, today) != null) {
            throw new IllegalStateException("이미 출근이 등록되었습니다: " + today);
        }

        Attendance attendance = new Attendance();
        attendance.setEmpId(empId);
        attendance.setWorkDate(today);
        attendance.setCheckIn(LocalDateTime.now());
        attendance.setWorkMinutes(0);
        attendance.setOvertimeMinutes(0);

        // ★ 함정 (계획서 4 Task 3) — 여기서 WorkTimePolicy 를 부르지 않는다.
        // WorkTimePolicy.evaluate() 는 checkOut 이 없으면 status=null 을 돌려준다
        // (지각·조퇴 여부를 아직 판정할 수 없어 유보한다는 뜻, WorkTimeResult 주석
        // 참조). 그런데 attendance.status 컬럼은 NOT NULL 이라 그 null 을 그대로
        // INSERT 하면 제약 위반으로 트랜잭션이 죽는다. 그래서 출근 시점에는
        // 정책과 무관한 임시값 NORMAL 을 쓴다 — "지각/조퇴 등 규율 위반을
        // 미리 단정하지 않는다"는 뜻으로 가장 중립적인 값이고, 퇴근 시점에
        // WorkTimePolicy 가 실제 판정으로 덮어쓴다(checkOut() 참조).
        attendance.setStatus(AttendanceStatus.NORMAL);
        attendanceMapper.insertCheckIn(attendance);
    }

    /**
     * 퇴근을 등록한다. 출근 기록이 없으면 예외를 던진다 — 출근 없는 퇴근은
     * 근태 데이터를 조용히 오염시킨다.
     */
    @Transactional
    public void checkOut(Long empId) {
        LocalDate today = LocalDate.now();
        Attendance attendance = attendanceMapper.findByEmpIdAndWorkDate(empId, today);
        if (attendance == null || attendance.getCheckIn() == null) {
            throw new IllegalStateException("출근 기록이 없습니다: " + today);
        }

        LocalDateTime checkOutTime = LocalDateTime.now();
        WorkTimeResult result = workTimePolicy.evaluate(attendance.getCheckIn(), checkOutTime, today);

        attendance.setCheckOut(checkOutTime);
        attendance.setWorkMinutes(result.getWorkMinutes());
        attendance.setOvertimeMinutes(result.getOvertimeMinutes());
        // checkOut 을 지금 막 채웠으므로 result.getStatus() 는 null 이 아니다.
        attendance.setStatus(result.getStatus());
        attendanceMapper.updateCheckOut(attendance);
    }

    /** 홈 화면의 "오늘 상태" 표시용. 그날 행이 없으면 null */
    @Transactional(readOnly = true)
    public Attendance findToday(Long empId) {
        return attendanceMapper.findByEmpIdAndWorkDate(empId, LocalDate.now());
    }
}
