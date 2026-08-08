package com.flowmate.approval.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.flowmate.approval.service.ApprovalService;
import com.flowmate.org.security.LoginEmployee;

/**
 * 결재 처리 액션. 모두 POST 다 — 상태를 바꾸는 요청을 GET 으로 두면
 * 브라우저 프리페치나 크롤러가 결재를 처리해 버릴 수 있다.
 */
@Controller
@RequestMapping("/approval")
public class ApprovalActionController {

    private final ApprovalService approvalService;

    public ApprovalActionController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{approvalId}/submit")
    public String submit(@PathVariable Long approvalId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.submit(approvalId, loginEmployee.getEmpId());
        return "redirect:/approval/" + approvalId;
    }

    @PostMapping("/{approvalId}/approve")
    public String approve(@PathVariable Long approvalId,
                          @RequestParam(required = false) String comment,
                          @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.approve(approvalId, loginEmployee.getEmpId(), comment);
        return "redirect:/approval/" + approvalId;
    }

    @PostMapping("/{approvalId}/reject")
    public String reject(@PathVariable Long approvalId,
                         @RequestParam String reasonCategory,
                         @RequestParam(required = false) String reasonText,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.reject(approvalId, loginEmployee.getEmpId(), reasonCategory, reasonText);
        return "redirect:/approval/" + approvalId;
    }

    @PostMapping("/{approvalId}/cancel")
    public String cancel(@PathVariable Long approvalId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.cancel(approvalId, loginEmployee.getEmpId());
        return "redirect:/approval/" + approvalId;
    }
}
