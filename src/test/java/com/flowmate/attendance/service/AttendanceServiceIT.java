package com.flowmate.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.mapper.AttendanceMapper;

/**
 * 출퇴근 등록 통합 테스트 (계획서 4 Task 3). 통합 3건:
 * 출근→퇴근 정상 / 중복 출근 거부 / 출근 없는 퇴근 거부.
 *
 * @Transactional 로 감싼다 — 이 클래스의 테스트는 실패 시 롤백 여부 자체를
 * 검증하지 않으므로(ApprovalServiceLeaveApplyRollbackIT 와 달리) 테스트
 * 트랜잭션 안에서 결과를 바로 읽어도 함정이 없다. 테스트가 만든 행은
 * 메서드가 끝나면 자동 롤백된다.
 */
@SpringBootTest
@Transactional
class AttendanceServiceIT {

    private static final Long KWAK = 18L;   // 곽수빈 — 오늘 근태 행이 시드에 없다

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Test
    @DisplayName("출근 후 퇴근하면 WorkTimePolicy 가 판정한 근무시간·상태가 저장된다")
    void checkInThenCheckOut() {
        attendanceService.checkIn(KWAK);
        attendanceService.checkOut(KWAK);

        Attendance attendance = attendanceMapper.findByEmpIdAndWorkDate(KWAK, LocalDate.now());
        assertThat(attendance).isNotNull();
        assertThat(attendance.getCheckIn()).isNotNull();
        assertThat(attendance.getCheckOut()).isNotNull();
        // checkOut 이 있으므로 WorkTimePolicy 가 반드시 status 를 채운다(null 이 아니다) —
        // 실행 시각에 따라 NORMAL/LATE/EARLY_LEAVE 중 무엇이 나올지는 달라지므로
        // 값 자체는 고정하지 않는다.
        assertThat(attendance.getStatus()).isNotNull();
        assertThat(attendance.getWorkMinutes()).isNotNull();
        assertThat(attendance.getOvertimeMinutes()).isNotNull();
    }

    @Test
    @DisplayName("같은 날 두 번 출근하면 예외가 난다")
    void duplicateCheckInRejected() {
        attendanceService.checkIn(KWAK);

        assertThatThrownBy(() -> attendanceService.checkIn(KWAK))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("출근 기록 없이 퇴근하면 예외가 난다")
    void checkOutWithoutCheckInRejected() {
        assertThatThrownBy(() -> attendanceService.checkOut(KWAK))
                .isInstanceOf(IllegalStateException.class);

        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, LocalDate.now())).isNull();
    }
}
