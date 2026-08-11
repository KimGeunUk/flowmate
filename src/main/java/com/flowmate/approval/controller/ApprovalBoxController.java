package com.flowmate.approval.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.ai.feature.LeaveContextService;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.mapper.ApprovalAttachmentMapper;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.config.AiProperties;
import com.flowmate.org.security.LoginEmployee;

@Controller
@RequestMapping("/approval")
public class ApprovalBoxController {

    private final ApprovalQueryService queryService;
    private final ApprovalAttachmentMapper attachmentMapper;
    private final LeaveContextService leaveContextService;
    private final AiProperties aiProperties;

    public ApprovalBoxController(ApprovalQueryService queryService,
                                 ApprovalAttachmentMapper attachmentMapper,
                                 LeaveContextService leaveContextService,
                                 AiProperties aiProperties) {
        this.queryService = queryService;
        this.attachmentMapper = attachmentMapper;
        this.leaveContextService = leaveContextService;
        this.aiProperties = aiProperties;
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
        model.addAttribute("docTypes", DocType.options());
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
        model.addAttribute("attachments", attachmentMapper.findByApprovalId(approvalId));
        model.addAttribute("rejectReasons", RejectReason.ALL);
        model.addAttribute("myTurn", queryService.isMyTurn(doc, lines, viewerId));
        model.addAttribute("canCancel", queryService.canCancel(doc, viewerId));

        // 연차 맥락 패널(계획서 5 Task 4) - LEAVE 문서에만, 이미 위에서 통과한
        // 조회 권한을 그대로 재사용해서 만든다. LEAVE 가 아니면 서비스가
        // null 을 돌려주므로 호출 자체는 조건 없이 해도 안전하지만, LEAVE가
        // 아닌 문서에서 불필요한 조회(leaveInquiryService/attendanceQueryService
        // 까지 타는 것은 아니고 findDoc 재조회 정도)를 피하려고 여기서도 한 번
        // 걸러 둔다. ai.features.leave-context 가 꺼져 있으면 서비스 자체가
        // null 을 돌려주므로(LeaveContextService.build 참고) 여기서 따로 걸러낼
        // 것이 없다 - JSP 는 이미 null 여부만 본다(계획서 5 Task 7).
        if (DocType.LEAVE.equals(doc.getDocType())) {
            model.addAttribute("leaveContext", leaveContextService.build(approvalId, viewerId));
        }

        // AI 요약(계획서 5 Task 3) 영역 자체의 노출 여부(계획서 5 Task 7, 커스터마이징
        // 지점 5) - SummaryService.summarize() 가 empty 를 돌려주는 것과는 별개다.
        // empty 는 "AI 가 지금 실패했다"는 뜻으로 안내 문구를 보여주는 경로(D8)이고,
        // 이 플래그가 꺼진 것은 "이 기능 자체가 없다"는 뜻이라 영역이 아예 나타나지
        // 않아야 한다(설계서 요구: 오류가 아니라 부재) - 그래서 detail.jsp 가 이
        // 플래그를 별도로 보고 <section class="ai-summary-box"> 와 그 AJAX 스크립트
        // 자체를 렌더링할지 말지를 결정한다.
        model.addAttribute("aiSummaryEnabled", aiProperties.getFeatures().isSummary());
        return "approval/detail";
    }
}
