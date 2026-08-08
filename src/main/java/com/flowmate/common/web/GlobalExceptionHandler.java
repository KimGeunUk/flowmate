package com.flowmate.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;

/**
 * 결재 화면의 업무 예외를 화면에 맞는 형태로 바꾼다.
 *
 * basePackages 로 범위를 좁힌 이유:
 * IllegalArgumentException 과 IllegalStateException 은 JDK 전반에서 흔히 던져진다
 * (NumberFormatException 도 IllegalArgumentException 이다). 범위를 제한하지 않으면
 * 다른 모듈의 진짜 버그가 친절한 400/409 화면으로 덮여 500 으로 드러나지 않는다.
 */
@ControllerAdvice(basePackages = "com.flowmate.approval.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(ApprovalNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ApprovalNotFoundException e, Model model) {
        model.addAttribute("errorTitle", "문서를 찾을 수 없습니다");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }

    @ExceptionHandler(ApprovalAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(ApprovalAccessDeniedException e, Model model) {
        model.addAttribute("errorTitle", "권한이 없습니다");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }

    /** 도메인 객체가 거부한 상태 전이 */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalState(IllegalStateException e, Model model) {
        model.addAttribute("errorTitle", "처리할 수 없는 상태입니다");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }

    /** 잘못된 입력 (반려 유형 누락 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException e, Model model) {
        model.addAttribute("errorTitle", "입력을 확인해 주세요");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }
}
