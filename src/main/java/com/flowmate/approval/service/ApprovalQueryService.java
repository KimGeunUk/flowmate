package com.flowmate.approval.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalHistory;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;
import com.flowmate.common.web.Page;

/**
 * 결재 조회. 쓰기와 분리한 이유는 트랜잭션 속성이 다르고(readOnly),
 * 문서 접근 권한 검사가 조회에만 필요하기 때문이다.
 */
@Service
public class ApprovalQueryService {

    private final ApprovalDocMapper docMapper;
    private final ApprovalLineMapper lineMapper;
    private final ApprovalHistoryMapper historyMapper;

    public ApprovalQueryService(ApprovalDocMapper docMapper,
                               ApprovalLineMapper lineMapper,
                               ApprovalHistoryMapper historyMapper) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.historyMapper = historyMapper;
    }

    /**
     * 문서 하나. **기안자이거나 결재선에 있는 사람만** 볼 수 있다.
     *
     * URL 인가로는 막을 수 없는 권한이다 — 문서마다 볼 수 있는 사람이 다르다 (설계서 §6.1).
     */
    @Transactional(readOnly = true)
    public ApprovalDoc findDoc(Long approvalId, Long viewerId) {
        ApprovalDoc doc = docMapper.findById(approvalId);
        if (doc == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        if (!canView(doc, approvalId, viewerId)) {
            throw new ApprovalAccessDeniedException("이 문서를 볼 권한이 없습니다");
        }
        return doc;
    }

    @Transactional(readOnly = true)
    public List<ApprovalLine> findLines(Long approvalId) {
        return lineMapper.findByApprovalId(approvalId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> findHistories(Long approvalId) {
        return historyMapper.findByApprovalId(approvalId);
    }

    private boolean canView(ApprovalDoc doc, Long approvalId, Long viewerId) {
        if (Objects.equals(doc.getDrafterId(), viewerId)) {
            return true;
        }
        for (ApprovalLine line : lineMapper.findByApprovalId(approvalId)) {
            if (Objects.equals(line.getApproverId(), viewerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 내 결재함. 건수를 먼저 읽고 요청 페이지를 실제 마지막 페이지로 보정한 뒤 목록을 읽는다.
     *
     * 보정하지 않으면 startPage 가 endPage 보다 커져 pagination.jsp 의
     * c:forEach 가 예외 없이 링크를 0개 그린다 — 페이징이 조용히 죽는다.
     * Phase 1 의 EmployeeService 와 같은 이유, 같은 순서다.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalDoc> searchBox(ApprovalSearchCond cond) {
        long totalCount = docMapper.countBox(cond);

        int totalPages = Page.totalPagesOf(totalCount, cond.getSize());
        if (cond.getPage() > totalPages) {
            cond.setPage(totalPages);
        }

        List<ApprovalDoc> content = docMapper.searchBox(cond);
        return new Page<>(content, cond.getPage(), cond.getSize(), totalCount);
    }

    /** 지금 이 사람이 처리할 차례인가 */
    public boolean isMyTurn(ApprovalDoc doc, List<ApprovalLine> lines, Long viewerId) {
        if (!ApprovalStatus.PENDING.equals(doc.getStatus())) {
            return false;
        }
        for (ApprovalLine line : lines) {
            if (line.isCurrent() && Objects.equals(line.getApproverId(), viewerId)) {
                return true;
            }
        }
        return false;
    }

    /** 회수 버튼을 보여줄지. 판정 규칙은 도메인 객체와 같아야 하므로 그대로 옮긴다 */
    public boolean canCancel(ApprovalDoc doc, Long viewerId) {
        if (!Objects.equals(doc.getDrafterId(), viewerId)) {
            return false;
        }
        if (ApprovalStatus.DRAFT.equals(doc.getStatus())) {
            return true;
        }
        return ApprovalStatus.PENDING.equals(doc.getStatus()) && doc.getCurrentStep() <= 1;
    }
}
