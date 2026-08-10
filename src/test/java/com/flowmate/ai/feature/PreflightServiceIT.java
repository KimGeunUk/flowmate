package com.flowmate.ai.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.domain.Finding;
import com.flowmate.ai.domain.PreflightRecord;
import com.flowmate.ai.domain.PreflightResult;
import com.flowmate.ai.domain.PreflightVerdict;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.approval.service.ApprovalService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 2(상신 전 사전 점검) 배선 검증 (설계서 §6.4.6, 계획서 5 Task 5 ★ 이 Phase 의 핵심).
 * {@code ai.enabled=false}(기본값)라서 {@link FakeLlmClient} 로 전체 체인을 태운다 - 실제
 * API 호출은 없다(평가셋은 별도 Task 9 의 몫이다).
 *
 * 드래프터는 사원 18(곽수빈, 개발팀=dept 7) - 다른 Phase 5 IT(SummaryServiceIT,
 * LeaveContextServiceIT)와 같은 픽스처를 재사용한다. approval_reject_history 에
 * 직접 꽂는 픽스처는 dept=7 + GENERAL(운영 시드가 이 조합에 반려를 심지 않는다 -
 * 50-seed-demo.sql 은 dept7 을 PURCHASE 로만 편중시킨다) 또는 dept=7 + CONTRACT
 * (운영 시드가 아예 만들지 않는 문서유형)를 써서, 실제 데모 시드의 반려 이력과 절대
 * 섞이지 않는 깨끗한 조합만 쓴다 - 그래야 집계 건수를 시드 변경과 무관하게
 * 손으로 정확히 예측할 수 있다.
 */
@SpringBootTest
@Transactional
class PreflightServiceIT {

    private static final Long KWAK = 18L;      // 개발팀(dept 7)
    private static final Long OUTSIDER = 5L;   // 기안자도 결재선도 아니다
    private static final Long FK_DOC_ID = 1L;  // approval_reject_history.approval_id FK 용 - 어떤 문서든 존재하기만 하면 된다

    @Autowired
    private PreflightService preflightService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalQueryService approvalQueryService;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFake() {
        fakeLlmClient.getReceived().clear();
        fakeLlmClient.setDelayMillis(0);
        fakeLlmClient.setExceptionToThrow(null);
        fakeLlmClient.setEchoPrompt(false);
        fakeLlmClient.setFixedResult(PreflightResult.class, passResult());
    }

    @Test
    @DisplayName("반려 이력이 있는 부서·유형 → 집계(유형별 건수)가 프롬프트에 들어간다")
    void aggregationOfExistingDeptReachesPrompt() {
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - 개별1");
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - 개별2");
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - 개별3");
        insertReject(DocType.GENERAL, 7L, RejectReason.PROCEDURE_ERROR, "절차 오류 - 개별1");
        insertReject(DocType.GENERAL, 7L, RejectReason.PROCEDURE_ERROR, "절차 오류 - 개별2");

        Long approvalId = draft(KWAK, DocType.GENERAL);

        preflightService.check(approvalId, KWAK);

        String prompt = fakeLlmClient.lastRequest().getPrompt();
        assertThat(prompt).contains(RejectReason.MISSING_EVIDENCE).contains("3건");
        assertThat(prompt).contains(RejectReason.PROCEDURE_ERROR).contains("2건");
    }

    @Test
    @DisplayName("해당 부서에 반려 이력이 없으면 전사로 확대한다")
    void widensToCompanyWideWhenDeptHasNoHistory() {
        // CONTRACT 는 운영 시드가 아예 만들지 않는 유형이다 - dept 7 은 당연히 0건이고,
        // dept 4 에 손으로 심은 이 이력이 "전사"의 유일한 신호가 된다.
        insertReject(DocType.CONTRACT, 4L, RejectReason.BUDGET_EXCEEDED, "예산 초과 - 타부서1");
        insertReject(DocType.CONTRACT, 4L, RejectReason.BUDGET_EXCEEDED, "예산 초과 - 타부서2");
        insertReject(DocType.CONTRACT, 4L, RejectReason.BUDGET_EXCEEDED, "예산 초과 - 타부서3");

        Long approvalId = draft(KWAK, DocType.CONTRACT); // KWAK 은 dept 7

        preflightService.check(approvalId, KWAK);

        String prompt = fakeLlmClient.lastRequest().getPrompt();
        assertThat(prompt).contains(RejectReason.BUDGET_EXCEEDED).contains("3건");
    }

    @Test
    @DisplayName("★ 프롬프트에 반려 원문(reason_text)이 들어가지 않는다 - 개인정보 유입 방지")
    void promptNeverContainsReasonText() {
        String secretReasonText = "곽수빈에게 1200000원 초과 지급 확인됨 - 유출테스트마커XZQ";
        insertReject(DocType.GENERAL, 7L, RejectReason.EXCESSIVE_AMOUNT, secretReasonText);

        Long approvalId = draft(KWAK, DocType.GENERAL);

        preflightService.check(approvalId, KWAK);

        String prompt = fakeLlmClient.lastRequest().getPrompt();
        assertThat(prompt).doesNotContain(secretReasonText);
        assertThat(prompt).doesNotContain("유출테스트마커XZQ");
        assertThat(prompt).doesNotContain("1200000");
        // 반면 유형·건수는 정상적으로 들어간다 - "아무것도 안 보낸다"가 아니라
        // "원문만 안 보낸다"는 것을 함께 확인한다.
        assertThat(prompt).contains(RejectReason.EXCESSIVE_AMOUNT).contains("1건");
    }

    @Test
    @DisplayName("★ verdict=WARN 이면 ai_preflight_result 에 기록된다")
    void warnVerdictIsPersisted() {
        fakeLlmClient.setFixedResult(PreflightResult.class, warnResult(3));
        Long approvalId = draft(KWAK, DocType.GENERAL);

        Optional<PreflightRecord> result = preflightService.check(approvalId, KWAK);

        assertThat(result).isPresent();
        assertThat(result.get().getResultId()).isNotNull();
        assertThat(result.get().getVerdict()).isEqualTo(PreflightVerdict.WARN);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT verdict, ignored_yn, findings_json FROM ai_preflight_result WHERE result_id = ?",
                result.get().getResultId());
        assertThat(row.get("verdict")).isEqualTo("WARN");
        assertThat(row.get("ignored_yn")).isEqualTo("N");
        assertThat(String.valueOf(row.get("findings_json"))).contains("MISSING_EVIDENCE");
    }

    @Test
    @DisplayName("verdict=PASS 는 ai_preflight_result 에 기록되지 않는다 - 경고만 추적한다")
    void passVerdictIsNotPersisted() {
        Long approvalId = draft(KWAK, DocType.GENERAL);

        Optional<PreflightRecord> result = preflightService.check(approvalId, KWAK);

        assertThat(result).isPresent();
        assertThat(result.get().getResultId()).isNull();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_preflight_result WHERE approval_id = ?", Integer.class, approvalId);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("★ '무시하고 상신' → ignored_yn='Y' 로 바뀐다")
    void ignoringWarnMarksIgnoredYn() {
        fakeLlmClient.setFixedResult(PreflightResult.class, warnResult(2));
        Long approvalId = draft(KWAK, DocType.GENERAL);
        Long resultId = preflightService.check(approvalId, KWAK).orElseThrow().getResultId();

        preflightService.ignore(resultId, KWAK);

        String ignoredYn = jdbcTemplate.queryForObject(
                "SELECT ignored_yn FROM ai_preflight_result WHERE result_id = ?", String.class, resultId);
        assertThat(ignoredYn).isEqualTo("Y");
    }

    @Test
    @DisplayName("★ AI 실패(진짜 예외) → 사전점검은 빈 결과이고, 상신은 그것과 무관하게 성공한다")
    void aiFailureNeverBlocksSubmission() {
        Long approvalId = draft(KWAK, DocType.GENERAL);

        // 진짜 예외를 던지게 한다(Mockito 로 흉내낸 상호작용이 아니라, FakeLlmClient 가
        // 실제로 던지고 ResilientLlmClient 가 실제로 흡수하는 경로다).
        fakeLlmClient.setExceptionToThrow(new RuntimeException("의도된 테스트 실패 - 게이트웨이 다운 흉내"));

        Optional<PreflightRecord> result = preflightService.check(approvalId, KWAK);
        assertThat(result).isEmpty();

        // ai_preflight_result 에는 아무것도 남지 않는다 - 점검 자체가 성립하지 않았다.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_preflight_result WHERE approval_id = ?", Integer.class, approvalId);
        assertThat(count).isZero();

        // ★ 상신은 사전점검과 완전히 독립된 경로다(ApprovalActionController 가 submit 을
        // preflightService 없이 바로 부른다) - 점검이 방금 실패했어도 상신 자체는
        // 영향을 받지 않고 정상 완료된다는 것을 같은 문서로 직접 증명한다.
        approvalService.submit(approvalId, KWAK);

        ApprovalDoc doc = approvalQueryService.findDoc(approvalId, KWAK);
        assertThat(doc.getStatus()).isNotEqualTo(ApprovalStatus.DRAFT);
    }

    @Test
    @DisplayName("★ basedOnRejectCount 가 집계 → 프롬프트 → 반환된 findings 까지 그대로 이어진다")
    void basedOnRejectCountSurvivesAggregationToPromptToFindings() {
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - a");
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - b");
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - c");
        insertReject(DocType.GENERAL, 7L, RejectReason.MISSING_EVIDENCE, "증빙 누락 - d");
        int expectedCount = 4;

        Long approvalId = draft(KWAK, DocType.GENERAL);

        // 이 시점에는 아직 findings 를 모른다 - 먼저 집계가 실제로 프롬프트에 그 숫자를
        // 실었는지부터 확인한다(1번째 홉: 집계 → 프롬프트).
        fakeLlmClient.setFixedResult(PreflightResult.class, passResult());
        preflightService.check(approvalId, KWAK);
        String prompt = fakeLlmClient.lastRequest().getPrompt();
        assertThat(prompt).contains(RejectReason.MISSING_EVIDENCE).contains(expectedCount + "건");

        // 잘 작성된 모델이라면 프롬프트가 제시한 건수(4)를 그대로 인용해 응답한다 -
        // FakeLlmClient 는 실제로 세지 않으므로, 그 인용을 흉내내 고정값으로 준다.
        Finding finding = new Finding();
        finding.setSeverity("HIGH");
        finding.setCategory(RejectReason.MISSING_EVIDENCE);
        finding.setMessage("증빙 자료 언급이 본문에 없습니다.");
        finding.setSuggestion("영수증 등 증빙 자료를 첨부하세요.");
        finding.setBasedOnRejectCount(expectedCount);
        fakeLlmClient.setFixedResult(PreflightResult.class, warnResult(finding));

        // 2번째 홉(프롬프트 → 반환된 findings): 서비스가 모델이 인용한 숫자를
        // 화면까지 그대로 전달하는지 확인한다.
        Optional<PreflightRecord> result = preflightService.check(approvalId, KWAK);

        assertThat(result).isPresent();
        List<Finding> findings = result.get().getFindings();
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getBasedOnRejectCount()).isEqualTo(expectedCount);
    }

    @Test
    @DisplayName("★ 권한: 문서를 볼 수 없는 사람은 사전점검도 받을 수 없다")
    void deniesPreflightToNonViewer() {
        Long approvalId = draft(KWAK, DocType.GENERAL);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.flowmate.common.exception.ApprovalAccessDeniedException.class,
                () -> preflightService.check(approvalId, OUTSIDER));
        assertThat(fakeLlmClient.getReceived()).isEmpty();
    }

    // ── 픽스처 ──────────────────────────────────────────────────

    private Long draft(Long drafterId, String docType) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(docType);
        form.setTitle("사전점검 테스트 문서 - " + docType);
        form.setContent("사전점검 배선 검증을 위한 본문입니다.");
        form.setAmount(BigDecimal.TEN);
        return approvalService.saveDraft(form, drafterId);
    }

    private void insertReject(String docType, Long deptId, String reasonCategory, String reasonText) {
        jdbcTemplate.update(
                "INSERT INTO approval_reject_history "
                        + "(approval_id, doc_type, dept_id, rejector_id, reason_category, reason_text, rejected_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                FK_DOC_ID, docType, deptId, 2L, reasonCategory, reasonText,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private PreflightResult passResult() {
        PreflightResult result = new PreflightResult();
        result.setVerdict(PreflightVerdict.PASS);
        result.setFindings(List.of());
        return result;
    }

    private PreflightResult warnResult(int basedOnRejectCount) {
        Finding finding = new Finding();
        finding.setSeverity("HIGH");
        finding.setCategory(RejectReason.MISSING_EVIDENCE);
        finding.setMessage("본문에 증빙 관련 언급이 없습니다.");
        finding.setSuggestion("증빙 자료를 첨부하거나 본문에 근거를 밝히세요.");
        finding.setBasedOnRejectCount(basedOnRejectCount);
        return warnResult(finding);
    }

    private PreflightResult warnResult(Finding finding) {
        PreflightResult result = new PreflightResult();
        result.setVerdict(PreflightVerdict.WARN);
        result.setFindings(List.of(finding));
        return result;
    }
}
