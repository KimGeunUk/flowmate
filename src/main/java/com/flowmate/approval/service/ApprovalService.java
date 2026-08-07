package com.flowmate.approval.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.HistoryAction;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.approval.mapper.RejectHistoryMapper;
import com.flowmate.approval.policy.ApprovalLinePolicy;
import com.flowmate.approval.policy.ApproverCandidate;
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

    private static final int DOC_NO_RETRY = 3;

    private final ApprovalDocMapper docMapper;
    private final ApprovalLineMapper lineMapper;
    private final ApprovalHistoryMapper historyMapper;
    private final RejectHistoryMapper rejectHistoryMapper;
    private final ApprovalLinePolicy linePolicy;
    private final DepartmentService departmentService;
    private final EmployeeMapper employeeMapper;

    public ApprovalService(ApprovalDocMapper docMapper,
                           ApprovalLineMapper lineMapper,
                           ApprovalHistoryMapper historyMapper,
                           RejectHistoryMapper rejectHistoryMapper,
                           ApprovalLinePolicy linePolicy,
                           DepartmentService departmentService,
                           EmployeeMapper employeeMapper) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.historyMapper = historyMapper;
        this.rejectHistoryMapper = rejectHistoryMapper;
        this.linePolicy = linePolicy;
        this.departmentService = departmentService;
        this.employeeMapper = employeeMapper;
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
        if (form.getApprovalId() == null) {
            return createDraft(form, actorId);
        }
        return updateDraft(form, actorId);
    }

    private Long createDraft(ApprovalForm form, Long actorId) {
        Employee drafter = requireEmployee(actorId);

        ApprovalDoc doc = new ApprovalDoc();
        doc.setDocType(form.getDocType());
        doc.setTitle(form.getTitle());
        doc.setContent(form.getContent());
        doc.setAmount(form.getAmount());
        doc.setDrafterId(actorId);
        doc.setDeptId(drafter.getDeptId());
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.now());

        insertWithGeneratedDocNo(doc);
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
        doc.setDocType(form.getDocType());
        doc.setTitle(form.getTitle());
        doc.setContent(form.getContent());
        doc.setAmount(form.getAmount());
        docMapper.update(doc);

        rebuildLines(doc, requireEmployee(actorId));
        return doc.getApprovalId();
    }

    /**
     * 결재선을 정책으로 다시 만든다.
     *
     * 부서장 체인 조회는 org 모듈의 Service 를 경유한다 (설계서 §4.3).
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
     * MAX + 1 방식이라 동시에 두 명이 같은 유형을 기안하면 같은 번호가 나올 수 있다.
     * doc_no 의 UNIQUE 제약이 최후 방어선이고, 충돌하면 번호를 다시 계산해 재시도한다.
     * (운영 규모에서는 유형별 시퀀스를 쓰는 것이 정석이다 — 여기서는 제약 + 재시도로 충분하다.)
     */
    private void insertWithGeneratedDocNo(ApprovalDoc doc) {
        int year = Year.now().getValue();
        String prefix = DocType.prefixOf(doc.getDocType());
        for (int attempt = 1; attempt <= DOC_NO_RETRY; attempt++) {
            int seq = docMapper.maxDocNoSeq(prefix, year) + 1;
            doc.setDocNo(String.format("%s-%d-%04d", prefix, year, seq));
            try {
                docMapper.insert(doc);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == DOC_NO_RETRY) {
                    throw e;
                }
            }
        }
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
