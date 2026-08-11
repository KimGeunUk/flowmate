package com.flowmate.approval.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalBoxCounts;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalHistory;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.approval.mapper.LeaveRequestMapper;
import com.flowmate.attendance.domain.LeaveBalance;
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
    private final LeaveRequestMapper leaveRequestMapper;

    public ApprovalQueryService(ApprovalDocMapper docMapper,
                               ApprovalLineMapper lineMapper,
                               ApprovalHistoryMapper historyMapper,
                               LeaveRequestMapper leaveRequestMapper) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.historyMapper = historyMapper;
        this.leaveRequestMapper = leaveRequestMapper;
    }

    /**
     * 문서 하나. **기안자이거나 결재선에 있는 사람만** 볼 수 있다.
     *
     * URL 인가로는 막을 수 없는 권한이다 — 문서마다 볼 수 있는 사람이 다르다.
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

    /**
     * LEAVE 문서의 연차 신청 확장 정보 (연차 맥락 패널).
     * LEAVE 가 아니거나 정합성이 깨져 확장 행이 없으면 null - 호출자(LeaveContextService)
     * 가 null 을 패널 자체를 감추라는 신호로 쓴다. 권한 검사는 이 메서드 앞에서
     * findDoc 이 이미 했다고 가정한다(approval 모듈 밖의 ai 모듈이 이 메서드만
     * 단독으로 부르지 않도록, LeaveContextService 가 항상 findDoc 뒤에만 호출한다).
     */
    @Transactional(readOnly = true)
    public LeaveRequest findLeaveRequest(Long approvalId) {
        return leaveRequestMapper.findByApprovalId(approvalId);
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

    /**
     * 탭 4종의 건수. 검색 조건과 무관하게 항상 전체 기준이다
     * (이유는 ApprovalBoxCounts 주석).
     */
    @Transactional(readOnly = true)
    public ApprovalBoxCounts countBoxTabs(Long empId) {
        return docMapper.countBoxTabs(empId);
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

    /**
     * 신청 일수가 잔여를 넘는지 — 기안 화면의 안내용 경고 판정이다.
     *
     * ★ 이 판정은 우회 가능한 안내일 뿐이다. 상신과 승인 사이에 다른
     * 문서가 승인되어 잔여가 줄 수 있으므로, 권위 있는 검사는 승인 시점에
     * leave_balance 를 잠근 뒤 다시 한다. 여기서 true 가 나와도 저장을
     * 막지 않는다 — 막으면 기안자가 정당한 사유로 잔여를 초과 신청하는 것(무급
     * 처리 등 후속 처리가 있는 경우)까지 원천 차단하게 된다.
     */
    public boolean exceedsBalance(LeaveRequest leaveRequest, LeaveBalance balance) {
        if (leaveRequest == null || leaveRequest.getDays() == null || balance == null) {
            return false;
        }
        return leaveRequest.getDays().compareTo(balance.getRemainingDays()) > 0;
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
