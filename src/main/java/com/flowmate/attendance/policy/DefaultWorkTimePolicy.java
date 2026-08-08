package com.flowmate.attendance.policy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.WorkTimeResult;

public class DefaultWorkTimePolicy implements WorkTimePolicy {

    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final LocalTime END_TIME = LocalTime.of(18, 0);
    private static final int LUNCH_MINUTES = 60;
    private static final int CONTRACTED_MINUTES = 8 * 60;

    @Override
    public WorkTimeResult evaluate(LocalDateTime checkIn, LocalDateTime checkOut, LocalDate date) {
        if (checkOut == null) {
            // 출근은 했으니 ABSENT 로 적으면 거짓이 된다. 판정을 유보한다 —
            // status=null 은 호출자에게 "기존 상태를 덮어쓰지 마라"는 신호다.
            // 화면은 이 상태에서 "퇴근 미등록"을 보여준다.
            return new WorkTimeResult(0, 0, null);
        }

        int rawMinutes = (int) Duration.between(checkIn, checkOut).toMinutes();
        int workMinutes = Math.max(0, rawMinutes - LUNCH_MINUTES);
        int overtimeMinutes = Math.max(0, workMinutes - CONTRACTED_MINUTES);

        boolean late = checkIn.toLocalTime().isAfter(START_TIME);
        boolean earlyLeave = checkOut.toLocalTime().isBefore(END_TIME);

        // 지각과 조퇴가 같은 날 동시에 일어날 수 있는데 상태는 하나뿐이다.
        // LATE 를 우선한다 — "늦게 와서 일찍 갔다"에서 더 무거운 규율 위반으로
        // 본다는 임의의 결정이다. 다른 우선순위로 바꾸려면 이 판단부터 바꿔야 한다.
        String status;
        if (late) {
            status = AttendanceStatus.LATE;
        } else if (earlyLeave) {
            status = AttendanceStatus.EARLY_LEAVE;
        } else {
            status = AttendanceStatus.NORMAL;
        }

        return new WorkTimeResult(workMinutes, overtimeMinutes, status);
    }
}
