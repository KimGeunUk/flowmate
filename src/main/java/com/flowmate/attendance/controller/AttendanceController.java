package com.flowmate.attendance.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.attendance.service.AttendanceService;
import com.flowmate.org.security.LoginEmployee;

/**
 * 출퇴근 등록 (계획서 4 Task 3). 화면은 홈에 버튼 두 개를 얹는 수준으로
 * 최소화한다 — 별도의 출퇴근 전용 화면은 만들지 않는다.
 *
 * 둘 다 POST 다 — 상태를 바꾸는 요청을 GET 으로 두면 브라우저 프리페치나
 * 크롤러가 출퇴근을 등록해 버릴 수 있다(ApprovalActionController 와 같은 이유).
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

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
}
