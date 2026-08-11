package com.flowmate.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final Long KWAK = 18L;   // 곽수빈

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ★ "오늘 아직 출근하지 않았다"를 테스트가 직접 만든다.
     *
     *   원래는 시드에 오늘 근태가 없다는 것에 기대고 있었다. 그런데 이 DB 는
     *   앱과 공유하는 것이라, **누구든 화면에서 출근 버튼을 한 번 누르면**
     *   그 뒤로 이 클래스 세 건이 전부 깨진다. 실제로 컨테이너 동작을 확인하다
     *   곽수빈으로 근태 행이 하나 생겼고 그 즉시 깨졌다.
     *
     *   클래스가 @Transactional 이므로 이 삭제도 테스트가 끝나면 함께 롤백된다 —
     *   진짜 데이터는 건드리지 않으면서 조건만 확보한다.
     *
     *   같은 교훈이 이 프로젝트에서 두 번째다(LlmConfigTest 는 환경변수에
     *   의존했다). 테스트가 환경을 관찰하는 대신 조건을 통제해야 한다.
     */
    @BeforeEach
    void clearTodaysAttendance() {
        jdbcTemplate.update("DELETE FROM attendance WHERE emp_id = ? AND work_date = ?",
                KWAK, LocalDate.now());
    }

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
