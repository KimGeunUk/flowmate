package com.flowmate.ai.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.domain.LeaveContext;
import com.flowmate.ai.domain.PreflightRecord;
import com.flowmate.ai.domain.SummaryResult;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.service.ApprovalService;
import com.flowmate.attendance.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * ★ 커스터마이징 지점 5 의 증명(계획서 5 Task 7, 계획서 5 정정 섹션). {@code ai.features.*}
 * 키는 Phase 3 부터 application.yml 에 있었지만 아무도 읽지 않았다 - "키만 있고 쓰는
 * 기능이 없다"는 것이 그 정정 섹션이 지적한 결함이다. 이 클래스는 그 반대,
 * 즉 플래그를 끄면 해당 기능이 {@link com.flowmate.ai.client.LlmClient} 까지 실제로
 * 도달하지 않는다는 것을 단정한다.
 *
 * 세 플래그를 모두 끈 하나의 Spring 컨텍스트로 세 기능을 함께 검증한다 -
 * "켜져 있을 때"(기본값 true)의 증거는 이미 SummaryServiceIT/PreflightServiceIT/
 * LeaveContextServiceIT 가 맡고 있으므로, 여기서는 "꺼졌을 때"만 새로 증명하면
 * 충분하다(같은 서비스에 대해 서로 다른 설정 → 서로 다른 결과, Phase 2·4와 같은
 * 교체 증명의 모양이다 - 이번엔 구현체 교체가 아니라 켜짐/꺼짐이라는 차이가 있다).
 */
@SpringBootTest(properties = {
        "ai.features.summary=false",
        "ai.features.preflight=false",
        "ai.features.leave-context=false"
})
@Transactional
class AiFeatureFlagsDisabledIT {

    private static final Long KWAK = 18L; // 개발팀(dept 7) - 문서 1(EXP-2026-0001)의 기안자

    @Autowired
    private SummaryService summaryService;

    @Autowired
    private PreflightService preflightService;

    @Autowired
    private LeaveContextService leaveContextService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    /**
     * {@code fakeLlmClient} 는 싱글턴이라 다른 IT 가 남긴 받은 요청 목록이 남아 있을
     * 수 있다(LlmChainIT/SummaryServiceIT 의 resetFake() 주석과 같은 함정) - 이
     * 클래스의 단정은 "0건"이어야 하므로 특히 더 중요하다.
     */
    @BeforeEach
    void resetFake() {
        fakeLlmClient.getReceived().clear();
    }

    @Test
    @DisplayName("★ ai.features.summary=false → 문서 요약이 LlmClient 를 전혀 부르지 않고 빈 결과다")
    void summaryDisabledNeverReachesLlmClient() {
        Optional<SummaryResult> result = summaryService.summarize(1L, KWAK);

        assertThat(result).isEmpty();
        assertThat(fakeLlmClient.getReceived()).isEmpty();
    }

    @Test
    @DisplayName("★ ai.features.preflight=false → 사전점검이 LlmClient 를 전혀 부르지 않고 빈 결과다")
    void preflightDisabledNeverReachesLlmClient() {
        Long approvalId = draft(KWAK, DocType.GENERAL);

        Optional<PreflightRecord> result = preflightService.check(approvalId, KWAK);

        assertThat(result).isEmpty();
        assertThat(fakeLlmClient.getReceived()).isEmpty();
    }

    @Test
    @DisplayName("ai.features.leave-context=false → 연차 맥락 패널이 null 이고, LlmClient 호출도 없다")
    void leaveContextDisabledNeverReachesLlmClientEither() {
        Long approvalId = draftLeave(KWAK);

        LeaveContext context = leaveContextService.build(approvalId, KWAK);

        assertThat(context).isNull();
        // LeaveContextService 는 원래도 LlmClient 를 쓰지 않는다(Task 4, LLM 없음) -
        // 그래도 세 기능이 같은 방식으로 검증된다는 것을 보이기 위해 함께 확인한다.
        assertThat(fakeLlmClient.getReceived()).isEmpty();
    }

    private Long draft(Long drafterId, String docType) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(docType);
        form.setTitle("기능 플래그 테스트 문서 - " + docType);
        form.setContent("ai.features 플래그 검증을 위한 본문입니다.");
        form.setAmount(BigDecimal.TEN);
        return approvalService.saveDraft(form, drafterId);
    }

    private Long draftLeave(Long drafterId) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.LEAVE);
        form.setTitle("연차 신청 - 기능 플래그 테스트");
        form.setAmount(BigDecimal.ZERO);
        form.setLeaveType(LeaveType.ANNUAL);
        form.setStartDate(LocalDate.of(2026, 9, 10));
        form.setEndDate(LocalDate.of(2026, 9, 10));
        form.setReason("기능 플래그 테스트");
        return approvalService.saveDraft(form, drafterId);
    }
}
