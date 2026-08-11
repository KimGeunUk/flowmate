package com.flowmate.attendance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.calc.BusinessDayCalculator;
import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.LeaveApplyCommand;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.LeaveType;
import com.flowmate.attendance.domain.LeaveUsage;
import com.flowmate.attendance.mapper.AttendanceMapper;
import com.flowmate.attendance.mapper.LeaveBalanceMapper;
import com.flowmate.attendance.mapper.LeaveUsageMapper;

/**
 * ★★ 이 Phase의 척추.
 *
 * ApprovalService.approve() 의 트랜잭션 안에서 직접 호출된다 — Spring 이벤트가
 * 아니다. 어느 한쪽이 실패하면 전부 롤백되어야 "승인은 됐는데 연차가 안
 * 깎였다" 같은 사고를 막을 수 있기 때문이다. 이 판단은 README에도 기록한다.
 *
 * 순서가 중요하다:
 *   1) 이미 반영됐는지 조회 (D2 — 제약 위반을 잡지 않는다) → 있으면 조용히 return
 *   2) leave_balance 를 FOR UPDATE 로 잠그고 읽는다 (D3, 잠금 순서 approval_doc → leave_balance)
 *   3) 잔여 검사 — 부족하면 IllegalStateException (D4)
 *   4) used_days 갱신
 *   5) leave_usage 삽입
 *   6) 기간의 영업일마다 attendance UPSERT — status=LEAVE (반차는 HALF_LEAVE) (D6)
 */
@Service
public class DefaultLeaveApplyService implements LeaveApplyService {

    private final LeaveUsageMapper leaveUsageMapper;
    private final LeaveBalanceMapper leaveBalanceMapper;
    private final AttendanceMapper attendanceMapper;
    private final BusinessDayCalculator businessDayCalculator;

    public DefaultLeaveApplyService(LeaveUsageMapper leaveUsageMapper,
                                    LeaveBalanceMapper leaveBalanceMapper,
                                    AttendanceMapper attendanceMapper,
                                    BusinessDayCalculator businessDayCalculator) {
        this.leaveUsageMapper = leaveUsageMapper;
        this.leaveBalanceMapper = leaveBalanceMapper;
        this.attendanceMapper = attendanceMapper;
        this.businessDayCalculator = businessDayCalculator;
    }

    @Override
    @Transactional
    public void apply(LeaveApplyCommand command) {
        // 1) D2 — 제약 위반을 잡지 않는다. 먼저 조회해서 분기한다.
        //    ApprovalService.approve() 가 이미 approval_doc 행을 FOR UPDATE 로
        //    잠근 상태이므로, 같은 문서를 두 트랜잭션이 동시에 이 지점을
        //    통과할 수 없다 — 조회 후 삽입이 그 잠금 안에서는 안전하다.
        if (leaveUsageMapper.existsByApprovalId(command.getApprovalId())) {
            return;
        }

        int year = command.getStartDate().getYear();

        // 2) D3 — 잠금 순서는 approval_doc → leave_balance 로 고정한다.
        //    approve() 가 먼저 문서를 잠그고 그 안에서 이 조회를 부르므로
        //    자연히 이 순서가 된다.
        LeaveBalance balance = leaveBalanceMapper.findForUpdate(command.getEmpId(), year);
        if (balance == null) {
            throw new IllegalStateException(
                    "연차 잔여 정보가 없습니다: empId=" + command.getEmpId() + " year=" + year);
        }

        // 3) D4 — 승인 시점의 권위 있는 검증이다. 기안 화면의 안내
        //    (ApprovalQueryService.exceedsBalance)는 우회 가능하지만, 잠금 안의
        //    이 검사는 우회할 수 없다. 상신과 승인 사이에 다른 문서가 승인되어
        //    잔여가 줄었을 수 있으므로 여기서 다시 확인한다.
        BigDecimal newUsedDays = balance.getUsedDays().add(command.getDays());
        if (newUsedDays.compareTo(balance.getGrantedDays()) > 0) {
            throw new IllegalStateException(
                    "연차 잔여가 부족합니다: empId=" + command.getEmpId()
                            + " year=" + year
                            + " 잔여=" + balance.getRemainingDays()
                            + " 신청=" + command.getDays());
        }

        // 4)
        leaveBalanceMapper.updateUsedDays(command.getEmpId(), year, newUsedDays);

        // 5)
        LeaveUsage usage = new LeaveUsage();
        usage.setEmpId(command.getEmpId());
        usage.setApprovalId(command.getApprovalId());
        usage.setLeaveType(command.getLeaveType());
        usage.setStartDate(command.getStartDate());
        usage.setEndDate(command.getEndDate());
        usage.setDays(command.getDays());
        usage.setAppliedAt(LocalDateTime.now());
        leaveUsageMapper.insert(usage);

        // 6) D6 — 영업일마다 UPSERT. ON CONFLICT 가 DB 안에서 해결하므로
        //    여기서도 제약 위반을 잡지 않는다.
        String status = LeaveType.isHalfDay(command.getLeaveType())
                ? AttendanceStatus.HALF_LEAVE
                : AttendanceStatus.LEAVE;
        String note = LeaveType.labelOf(command.getLeaveType());

        List<LocalDate> businessDays =
                businessDayCalculator.businessDaysBetween(command.getStartDate(), command.getEndDate());
        for (LocalDate day : businessDays) {
            Attendance attendance = new Attendance();
            attendance.setEmpId(command.getEmpId());
            attendance.setWorkDate(day);
            attendance.setStatus(status);
            attendance.setNote(note);
            attendanceMapper.upsertForLeave(attendance);
        }
    }
}
