package com.flowmate.org.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면 모델에 loginEmployee 를 넣는다. common/header.jsp 가 이 값을 쓴다.
 *
 * 화면마다 Controller 가 직접 담지 않는 이유:
 * 화면이 늘어날 때마다 같은 코드를 복사해야 하고, 한 곳을 빼먹으면
 * 그 화면에서만 상단 사용자 정보가 조용히 사라진다.
 *
 * 미인증 요청에서는 principal 이 null 이라 header.jsp 의 c:if 가 블록을 건너뛴다.
 */
@ControllerAdvice
public class LoginEmployeeAdvice {

    @ModelAttribute("loginEmployee")
    public LoginEmployee loginEmployee(@AuthenticationPrincipal LoginEmployee principal) {
        return principal;
    }
}
