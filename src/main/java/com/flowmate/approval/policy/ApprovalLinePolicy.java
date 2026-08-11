package com.flowmate.approval.policy;

import java.util.ArrayList;
import java.util.List;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.LineType;

/**
 * ★ 커스터마이징 지점 1 — 고객사마다 결재선 규칙이 다르다.
 *
 * 조회를 하지 않고 넘겨받은 후보로 계산만 하는 이유:
 * 매퍼를 주입받으면 DB 없이 단위 테스트할 수 없다. 이 정책을
 * "JUnit 단위 테스트" 대상으로 잡았으므로 순수 로직으로 유지한다.
 * 조회는 ApprovalService 가 담당한다.
 */
public interface ApprovalLinePolicy {

    /**
     * 결재선을 만든다.
     *
     * @param doc           결재선을 붙일 문서. docType 과 amount 를 참조한다
     * @param drafter       기안자
     * @param deptHeadChain 기안자 부서에서 루트까지 각 부서의 최고 직급자 1명.
     *                      **가까운 부서가 먼저**여야 한다
     * @return 1단계부터 순서대로. 결재자가 없으면 빈 리스트
     */
    List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                      List<ApproverCandidate> deptHeadChain);

    /**
     * 결재자 목록을 결재선으로 바꾼다. 구현체가 공유하는 조립 로직이다.
     *
     * 전부 WAITING 으로 만든다 - 결재선은 임시저장 시점에 생기므로 아직 아무
     * 단계도 진행 중이 아니다. 1단계를 CURRENT 로 바꾸는 것은 상신 시점의 일이다.
     */
    default List<ApprovalLine> toApprovalLines(Long approvalId, List<ApproverCandidate> approvers) {
        List<ApprovalLine> lines = new ArrayList<>();
        int stepNo = 1;
        for (ApproverCandidate approver : approvers) {
            ApprovalLine line = new ApprovalLine();
            line.setApprovalId(approvalId);
            line.setStepNo(stepNo);
            line.setApproverId(approver.getEmpId());
            line.setLineType(LineType.APPROVAL);
            line.setStatus(LineStatus.WAITING);
            line.setApproverName(approver.getEmpName());
            line.setApproverPositionName(approver.getPositionName());
            lines.add(line);
            stepNo++;
        }
        return lines;
    }
}
