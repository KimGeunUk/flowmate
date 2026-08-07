package com.flowmate.approval.policy;

import java.util.List;
import java.util.Objects;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;

/**
 * 소규모 고객사용 결재선 정책 — 기안자 → 소속 부서장 2단계로 고정한다.
 *
 * 기본 정책과 다른 점:
 *   - 부서 트리를 오르지 않는다. 체인의 첫 항목(소속 부서)만 본다
 *   - 금액을 보지 않는다. 임원 결재가 붙지 않는다
 *
 * 조직이 작아 본부 계층이 사실상 없는 고객사를 상정한 것이다.
 * 결재 단계가 늘어나면 오히려 업무가 막히는 규모에서 쓴다.
 */
public class SimpleTwoStepLinePolicy implements ApprovalLinePolicy {

    @Override
    public List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                             List<ApproverCandidate> deptHeadChain) {
        if (deptHeadChain.isEmpty()) {
            return toApprovalLines(doc.getApprovalId(), List.of());
        }
        ApproverCandidate head = deptHeadChain.get(0);
        if (Objects.equals(head.getEmpId(), drafter.getEmpId())) {
            return toApprovalLines(doc.getApprovalId(), List.of());
        }
        if (head.getPositionLevel() <= drafter.getPositionLevel()) {
            return toApprovalLines(doc.getApprovalId(), List.of());
        }
        return toApprovalLines(doc.getApprovalId(), List.of(head));
    }
}
