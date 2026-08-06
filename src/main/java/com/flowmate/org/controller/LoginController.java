package com.flowmate.org.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /** POST /login 은 Spring Security 필터가 처리한다. 여기는 화면만 담당한다. */
    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }
}
