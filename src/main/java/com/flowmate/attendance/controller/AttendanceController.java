package com.flowmate.attendance.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.flowmate.attendance.service.AttendanceQueryService;
import com.flowmate.attendance.service.AttendanceService;
import com.flowmate.org.security.LoginEmployee;

/**
 * 출퇴근 등록(Task 3) + 근태 조회 화면(Task 7).
 *
 * 조회 두 화면 모두 대상(empId/deptId)을 요청 파라미터로 받지 않는다 —
 * 로그인 주체(loginEmployee)에서만 가져온다. 그래서 URL 을 조작해 남의
 * 근태나 다른 부서를 보는 경로 자체가 없다(계획서 4 Task 7 권한 규칙).
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceQueryService attendanceQueryService;

    public AttendanceController(AttendanceService attendanceService,
                                AttendanceQueryService attendanceQueryService) {
        this.attendanceService = attendanceService;
        this.attendanceQueryService = attendanceQueryService;
    }

    /**
     * 출근 등록. POST 인 이유는 ApprovalActionController 와 같다 — 상태를 바꾸는
     * 요청을 GET 으로 두면 브라우저 프리페치나 크롤러가 출퇴근을 등록해 버릴 수 있다.
     */
    @PostMapping("/check-in")
    public String checkIn(@AuthenticationPrincipal LoginEmployee loginEmployee) {
        attendanceService.checkIn(loginEmployee.getEmpId());
        return "redirect:/";
    }

    @PostMapping("/check-out")
    public String checkOut(@AuthenticationPrincipal LoginEmployee loginEmployee) {
        attendanceService.checkOut(loginEmployee.getEmpId());
        return "redirect:/";
    }

    /** 내 근태(월간). ?ym=2026-08 로 이동한다. 없거나 형식이 틀리면 이번 달로 본다 */
    @GetMapping("/my")
    public String my(@RequestParam(required = false) String ym,
                     @AuthenticationPrincipal LoginEmployee loginEmployee,
                     Model model) {
        YearMonth yearMonth = parseYearMonth(ym);
        model.addAttribute("summary", attendanceQueryService.findMyMonthly(loginEmployee.getEmpId(), yearMonth));
        addMonthNavAttributes(model, yearMonth);
        return "attendance/my";
    }

    /** 부서 근태 현황(월간). 본인 부서와 하위 부서까지만 대상이다 */
    @GetMapping("/dept")
    public String dept(@RequestParam(required = false) String ym,
                       @AuthenticationPrincipal LoginEmployee loginEmployee,
                       Model model) {
        YearMonth yearMonth = parseYearMonth(ym);
        model.addAttribute("rows", attendanceQueryService.findDeptMonthly(loginEmployee.getDeptId(), yearMonth));
        addMonthNavAttributes(model, yearMonth);
        return "attendance/dept";
    }

    private void addMonthNavAttributes(Model model, YearMonth yearMonth) {
        model.addAttribute("yearMonth", yearMonth.toString());
        model.addAttribute("prevYm", yearMonth.minusMonths(1).toString());
        model.addAttribute("nextYm", yearMonth.plusMonths(1).toString());
    }

    private YearMonth parseYearMonth(String ym) {
        if (ym == null || ym.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(ym);
        } catch (DateTimeParseException e) {
            return YearMonth.now();
        }
    }
}
