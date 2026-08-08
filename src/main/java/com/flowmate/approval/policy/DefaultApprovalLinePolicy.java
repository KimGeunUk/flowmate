package com.flowmate.approval.policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;

/**
 * 기본 결재선 정책 — 부서 트리를 올라가며 상위 결재자를 모으고, 큰 금액이면 임원을 붙인다.
 *
 * 규칙 (로드맵 §5.1 확정):
 *   1. 기안자 부서에서 루트까지 올라간다
 *   2. 각 부서의 최고 직급자 1명이 후보다
 *   3. 기안자보다 직급이 높고, 임원 미만이고, 본인이 아니면 결재자로 추가
 *   4. 금액이 기준을 넘으면 임원을 마지막에 추가
 *   5. 그래도 결재선이 비면 임원을 추가 (빈 결재선 방지)
 *   6. 그래도 비면 결재자 0명 — 상신 즉시 완료된다
 *
 * ★ 3번에서 임원을 걸러내는 것이 이 정책의 핵심이다.
 *   걸러내지 않으면 루트 부서장(이사)이 항상 체인에 있으므로 결재선에 늘 들어오고,
 *   4번의 금액 조건이 아무 의미가 없어진다.
 */
public class DefaultApprovalLinePolicy implements ApprovalLinePolicy {

    /** 이 직급 이상을 임원으로 본다. 시드에서는 이사(6)만 해당한다 */
    private static final int EXECUTIVE_LEVEL = 6;

    /** 이 금액을 **초과**하면 임원 결재가 붙는다 */
    private static final BigDecimal EXECUTIVE_THRESHOLD = new BigDecimal("3000000");

    @Override
    public List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                             List<ApproverCandidate> deptHeadChain) {
        List<ApproverCandidate> approvers = new ArrayList<>();

        for (ApproverCandidate head : deptHeadChain) {
            if (head.getPositionLevel() <= drafter.getPositionLevel()) {
                continue;   // 기안자와 같거나 낮은 직급은 결재자가 아니다
            }
            if (head.getPositionLevel() >= EXECUTIVE_LEVEL) {
                continue;   // ★ 임원은 트리 탐색으로 넣지 않는다. 금액 규칙으로만 붙인다
            }
            if (Objects.equals(head.getEmpId(), drafter.getEmpId())) {
                continue;   // 본인
            }
            if (containsEmp(approvers, head.getEmpId())) {
                continue;   // 같은 사람이 두 부서의 장인 경우
            }
            approvers.add(head);
        }

        ApproverCandidate executive = findExecutive(deptHeadChain);
        if (executive != null && !Objects.equals(executive.getEmpId(), drafter.getEmpId())) {
            boolean overThreshold = doc.getAmount() != null
                    && doc.getAmount().compareTo(EXECUTIVE_THRESHOLD) > 0;
            boolean wouldBeEmpty = approvers.isEmpty();
            if ((overThreshold || wouldBeEmpty) && !containsEmp(approvers, executive.getEmpId())) {
                approvers.add(executive);
            }
        }

        return toApprovalLines(doc.getApprovalId(), approvers);
    }

    /** 체인에서 가장 직급이 높은 임원. 없으면 null */
    private ApproverCandidate findExecutive(List<ApproverCandidate> deptHeadChain) {
        ApproverCandidate found = null;
        for (ApproverCandidate c : deptHeadChain) {
            if (c.getPositionLevel() < EXECUTIVE_LEVEL) {
                continue;
            }
            if (found == null || c.getPositionLevel() > found.getPositionLevel()) {
                found = c;
            }
        }
        return found;
    }

    private boolean containsEmp(List<ApproverCandidate> list, Long empId) {
        for (ApproverCandidate c : list) {
            if (Objects.equals(c.getEmpId(), empId)) {
                return true;
            }
        }
        return false;
    }
}
