package com.flowmate.approval.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;

/**
 * 소규모 고객사용 결재선 정책 — 소속 부서장 1명으로 끝난다.
 *
 * 이 클래스의 존재 이유는 기능이 아니라 **교체 가능성의 증명**이다.
 * 마지막 테스트가 같은 입력에 두 정책이 서로 다른 결재선을 내는 것을 나란히 보여준다.
 */
class SimpleTwoStepLinePolicyTest {

    private final ApprovalLinePolicy simple = new SimpleTwoStepLinePolicy();
    private final ApprovalLinePolicy defaultPolicy = new DefaultApprovalLinePolicy();

    private static final ApproverCandidate KWAK  = new ApproverCandidate(18L, "곽수빈", 7L, 1, "사원");
    private static final ApproverCandidate SHIN  = new ApproverCandidate(14L, "신동혁", 7L, 3, "과장");
    private static final ApproverCandidate PARK  = new ApproverCandidate(3L,  "박현주", 3L, 5, "부장");
    private static final ApproverCandidate JEONG = new ApproverCandidate(1L,  "정도현", 1L, 6, "이사");

    private static final List<ApproverCandidate> DEV_CHAIN = List.of(SHIN, PARK, JEONG);

    private static ApprovalDoc doc(ApproverCandidate drafter, String amount) {
        ApprovalDoc d = new ApprovalDoc();
        d.setApprovalId(200L);
        d.setDocType(DocType.PURCHASE);
        d.setTitle("테스트 문서");
        d.setDrafterId(drafter.getEmpId());
        d.setDeptId(drafter.getDeptId());
        d.setAmount(new BigDecimal(amount));
        d.setStatus(ApprovalStatus.DRAFT);
        return d;
    }

    @Test
    @DisplayName("소속 부서장 1명만 결재자로 둔다")
    void onlyImmediateDepartmentHead() {
        List<ApprovalLine> lines = simple.determineLines(doc(KWAK, "1000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1);
    }

    @Test
    @DisplayName("기안자가 자기 부서 최고 직급이면 결재선이 비어 있다")
    void emptyWhenDrafterIsOwnDepartmentHead() {
        List<ApprovalLine> lines = simple.determineLines(doc(SHIN, "1000000"), SHIN, DEV_CHAIN);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("★ 같은 입력에 두 정책이 서로 다른 결재선을 만든다 - 교체 가능성의 증명")
    void twoPoliciesProduceDifferentLinesForSameInput() {
        ApprovalDoc largeAmount = doc(KWAK, "5000000");

        List<ApprovalLine> byDefault = defaultPolicy.determineLines(largeAmount, KWAK, DEV_CHAIN);
        List<ApprovalLine> bySimple = simple.determineLines(largeAmount, KWAK, DEV_CHAIN);

        // 기본 정책: 팀장 → 본부장 → 이사 (금액이 크므로 임원까지)
        assertThat(byDefault).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L, 1L);
        // 소규모 정책: 팀장 한 명. 금액을 보지 않는다
        assertThat(bySimple).extracting(ApprovalLine::getApproverId).containsExactly(14L);

        assertThat(bySimple).hasSizeLessThan(byDefault.size());
    }
}
