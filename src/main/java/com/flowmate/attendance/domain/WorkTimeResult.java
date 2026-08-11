package com.flowmate.attendance.domain;

/**
 * WorkTimePolicy 가 출퇴근 시각을 판정한 결과. 불변 값 객체다.
 *
 * status 가 null 일 수 있다 — checkOut 이 없으면 판정을 유보한다는 뜻이다.
 * 퇴근 미등록은 ABSENT 가 아니다 - 호출자는 status 가
 * null 이면 기존 attendance 행의 상태를 덮어쓰지 않고 그대로 둔다.)
 */
public class WorkTimeResult {

    private final int workMinutes;
    private final int overtimeMinutes;
    private final String status;

    public WorkTimeResult(int workMinutes, int overtimeMinutes, String status) {
        this.workMinutes = workMinutes;
        this.overtimeMinutes = overtimeMinutes;
        this.status = status;
    }

    public int getWorkMinutes() {
        return workMinutes;
    }

    public int getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public String getStatus() {
        return status;
    }
}
