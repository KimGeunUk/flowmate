package com.flowmate.approval.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.mapper.ApprovalAttachmentMapper;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.approval.service.ApprovalService;
import com.flowmate.org.security.LoginEmployee;

@Controller
@RequestMapping("/approval")
public class ApprovalWriteController {

    private final ApprovalService approvalService;
    private final ApprovalQueryService queryService;
    private final ApprovalAttachmentMapper attachmentMapper;

    public ApprovalWriteController(ApprovalService approvalService, ApprovalQueryService queryService,
                                   ApprovalAttachmentMapper attachmentMapper) {
        this.approvalService = approvalService;
        this.queryService = queryService;
        this.attachmentMapper = attachmentMapper;
    }

    /** 새 기안 또는 임시저장 문서 수정 */
    @GetMapping("/write")
    public String writeForm(@RequestParam(required = false) Long approvalId,
                            @AuthenticationPrincipal LoginEmployee loginEmployee,
                            Model model) {
        ApprovalForm form = new ApprovalForm();
        if (approvalId != null) {
            // var 를 쓰지 않는다 — 설계서 §3 이 Java 8 에 없는 문법을 최소화하라고 했다
            ApprovalDoc doc = queryService.findDoc(approvalId, loginEmployee.getEmpId());
            form.setApprovalId(doc.getApprovalId());
            form.setDocType(doc.getDocType());
            form.setTitle(doc.getTitle());
            form.setContent(doc.getContent());
            form.setAmount(doc.getAmount());
            model.addAttribute("doc", doc);
            model.addAttribute("lines", queryService.findLines(approvalId));
            model.addAttribute("attachments", attachmentMapper.findByApprovalId(approvalId));
        }
        model.addAttribute("form", form);
        model.addAttribute("docTypes", DocType.ALL);
        return "approval/write";
    }

    /** 임시저장. 저장 후 같은 화면으로 돌아와 결재선을 보여준다 */
    @PostMapping("/draft")
    public String saveDraft(@ModelAttribute ApprovalForm form,
                           @AuthenticationPrincipal LoginEmployee loginEmployee) {
        Long id = approvalService.saveDraft(form, loginEmployee.getEmpId());
        return "redirect:/approval/write?approvalId=" + id;
    }
}
