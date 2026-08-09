package com.flowmate.attendance.policy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.WorkTimeResult;

/**
 * ★ 두 번째 구현 — 자율출퇴근제. 지각·조퇴 개념이 없다. 총 근무시간만 본다.
 *
 * DefaultWorkTimePolicy 와 다른 점:
 *   - 출근·퇴근 시각 자체는 보지 않는다. 몇 시에 왔든 총 근무시간이 소정
 *     8시간을 채웠으면 정상이다
 *   - 점심시간 1시간은 그대로 뺀다 — 근로기준법상 휴게시간은 근무형태와
 *     무관하게 실근무시간에서 빠지는 사실이라, 이 부분은 두 정책이 같다.
 *     정책이 달라지는 지점은 "그 시간을 언제 채웠는가"를 보느냐 마느냐다
 */
public class FlexWorkTimePolicy implements WorkTimePolicy {

    private static final int LUNCH_MINUTES = 60;
    private static final int CONTRACTED_MINUTES = 8 * 60;

    @Override
    public WorkTimeResult evaluate(LocalDateTime checkIn, LocalDateTime checkOut, LocalDate date) {
        if (checkOut == null) {
            return new WorkTimeResult(0, 0, null);
        }

        int rawMinutes = (int) Duration.between(checkIn, checkOut).toMinutes();
        int workMinutes = Math.max(0, rawMinutes - LUNCH_MINUTES);
        int overtimeMinutes = Math.max(0, workMinutes - CONTRACTED_MINUTES);

        String status = workMinutes >= CONTRACTED_MINUTES ? AttendanceStatus.NORMAL : AttendanceStatus.EARLY_LEAVE;

        return new WorkTimeResult(workMinutes, overtimeMinutes, status);
    }
}
