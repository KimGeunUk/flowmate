package com.flowmate.org.controller;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        // serverTime 이 java.util.Date 인 이유: <fmt:formatDate> 가 java.time 타입을 받지 못한다.
        // 이 화면은 fmt 태그리브가 동작하는지 확인하는 용도까지 겸한다.
        model.addAttribute("serverTime", new Date());
        model.addAttribute("modules", List.of("전자결재", "근태관리"));
        return "home";
    }
}
