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
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.LineType;

/**
 * 기본 결재선 정책. 로드맵 §5.1 이 확정한 규칙과 검증 표를 그대로 고정한다.
 *
 * 후보 목록을 인자로 받으므로 DB 없이 돈다 — 이것이 이 인터페이스를 설계서 원안과
 * 다르게 잡은 이유다.
 */
class DefaultApprovalLinePolicyTest {

    private final ApprovalLinePolicy policy = new DefaultApprovalLinePolicy();

    // 시드 조직도: 대표이사실(1) → 경영지원본부(2) · 사업본부(3) → 인사팀(4) · 재무팀(5) · 마케팅팀(6) · 개발팀(7)
    private static final ApproverCandidate KWAK   = cand(18L, "곽수빈", 7L, 1, "사원");
    private static final ApproverCandidate SHIN   = cand(14L, "신동혁", 7L, 3, "과장");
    private static final ApproverCandidate PARK   = cand(3L,  "박현주", 3L, 5, "부장");
    private static final ApproverCandidate JEONG  = cand(1L,  "정도현", 1L, 6, "이사");
    private static final ApproverCandidate SEO    = cand(6L,  "서다인", 4L, 1, "사원");
    private static final ApproverCandidate CHOI   = cand(4L,  "최민석", 4L, 4, "차장");
    private static final ApproverCandidate KIM    = cand(2L,  "김성일", 2L, 5, "부장");

    /** 개발팀 사원이 기안했을 때의 부서장 체인: 개발팀 → 사업본부 → 대표이사실 */
    private static final List<ApproverCandidate> DEV_CHAIN = List.of(SHIN, PARK, JEONG);
    /** 인사팀 사원이 기안했을 때의 체인: 인사팀 → 경영지원본부 → 대표이사실 */
    private static final List<ApproverCandidate> HR_CHAIN = List.of(CHOI, KIM, JEONG);
    /** 사업본부 부장이 기안했을 때의 체인: 사업본부 → 대표이사실 */
    private static final List<ApproverCandidate> BIZ_CHAIN = List.of(PARK, JEONG);
    /** 대표이사실 이사가 기안했을 때의 체인: 대표이사실뿐 */
    private static final List<ApproverCandidate> CEO_CHAIN = List.of(JEONG);

    private static ApproverCandidate cand(Long id, String name, Long deptId, int level, String position) {
        return new ApproverCandidate(id, name, deptId, level, position);
    }

    private static ApprovalDoc doc(ApproverCandidate drafter, String amount) {
        ApprovalDoc d = new ApprovalDoc();
        d.setApprovalId(100L);
        d.setDocType(DocType.EXPENSE);
        d.setTitle("테스트 문서");
        d.setDrafterId(drafter.getEmpId());
        d.setDeptId(drafter.getDeptId());
        d.setAmount(new BigDecimal(amount));
        d.setStatus(ApprovalStatus.DRAFT);
        return d;
    }

    @Test
    @DisplayName("사원이 소액을 기안하면 팀장과 본부장 2단계가 된다 - 설계서 완료 기준과 같은 모양")
    void staffSmallAmountGetsTeamLeadAndDivisionHead() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "1000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1, 2);
    }

    @Test
    @DisplayName("금액이 300만원을 넘으면 이사가 마지막에 붙는다")
    void largeAmountAppendsExecutive() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "5000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L, 1L);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("300만원 정확히면 임원이 붙지 않는다 - 초과일 때만 붙는다")
    void exactThresholdDoesNotAppendExecutive() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "3000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L);
    }

    @Test
    @DisplayName("기안자가 자기 부서 최고 직급이면 그 부서는 건너뛴다")
    void skipsOwnDepartmentWhenDrafterIsItsHead() {
        List<ApprovalLine> lines = policy.determineLines(doc(SHIN, "1000000"), SHIN, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(3L);
    }

    @Test
    @DisplayName("다른 본부에서도 같은 모양으로 2단계가 만들어진다")
    void sameShapeInAnotherDivision() {
        List<ApprovalLine> lines = policy.determineLines(doc(SEO, "1000000"), SEO, HR_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(4L, 2L);
    }

    @Test
    @DisplayName("결재자가 아무도 없으면 소액이어도 이사를 붙인다 - 빈 결재선 방지")
    void appendsExecutiveWhenLineWouldBeEmpty() {
        List<ApprovalLine> lines = policy.determineLines(doc(PARK, "1000000"), PARK, BIZ_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(1L);
    }

    @Test
    @DisplayName("이사가 기안하면 결재자가 없다 - 상신 즉시 완료되는 경로")
    void executiveDraftHasNoApprover() {
        List<ApprovalLine> lines = policy.determineLines(doc(JEONG, "1000000"), JEONG, CEO_CHAIN);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("이사가 큰 금액을 기안해도 자기 자신을 결재자로 넣지 않는다")
    void executiveDraftDoesNotSelfApproveEvenForLargeAmount() {
        List<ApprovalLine> lines = policy.determineLines(doc(JEONG, "50000000"), JEONG, CEO_CHAIN);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("생성된 결재선은 전부 대기 상태이고 결재 종류다")
    void createdLinesAreWaitingApprovalType() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "1000000"), KWAK, DEV_CHAIN);

        assertThat(lines).allSatisfy(line -> {
            assertThat(line.getStatus()).isEqualTo(LineStatus.WAITING);
            assertThat(line.getLineType()).isEqualTo(LineType.APPROVAL);
            assertThat(line.getApprovalId()).isEqualTo(100L);
            assertThat(line.getProcessedAt()).isNull();
        });
    }

    @Test
    @DisplayName("금액이 null 이어도 예외 없이 소액으로 취급한다")
    void nullAmountIsTreatedAsSmall() {
        ApprovalDoc d = doc(KWAK, "0");
        d.setAmount(null);

        List<ApprovalLine> lines = policy.determineLines(d, KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L);
    }
}
