package com.flowmate.org.controller;

import java.util.Date;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.service.AttendanceService;
import com.flowmate.common.service.DbHealthService;
import com.flowmate.org.security.LoginEmployee;

@Controller
public class HomeController {

    private final DbHealthService dbHealthService;
    private final AttendanceService attendanceService;

    public HomeController(DbHealthService dbHealthService, AttendanceService attendanceService) {
        this.dbHealthService = dbHealthService;
        this.attendanceService = attendanceService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginEmployee loginEmployee, Model model) {
        // serverTime 이 java.util.Date 인 이유: <fmt:formatDate> 가 java.time 타입을 받지 못한다.
        model.addAttribute("serverTime", new Date());
        model.addAttribute("modules", List.of("전자결재", "근태관리"));
        model.addAttribute("dbInfo", dbHealthService.findDbInfo());

        // 출퇴근 버튼 두 개(계획서 4 Task 3). 상태 판정(체크인/체크아웃 여부)은
        // 여기서 boolean 으로 끝내 JSP 에는 값 비교를 남기지 않는다 —
        // Phase 2 가 세운 규칙("state judgments live in the service, JSP reads
        // only booleans")을 홈 화면에도 그대로 적용한다.
        Attendance today = attendanceService.findToday(loginEmployee.getEmpId());
        model.addAttribute("todayAttendance", today);
        model.addAttribute("checkedIn", today != null && today.getCheckIn() != null);
        model.addAttribute("checkedOut", today != null && today.getCheckOut() != null);
        return "home";
    }
}
