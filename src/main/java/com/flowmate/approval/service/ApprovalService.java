package com.flowmate.approval.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalLimits;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.HistoryAction;
import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.RejectHistory;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.approval.mapper.LeaveRequestMapper;
import com.flowmate.approval.mapper.RejectHistoryMapper;
import com.flowmate.approval.policy.ApprovalLinePolicy;
import com.flowmate.approval.policy.ApproverCandidate;
import com.flowmate.attendance.domain.LeaveApplyCommand;
import com.flowmate.attendance.service.LeaveApplyService;
import com.flowmate.attendance.service.LeaveInquiryService;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;
import com.flowmate.org.service.DepartmentService;

/**
 * 결재 처리의 트랜잭션 경계.
 *
 * 상태 문자열을 직접 바꾸지 않는다 — ApprovalDoc 의 전이 메서드만 부른다.
 * 그래야 허용되지 않는 순서로 부르는 코드가 DB 에 닿기 전에 죽는다.
 */
@Service
public class ApprovalService {

    private final ApprovalDocMapper docMapper;
    private final ApprovalLineMapper lineMapper;
    private final ApprovalHistoryMapper historyMapper;
    private final RejectHistoryMapper rejectHistoryMapper;
    private final ApprovalLinePolicy linePolicy;
    private final DepartmentService departmentService;
    private final EmployeeMapper employeeMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveInquiryService leaveInquiryService;
    private final LeaveApplyService leaveApplyService;

    public ApprovalService(ApprovalDocMapper docMapper,
                           ApprovalLineMapper lineMapper,
                           ApprovalHistoryMapper historyMapper,
                           RejectHistoryMapper rejectHistoryMapper,
                           ApprovalLinePolicy linePolicy,
                           DepartmentService departmentService,
                           EmployeeMapper employeeMapper,
                           LeaveRequestMapper leaveRequestMapper,
                           LeaveInquiryService leaveInquiryService,
                           LeaveApplyService leaveApplyService) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.historyMapper = historyMapper;
        this.rejectHistoryMapper = rejectHistoryMapper;
        this.linePolicy = linePolicy;
        this.departmentService = departmentService;
        this.employeeMapper = employeeMapper;
        this.leaveRequestMapper = leaveRequestMapper;
        this.leaveInquiryService = leaveInquiryService;
        this.leaveApplyService = leaveApplyService;
    }

    /**
     * 임시저장. 신규면 만들고, approvalId 가 있으면 수정한다.
     *
     * 수정 시 결재선을 **지우고 다시 만든다.** 금액이나 유형이 바뀌면 정책 결과가 달라지는데
     * 기존 결재선을 남겨두면 화면에 보이는 결재선과 실제 규칙이 어긋난다.
     *
     * @return 저장된 문서의 approvalId
     */
    @Transactional
    public Long saveDraft(ApprovalForm form, Long actorId) {
        checkLengths(form);
        if (form.getApprovalId() == null) {
            return createDraft(form, actorId);
        }
        return updateDraft(form, actorId);
    }

    /**
     * 화면 maxlength 를 우회해 들어온 값을 여기서 막는다.
     *
     * ★ 없으면 DB 제약 위반이 그대로 올라와 500 이 된다 - 그 예외는
     *   DataIntegrityViolationException 이라 GlobalExceptionHandler 가 잡는
     *   IllegalArgumentException 계열도 아니어서 사용자에게 원인이 안 보인다.
     *   화면·DB·서버 세 곳이 같은 숫자를 봐야 한다(ApprovalLimits).
     */
    private void checkLengths(ApprovalForm form) {
        ApprovalLimits.check(form.getTitle(), ApprovalLimits.TITLE, "제목");
        ApprovalLimits.check(form.getContent(), ApprovalLimits.CONTENT, "본문");
        ApprovalLimits.check(form.getReason(), ApprovalLimits.REASON, "사유");
    }

    /**
     * 금액이 비어 있으면 0 으로 채운다.
     *
     * ★ approval_doc.amount 는 NOT NULL DEFAULT 0 인데, 매퍼가 NULL 을 명시적으로
     *   넣으면 DEFAULT 가 적용되지 않고 제약 위반으로 죽는다. 금액 칸이 없는
     *   유형(LEAVE·GENERAL·CONTRACT)을 화면에서 기안하면 그대로 500 이 났다.
     *
     *   통합 테스트가 이걸 못 잡은 이유: 테스트들이 전부 form.setAmount(ZERO) 를
     *   호출해서 null 경로를 한 번도 타지 않았다. 실제 HTTP 로 연차를 기안해 보고서야
     *   드러났다.
     *
     *   0 이 의미상으로도 맞다 — 결재선 정책이 금액으로 임원 결재를 붙일지 판단하는데
     *   (DefaultApprovalLinePolicy 의 300만원 규칙), 금액 없는 문서는 그 규칙에
     *   걸리지 않아야 한다.
     */
    private BigDecimal amountOrZero(ApprovalForm form) {
        return form.getAmount() == null ? BigDecimal.ZERO : form.getAmount();
    }

    /**
     * 저장할 본문. 연차 신청서는 **사유가 곧 본문**이다.
     *
     * ★ 이전에는 화면이 사유와 본문을 따로 물었는데, 사유(leave_request.reason)를
     *   읽는 곳이 기안 화면의 되채우기뿐이었다 - 결재자의 문서 상세에도, AI 요약·
     *   사전점검 프롬프트에도 들어가지 않았다. 그 둘은 approval_doc.content 만
     *   읽는다. 그래서 신청자가 사유를 적어도 아무에게도 닿지 않았고, 본문에 같은
     *   내용을 한 번 더 적어야만 실제로 전달됐다.
     *
     *   사유를 content 로 승격시키면 기존 경로가 전부 살아난다 - 문서 상세의 본문
     *   영역, AI 요약, 사전점검이 손대지 않아도 사유를 보게 된다. 그래서 화면에서는
     *   연차신청일 때 본문 칸을 감춘다(write.jsp).
     */
    private String contentOf(ApprovalForm form) {
        return DocType.LEAVE.equals(form.getDocType()) ? form.getReason() : form.getContent();
    }

    private Long createDraft(ApprovalForm form, Long actorId) {
        Employee drafter = requireEmployee(actorId);

        ApprovalDoc doc = new ApprovalDoc();
        doc.setDocType(form.getDocType());
        doc.setTitle(form.getTitle());
        doc.setContent(contentOf(form));
        doc.setAmount(amountOrZero(form));
        doc.setDrafterId(actorId);
        doc.setDeptId(drafter.getDeptId());
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.now());

        insertWithGeneratedDocNo(doc);
        saveLeaveRequestIfNeeded(doc, form);
        rebuildLines(doc, drafter);
        historyMapper.insert(HistoryFactory.of(doc.getApprovalId(), actorId, HistoryAction.DRAFT, null));
        return doc.getApprovalId();
    }

    private Long updateDraft(ApprovalForm form, Long actorId) {
        ApprovalDoc doc = requireDoc(form.getApprovalId());
        if (!Objects.equals(doc.getDrafterId(), actorId)) {
            throw new ApprovalAccessDeniedException("기안자만 수정할 수 있습니다");
        }
        if (!doc.isEditable()) {
            throw new ApprovalAccessDeniedException("임시저장 상태만 수정할 수 있습니다: " + doc.getStatus());
        }
        String previousDocType = doc.getDocType();
        doc.setDocType(form.getDocType());
        doc.setTitle(form.getTitle());
        doc.setContent(contentOf(form));
        doc.setAmount(amountOrZero(form));
        docMapper.update(doc);

        // docType 이 LEAVE 에서 다른 유형으로 바뀌면 이전에 만든 확장 행을 지운다 —
        // 그대로 두면 EXPENSE 문서인데 leave_request 가 남는 유령 데이터가 된다.
        if (DocType.LEAVE.equals(previousDocType) && !DocType.LEAVE.equals(doc.getDocType())) {
            leaveRequestMapper.deleteByApprovalId(doc.getApprovalId());
        }
        saveLeaveRequestIfNeeded(doc, form);

        rebuildLines(doc, requireEmployee(actorId));
        return doc.getApprovalId();
    }

    /**
     * docType=LEAVE 일 때만 leave_request 를 채운다. 일수는 화면 입력이 아니라
     * LeaveInquiryService(attendance 의 영업일 계산)가 계산한다.
     *
     * ★ 영업일이 0일이면 기안 자체를 거부한다 — 의미 없는 신청이 문서로 남는 것을
     * 막는다. 이 메서드는 saveDraft() 의 @Transactional 안에서 불리므로, 여기서
     * 던진 예외는 방금 만든 approval_doc 행까지 통째로 롤백시킨다.
     */
    private void saveLeaveRequestIfNeeded(ApprovalDoc doc, ApprovalForm form) {
        if (!DocType.LEAVE.equals(doc.getDocType())) {
            return;
        }
        if (form.getLeaveType() == null || form.getStartDate() == null || form.getEndDate() == null) {
            throw new IllegalArgumentException("연차 신청서는 유형과 기간을 모두 입력해야 합니다");
        }
        // 사유는 이 문서의 본문이 된다(contentOf 참조). 비면 결재자가 판단할 근거가
        // 없는 문서가 만들어지므로 다른 필수 항목과 같은 자리에서 막는다.
        if (form.getReason() == null || form.getReason().isBlank()) {
            throw new IllegalArgumentException("연차 신청서는 사유를 입력해야 합니다");
        }

        BigDecimal days = leaveInquiryService.calculateDays(
                form.getLeaveType(), form.getStartDate(), form.getEndDate());
        if (days.signum() <= 0) {
            throw new IllegalArgumentException(
                    "선택한 기간에는 영업일이 없어 신청할 수 없습니다: "
                            + form.getStartDate() + " ~ " + form.getEndDate());
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setApprovalId(doc.getApprovalId());
        leaveRequest.setLeaveType(form.getLeaveType());
        leaveRequest.setStartDate(form.getStartDate());
        leaveRequest.setEndDate(form.getEndDate());
        leaveRequest.setDays(days);
        leaveRequest.setReason(form.getReason());
        leaveRequestMapper.upsert(leaveRequest);
    }

    /**
     * 상신한다. 결재선 길이를 도메인 객체에 넘겨 결재자가 0명인 경우까지 한 곳에서 판정하게 한다.
     */
    @Transactional
    public void submit(Long approvalId, Long actorId) {
        ApprovalDoc doc = requireDocForUpdate(approvalId);
        if (!Objects.equals(doc.getDrafterId(), actorId)) {
            throw new ApprovalAccessDeniedException("기안자만 상신할 수 있습니다");
        }
        int approverCount = lineMapper.countByApprovalId(approvalId);

        doc.submit(approverCount);
        docMapper.updateStatus(doc);

        if (approverCount > 0) {
            lineMapper.activateStep(approvalId, 1);
        }
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.SUBMIT, null));
    }

    /**
     * 현재 단계를 승인한다.
     *
     * 행 잠금(FOR UPDATE)으로 조회하는 이유: 두 결재자가 동시에 누르면 잠금이 없으면
     * 둘 다 같은 current_step 을 읽어 각자 +1 해서 단계가 하나 건너뛰어진다.
     */
    @Transactional
    public void approve(Long approvalId, Long actorId, String comment) {
        ApprovalLimits.check(comment, ApprovalLimits.COMMENT, "의견");
        ApprovalDoc doc = requireDocForUpdate(approvalId);
        ApprovalLine current = requireMyCurrentLine(doc, approvalId, actorId);
        int totalStep = lineMapper.countByApprovalId(approvalId);

        current.setStatus(LineStatus.APPROVED);
        current.setComment(comment);
        current.setProcessedAt(LocalDateTime.now());
        lineMapper.updateStep(current);

        doc.approve(totalStep);
        docMapper.updateStatus(doc);

        if (!doc.isCompleted()) {
            lineMapper.activateStep(approvalId, doc.getCurrentStep());
        }
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.APPROVE, comment));

        // ★★ 이 Phase의 척추. 연차 신청서가 최종
        // 승인되면 근태에 반영한다. Spring 이벤트가 아니라 직접 호출로 붙인다 —
        // 같은 트랜잭션 안에서 어느 한쪽이 실패하면 전부 롤백되어야
        // "승인은 됐는데 연차가 안 깎였다" 같은 부분 실패를 막을 수 있기 때문이다.
        if (doc.isCompleted() && DocType.LEAVE.equals(doc.getDocType())) {
            leaveApplyService.apply(buildLeaveCommand(doc));
        }
    }

    /**
     * leave_request(approval 소유)를 읽어 LeaveApplyCommand 값을 조립한다
     * attendance 는 이 값만 받고, 자신의 소유가 아닌
     * leave_request 를 직접 읽지 않는다 — 그래야 의존이 approval → attendance
     * 한 방향으로 유지된다.
     */
    private LeaveApplyCommand buildLeaveCommand(ApprovalDoc doc) {
        LeaveRequest request = leaveRequestMapper.findByApprovalId(doc.getApprovalId());
        if (request == null) {
            throw new IllegalStateException(
                    "LEAVE 문서인데 연차 신청서가 없습니다: approvalId=" + doc.getApprovalId());
        }
        return new LeaveApplyCommand(doc.getApprovalId(), doc.getDrafterId(), request.getLeaveType(),
                request.getStartDate(), request.getEndDate(), request.getDays());
    }

    /**
     * 반려한다. 반려 유형이 없으면 거부한다 — 이 값이 Phase 5 사전점검의 학습 원천이다.
     */
    @Transactional
    public void reject(Long approvalId, Long actorId, String reasonCategory, String reasonText) {
        if (!RejectReason.isValid(reasonCategory)) {
            throw new IllegalArgumentException("반려 유형을 선택해야 합니다: " + reasonCategory);
        }
        ApprovalLimits.check(reasonText, ApprovalLimits.COMMENT, "의견");
        ApprovalDoc doc = requireDocForUpdate(approvalId);
        ApprovalLine current = requireMyCurrentLine(doc, approvalId, actorId);

        current.setStatus(LineStatus.REJECTED);
        current.setComment(reasonText);
        current.setProcessedAt(LocalDateTime.now());
        lineMapper.updateStep(current);

        // 뒤 단계는 차례가 오지 않는다
        lineMapper.skipRemaining(approvalId, doc.getCurrentStep() + 1);

        doc.reject();
        docMapper.updateStatus(doc);
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.REJECT, reasonText));

        // ★ Phase 5 AI 사전점검이 읽는 표. 비정규화된 doc_type/dept_id 를 여기서 채운다.
        RejectHistory reject = new RejectHistory();
        reject.setApprovalId(approvalId);
        reject.setDocType(doc.getDocType());
        reject.setDeptId(doc.getDeptId());
        reject.setRejectorId(actorId);
        reject.setReasonCategory(reasonCategory);
        reject.setReasonText(reasonText);
        reject.setRejectedAt(LocalDateTime.now());
        rejectHistoryMapper.insert(reject);
    }

    /** 기안자가 회수한다. 가능 여부 판정은 도메인 객체가 한다 */
    @Transactional
    public void cancel(Long approvalId, Long actorId) {
        ApprovalDoc doc = requireDocForUpdate(approvalId);

        doc.cancel(actorId);
        docMapper.updateStatus(doc);

        lineMapper.skipRemaining(approvalId, 1);
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.CANCEL, null));
    }

    private ApprovalDoc requireDocForUpdate(Long approvalId) {
        ApprovalDoc doc = docMapper.findByIdForUpdate(approvalId);
        if (doc == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        return doc;
    }

    /**
     * 지금 이 사람이 처리할 차례인 결재선 단계를 돌려준다.
     *
     * 두 가지를 구분해서 검사한다.
     * 1) 그 단계가 지금 처리 가능한 상태인가 — 아니라면 상태 문제다 (IllegalStateException, 409).
     *    이미 처리된 단계를 다시 누른 경우(중복 클릭, 다른 탭)가 여기 해당하는데,
     *    이건 권한이 없는 것이 아니라 타이밍이 어긋난 것이므로 "권한이 없습니다"라고 하면
     *    정당한 결재자를 오도한다.
     * 2) 그 단계의 결재자가 요청자인가 — 아니라면 진짜 권한 문제다 (ApprovalAccessDeniedException, 403).
     */
    private ApprovalLine requireMyCurrentLine(ApprovalDoc doc, Long approvalId, Long actorId) {
        ApprovalLine line = lineMapper.findStep(approvalId, doc.getCurrentStep());
        if (line == null || !LineStatus.CURRENT.equals(line.getStatus())) {
            throw new IllegalStateException("지금 처리할 수 있는 단계가 아닙니다");
        }
        if (!Objects.equals(line.getApproverId(), actorId)) {
            throw new ApprovalAccessDeniedException("이 단계의 결재자가 아닙니다");
        }
        return line;
    }

    /**
     * 결재선을 정책으로 다시 만든다.
     *
     * 부서장 체인 조회는 org 모듈의 Service 를 경유한다.
     * 변환(Employee → ApproverCandidate)은 여기서 한다 — org 이 approval 의 타입을 알지 않게.
     */
    private void rebuildLines(ApprovalDoc doc, Employee drafter) {
        lineMapper.deleteByApprovalId(doc.getApprovalId());

        List<ApproverCandidate> chain = new ArrayList<>();
        for (Employee head : departmentService.findDeptHeadChain(drafter.getDeptId())) {
            chain.add(toCandidate(head));
        }
        List<ApprovalLine> lines = linePolicy.determineLines(doc, toCandidate(drafter), chain);
        if (!lines.isEmpty()) {
            lineMapper.insertAll(lines);
        }
    }

    private ApproverCandidate toCandidate(Employee e) {
        return new ApproverCandidate(e.getEmpId(), e.getEmpName(), e.getDeptId(),
                e.getPositionLevel(), e.getPositionName());
    }

    /**
     * 문서번호를 부여해 저장한다.
     *
     * 채번 전에 (접두사, 연도) 단위 자문 잠금을 잡아 동시 기안이 같은 번호를 만들지 못하게 한다.
     * 재시도로 수습하지 않는 이유: PostgreSQL 은 제약 위반 시 트랜잭션 전체를 중단시키므로
     * DuplicateKeyException 을 잡아도 같은 트랜잭션에서 다음 쿼리를 실행할 수 없다.
     * doc_no 의 UNIQUE 제약은 여전히 최후 방어선으로 남는다.
     */
    private void insertWithGeneratedDocNo(ApprovalDoc doc) {
        int year = Year.now().getValue();
        String prefix = DocType.prefixOf(doc.getDocType());

        docMapper.lockDocNoSeq(prefix + "-" + year);

        int seq = docMapper.maxDocNoSeq(prefix, year) + 1;
        doc.setDocNo(String.format("%s-%d-%04d", prefix, year, seq));
        docMapper.insert(doc);
    }

    private ApprovalDoc requireDoc(Long approvalId) {
        ApprovalDoc doc = docMapper.findById(approvalId);
        if (doc == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        return doc;
    }

    private Employee requireEmployee(Long empId) {
        Employee e = employeeMapper.findById(empId);
        if (e == null) {
            throw new ApprovalAccessDeniedException("사원을 찾을 수 없습니다: " + empId);
        }
        return e;
    }
}
