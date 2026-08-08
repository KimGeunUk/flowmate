package com.flowmate.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;

/**
 * 업무 예외를 화면에 맞는 형태로 바꾼다.
 *
 * 권한 예외가 500 으로 보이면 데모에서 사고처럼 보인다.
 * IllegalStateException 도 처리하는 이유: 도메인 객체의 전이 거부가 그 타입이다.
 */
@ControllerAdvice
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
