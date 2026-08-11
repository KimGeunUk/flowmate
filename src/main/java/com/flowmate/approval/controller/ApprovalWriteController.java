package com.flowmate.approval.controller;

import java.time.Year;

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
import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.approval.mapper.ApprovalAttachmentMapper;
import com.flowmate.approval.mapper.LeaveRequestMapper;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.approval.service.ApprovalService;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.LeaveType;
import com.flowmate.attendance.service.LeaveInquiryService;
import com.flowmate.config.AiProperties;
import com.flowmate.org.security.LoginEmployee;

@Controller
@RequestMapping("/approval")
public class ApprovalWriteController {

    private final ApprovalService approvalService;
    private final ApprovalQueryService queryService;
    private final ApprovalAttachmentMapper attachmentMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveInquiryService leaveInquiryService;
    private final AiProperties aiProperties;

    public ApprovalWriteController(ApprovalService approvalService, ApprovalQueryService queryService,
                                   ApprovalAttachmentMapper attachmentMapper,
                                   LeaveRequestMapper leaveRequestMapper,
                                   LeaveInquiryService leaveInquiryService,
                                   AiProperties aiProperties) {
        this.approvalService = approvalService;
        this.queryService = queryService;
        this.attachmentMapper = attachmentMapper;
        this.leaveRequestMapper = leaveRequestMapper;
        this.leaveInquiryService = leaveInquiryService;
        this.aiProperties = aiProperties;
    }

    /** 새 기안 또는 임시저장 문서 수정 */
    @GetMapping("/write")
    public String writeForm(@RequestParam(required = false) Long approvalId,
                            @AuthenticationPrincipal LoginEmployee loginEmployee,
                            Model model) {
        ApprovalForm form = new ApprovalForm();
        int year = Year.now().getValue();
        // 잔여는 docType 을 아직 고르지 않았어도 미리 보여준다 - 화면에서 LEAVE 를
        // 고르는 순간 바로 참고할 수 있어야 한다 (D4의 안내 계층).
        LeaveBalance leaveBalance = leaveInquiryService.findBalance(loginEmployee.getEmpId(), year);
        model.addAttribute("leaveBalance", leaveBalance);

        if (approvalId != null) {
            // var 를 쓰지 않는다 — Java 8 에 없는 문법은 최소화한다는 규약이다
            ApprovalDoc doc = queryService.findDoc(approvalId, loginEmployee.getEmpId());
            form.setApprovalId(doc.getApprovalId());
            form.setDocType(doc.getDocType());
            form.setTitle(doc.getTitle());
            form.setContent(doc.getContent());
            form.setAmount(doc.getAmount());
            model.addAttribute("doc", doc);
            model.addAttribute("lines", queryService.findLines(approvalId));
            model.addAttribute("attachments", attachmentMapper.findByApprovalId(approvalId));

            if (DocType.LEAVE.equals(doc.getDocType())) {
                LeaveRequest leaveRequest = leaveRequestMapper.findByApprovalId(approvalId);
                if (leaveRequest != null) {
                    form.setLeaveType(leaveRequest.getLeaveType());
                    form.setStartDate(leaveRequest.getStartDate());
                    form.setEndDate(leaveRequest.getEndDate());
                    form.setReason(leaveRequest.getReason());
                    model.addAttribute("leaveRequest", leaveRequest);
                    model.addAttribute("exceedsBalance", queryService.exceedsBalance(leaveRequest, leaveBalance));
                }
            }
        }
        model.addAttribute("form", form);
        model.addAttribute("docTypes", DocType.options());
        model.addAttribute("leaveTypes", LeaveType.options());
        // 사전점검 모달·스크립트 노출 여부(커스터마이징 지점 5) -
        // ApprovalBoxController.detail 의 aiSummaryEnabled 와 같은 목적이다. 꺼져
        // 있으면 write.jsp 가 모달 include 와 상신 가로채기 스크립트를 아예
        // 렌더링하지 않는다 - 상신 폼이 원래(사전점검이 아예 없던 시절과 같은)
        // 평범한 POST 로 동작한다.
        model.addAttribute("aiPreflightEnabled", aiProperties.getFeatures().isPreflight());
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
