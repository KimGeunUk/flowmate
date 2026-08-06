package com.flowmate.org.controller;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.flowmate.common.service.DbHealthService;

@Controller
public class HomeController {

    private final DbHealthService dbHealthService;

    public HomeController(DbHealthService dbHealthService) {
        this.dbHealthService = dbHealthService;
    }

    @GetMapping("/")
    public String home(Model model) {
        // serverTime 이 java.util.Date 인 이유: <fmt:formatDate> 가 java.time 타입을 받지 못한다.
        model.addAttribute("serverTime", new Date());
        model.addAttribute("modules", List.of("전자결재", "근태관리"));
        model.addAttribute("dbInfo", dbHealthService.findDbInfo());
        return "home";
    }
}
