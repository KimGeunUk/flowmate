package com.flowmate.approval.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.org.security.LoginEmployee;

@Controller
@RequestMapping("/approval")
public class ApprovalBoxController {

    private final ApprovalQueryService queryService;

    public ApprovalBoxController(ApprovalQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 내 결재함. empId 는 화면이 보내는 값을 쓰지 않고 로그인 주체에서 강제로 덮어쓴다 —
     * 요청 파라미터로 남의 결재함을 볼 수 있으면 안 된다.
     */
    @GetMapping("/box")
    public String box(@ModelAttribute("cond") ApprovalSearchCond cond,
                      @AuthenticationPrincipal LoginEmployee loginEmployee,
                      Model model) {
        cond.setEmpId(loginEmployee.getEmpId());
        model.addAttribute("paging", queryService.searchBox(cond));
        model.addAttribute("docTypes", DocType.ALL);
        return "approval/box";
    }

    @GetMapping("/{approvalId}")
    public String detail(@PathVariable Long approvalId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee,
                         Model model) {
        Long viewerId = loginEmployee.getEmpId();
        ApprovalDoc doc = queryService.findDoc(approvalId, viewerId);
        List<ApprovalLine> lines = queryService.findLines(approvalId);

        model.addAttribute("doc", doc);
        model.addAttribute("lines", lines);
        model.addAttribute("histories", queryService.findHistories(approvalId));
        model.addAttribute("rejectReasons", RejectReason.ALL);
        model.addAttribute("myTurn", queryService.isMyTurn(doc, lines, viewerId));
        model.addAttribute("canCancel", queryService.canCancel(doc, viewerId));
        return "approval/detail";
    }
}
