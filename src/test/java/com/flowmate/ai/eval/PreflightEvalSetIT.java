package com.flowmate.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.Finding;
import com.flowmate.ai.domain.PreflightRecord;
import com.flowmate.ai.domain.PreflightVerdict;
import com.flowmate.ai.feature.PreflightService;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.service.ApprovalService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고정 평가셋 6건.
 *
 * ★ {@code @Tag("llm")} - 실제 Gemini API 를 부른다. {@code pom.xml} 의 Failsafe
 * {@code excludedGroups=llm} 이 기본 빌드(mvnw clean verify, 키 없음)에서 이 클래스
 * 전체를 건너뛴다. 수동 실행:
 *
 * <pre>
 * $env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')
 * .\mvnw.cmd verify "-Dit.test=PreflightEvalSetIT" "-Dgroups=llm" `
 *     "-Dflowmate.eval.excludedGroups=" "-Dflowmate.test.ai.enabled=true"
 * </pre>
 *
 * (ai.provider 는 application.yml 기본값 gemini 를 그대로 쓴다.) ★ {@code -Dgroups=llm}
 * 만으로는 부족하다 - pom.xml 의 excludedGroups 기본값이 이미 "llm"이라, 켜는 필터
 * (groups)와 끄는 필터(excludedGroups)가 같은 태그를 가리키면 끄는 쪽이 항상 이긴다
 * (JUnit5 TagFilter 의 결합 규칙). 그래서 excludedGroups 쪽도 같이 비워야 한다
 * (pom.xml 의 {@code flowmate.eval.excludedGroups} 프로퍼티 주석 참고).
 *
 * ★ 각 사례는 서로 다른 부서+문서유형 조합을 쓴다 - {@code 50-seed-demo.sql} 의 반려
 * 편중은 dept7+PURCHASE, dept6+EXPENSE, dept5+EXPENSE, dept4+GENERAL 넷뿐이므로,
 * 여기서는 그 넷을 피해 dept7+EXPENSE(개발팀, 완전히 비어 있음)와 dept5+PURCHASE
 * (재무팀, 완전히 비어 있음)를 쓴다 - 데모 시드의 반려 이력이 전혀 섞이지 않으므로
 * 이 테스트가 직접 심은 건수가 곧 집계 건수다(PreflightServiceIT 의 dept7+GENERAL/
 * dept7+CONTRACT 선택과 같은 이유).
 *
 * ★ 4·5번(잘 작성된 문서 -> PASS)이 가장 중요하다 - 억지 지적을 잡아내는 유일한
 * 장치다. 그래서 일부러 1·2번과 같은 반려 신호(INSUFFICIENT_CONTENT·MISSING_EVIDENCE)를
 * 4번에도 심어 둔다 - "신호가 있어도 실제로 해당하지 않으면 지적하지 않는다"를
 * 증명하기 위해서다. 신호를 아예 안 주면 모델이 findings 를 비우는 것이 "지적할
 * 근거가 없어서"인지 "애초에 볼 게 없어서"인지 구분할 수 없다.
 */
@Tag("llm")
@SpringBootTest
@Transactional
class PreflightEvalSetIT {

    // 개발팀(dept 7) - EXPENSE 는 데모 시드가 손대지 않는 조합이다.
    private static final Long DEV_DRAFTER = 20L;   // 심재현, 개발팀 사원
    private static final Long DEV_REJECTOR = 14L;  // 신동혁, 개발팀 과장(필러 값 - FK 없음)

    // 재무팀(dept 5) - PURCHASE 는 데모 시드가 손대지 않는 조합이다.
    private static final Long FIN_DRAFTER = 9L;    // 강태윤, 재무팀 사원
    private static final Long FIN_REJECTOR = 7L;   // 오세훈, 재무팀 과장(필러 값 - FK 없음)

    private static final Long FK_DOC_ID = 1L; // approval_reject_history.approval_id FK 용 - 존재하기만 하면 된다

    @Autowired
    private PreflightService preflightService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("1. 목적이 '업무협의'뿐인 출장비 -> 목적 불명확 지적(WARN)")
    void case1_unclearPurpose() {
        insertRejects(DocType.EXPENSE, 7L, DEV_REJECTOR, RejectReason.INSUFFICIENT_CONTENT, 6);

        Long approvalId = draft(DEV_DRAFTER, DocType.EXPENSE,
                "출장비 정산 신청",
                "[출장 개요]\n"
                        + "출장자: 심재현\n"
                        + "출장 기간: 2026-07-20 ~ 2026-07-21 (1박 2일)\n"
                        + "출장지: 서울\n"
                        + "출장 목적: 업무협의\n\n"
                        + "[정산 내역]\n"
                        + "교통비: 84,000원 (KTX 왕복)\n"
                        + "숙박비: 110,000원 (1박)\n"
                        + "식비: 45,000원\n"
                        + "합계: 239,000원\n\n"
                        + "[첨부 자료]\n"
                        + "영수증 3매 첨부",
                new BigDecimal("239000"));

        Optional<PreflightRecord> result = preflightService.check(approvalId, DEV_DRAFTER);

        printResult(1, "목적 불명확(WARN 기대)", result, approvalId);
        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo(PreflightVerdict.WARN);
        assertThat(result.get().getFindings()).isNotEmpty();
        assertThat(result.get().getFindings())
                .anyMatch(f -> matchesCategory(f, RejectReason.INSUFFICIENT_CONTENT));
    }

    @Test
    @DisplayName("2. 첨부 없는 출장비 정산 -> 증빙 누락 지적(WARN)")
    void case2_missingEvidence() {
        insertRejects(DocType.EXPENSE, 7L, DEV_REJECTOR, RejectReason.MISSING_EVIDENCE, 5);

        Long approvalId = draft(DEV_DRAFTER, DocType.EXPENSE,
                "출장비 정산 신청 - 부산 지사 방문",
                "[출장 개요]\n"
                        + "출장자: 심재현\n"
                        + "출장 기간: 2026-07-25 ~ 2026-07-26 (1박 2일)\n"
                        + "출장지: 부산\n"
                        + "출장 목적: 부산 지사 신규 물류관리 시스템 도입 관련 실무 협의 및 현장 점검\n\n"
                        + "[정산 내역]\n"
                        + "교통비: 98,000원 (KTX 왕복)\n"
                        + "숙박비: 120,000원 (1박)\n"
                        + "식비: 50,000원\n"
                        + "합계: 268,000원\n\n"
                        + "[첨부 자료]\n"
                        + "없음 - 영수증을 분실하여 첨부하지 못했습니다.",
                new BigDecimal("268000"));

        Optional<PreflightRecord> result = preflightService.check(approvalId, DEV_DRAFTER);

        printResult(2, "증빙 누락(WARN 기대)", result, approvalId);
        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo(PreflightVerdict.WARN);
        assertThat(result.get().getFindings()).isNotEmpty();
        assertThat(result.get().getFindings())
                .anyMatch(f -> matchesCategory(f, RejectReason.MISSING_EVIDENCE));
    }

    @Test
    @DisplayName("3. 부서 평균 4배 금액의 구매 -> 금액 과다 지적(WARN)")
    void case3_excessiveAmount() {
        insertRejects(DocType.PURCHASE, 5L, FIN_REJECTOR, RejectReason.EXCESSIVE_AMOUNT, 7);

        Long approvalId = draft(FIN_DRAFTER, DocType.PURCHASE,
                "노트북 20대 일괄 구매 요청",
                "[구매 개요]\n"
                        + "품목: 노트북(15형) 20대\n"
                        + "용도: 신규 입사자 및 노후 장비 교체\n"
                        + "구매처: (주)한빛전자유통\n\n"
                        + "[금액]\n"
                        + "총 구매 금액: 24,000,000원\n"
                        + "[참고] 최근 6개월간 재무팀 평균 구매 금액: 약 6,000,000원\n\n"
                        + "[비고]\n"
                        + "별도 사유 설명 없이 일괄 구매를 요청합니다.",
                new BigDecimal("24000000"));

        Optional<PreflightRecord> result = preflightService.check(approvalId, FIN_DRAFTER);

        printResult(3, "금액 과다(WARN 기대)", result, approvalId);
        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo(PreflightVerdict.WARN);
        assertThat(result.get().getFindings()).isNotEmpty();
        assertThat(result.get().getFindings())
                .anyMatch(f -> matchesCategory(f, RejectReason.EXCESSIVE_AMOUNT));
    }

    @Test
    @DisplayName("4. ★ 잘 작성된 출장비 -> PASS (지적 없음) - 억지 지적을 잡는 핵심 사례")
    void case4_wellWrittenExpensePasses() {
        // 1·2번과 같은 신호(INSUFFICIENT_CONTENT·MISSING_EVIDENCE)를 일부러 심는다 -
        // 신호가 있어도 이 문서에는 해당하지 않으므로 지적하지 말아야 한다.
        insertRejects(DocType.EXPENSE, 7L, DEV_REJECTOR, RejectReason.INSUFFICIENT_CONTENT, 6);
        insertRejects(DocType.EXPENSE, 7L, DEV_REJECTOR, RejectReason.MISSING_EVIDENCE, 4);

        Long approvalId = draft(DEV_DRAFTER, DocType.EXPENSE,
                "출장비 정산 신청 - 대구 협력사 계약 협의",
                "[출장 개요]\n"
                        + "출장자: 심재현\n"
                        + "출장 기간: 2026-08-03 ~ 2026-08-04 (1박 2일)\n"
                        + "출장지: 대구\n"
                        + "출장 목적: (주)대성기계 신규 부품 공급 계약 조건 협의 및 견본품 품질 검수\n\n"
                        + "[정산 내역]\n"
                        + "교통비: 76,000원 (KTX 왕복)\n"
                        + "숙박비: 95,000원 (1박)\n"
                        + "식비: 38,000원\n"
                        + "합계: 209,000원\n\n"
                        + "[첨부 자료]\n"
                        + "KTX 승차권 영수증, 호텔 결제 영수증, 식대 법인카드 사용 내역서 각 1부 첨부",
                new BigDecimal("209000"));

        Optional<PreflightRecord> result = preflightService.check(approvalId, DEV_DRAFTER);

        printResult(4, "잘 작성된 출장비(PASS 기대)", result, approvalId);
        assertThat(result).isPresent();
        boolean pass = PreflightVerdict.PASS.equals(result.get().getVerdict())
                || result.get().getFindings() == null
                || result.get().getFindings().isEmpty();
        assertThat(pass)
                .as("잘 작성된 문서인데 findings 가 있습니다: %s", result.get().getFindings())
                .isTrue();
    }

    @Test
    @DisplayName("5. ★ 잘 작성된 구매 요청 -> PASS (지적 없음) - 억지 지적을 잡는 핵심 사례")
    void case5_wellWrittenPurchasePasses() {
        // 3번과 같은 신호(EXCESSIVE_AMOUNT)를 일부러 심는다 - 이번 금액은 평균 이내이므로
        // 신호가 있어도 지적하지 말아야 한다.
        insertRejects(DocType.PURCHASE, 5L, FIN_REJECTOR, RejectReason.EXCESSIVE_AMOUNT, 7);

        Long approvalId = draft(FIN_DRAFTER, DocType.PURCHASE,
                "사무용 복합기 교체 구매 요청",
                "[구매 개요]\n"
                        + "품목: 레이저 복합기 1대\n"
                        + "용도: 재무팀 사무실 공용 복합기 교체 (기존 장비 고장)\n"
                        + "구매처: (주)오피스코리아\n\n"
                        + "[금액]\n"
                        + "총 구매 금액: 850,000원\n"
                        + "[참고] 최근 6개월간 재무팀 평균 구매 금액: 약 900,000원 (평균 이내)\n\n"
                        + "[비고]\n"
                        + "기존 복합기가 고장(수리 견적서 첨부, 부품 단종으로 수리 불가 확인)으로 사용이 불가능해 "
                        + "동일 사양의 장비로 교체합니다. 구매 견적서 1부 첨부.",
                new BigDecimal("850000"));

        Optional<PreflightRecord> result = preflightService.check(approvalId, FIN_DRAFTER);

        printResult(5, "잘 작성된 구매 요청(PASS 기대)", result, approvalId);
        assertThat(result).isPresent();
        boolean pass = PreflightVerdict.PASS.equals(result.get().getVerdict())
                || result.get().getFindings() == null
                || result.get().getFindings().isEmpty();
        assertThat(pass)
                .as("잘 작성된 문서인데 findings 가 있습니다: %s", result.get().getFindings())
                .isTrue();
    }

    @Test
    @DisplayName("6. ★ 본문이 첨부를 언급하지 않고 실제 첨부도 0개인 고액 구매 -> 증빙 누락 지적(WARN)")
    void case6_zeroAttachmentsWithoutAnyMentionInBody() {
        // ★ 2번과 무엇이 다른가: 2번 본문에는 "영수증을 분실하여 첨부하지 못했습니다"라고
        //   적혀 있다. 즉 작성자가 스스로 밝힌 것을 모델이 읽은 것이라, 사실은
        //   "본문 독해"를 검증할 뿐이다. 그렇게 적어 두는 사람은 이미 알고 있는 사람이고,
        //   정작 놓치는 사람은 첨부 얘기를 아예 안 쓴다.
        //
        // ★ 이 사례는 실제 사용 중에 드러난 구멍이다. 첨부 0개짜리 100만원 구매요청을
        //   점검했는데 증빙 누락이 나오지 않았다. 모델 잘못이 아니었다 - 프롬프트에
        //   첨부 정보가 아예 없었고, 규칙이 "본문에 없는 사실을 추측하지 말라"였으니
        //   규칙을 지킨 것이다. PreflightService 가 [첨부 파일] 구간을 넘기도록 고쳤고,
        //   이 테스트가 그 구간이 사라지면 걸리게 한다.
        insertRejects(DocType.PURCHASE, 5L, FIN_REJECTOR, RejectReason.MISSING_EVIDENCE, 9);

        Long approvalId = draft(FIN_DRAFTER, DocType.PURCHASE,
                "개발용 노트북 구매 요청",
                "[구매 개요]\n"
                        + "품목: 개발용 노트북 1대\n"
                        + "용도: 기존 장비 고장으로 인한 교체\n"
                        + "구매처: (주)한빛전자유통\n\n"
                        + "[금액]\n"
                        + "총 구매 금액: 2,400,000원\n\n"
                        + "[비고]\n"
                        + "기존 노트북이 부팅되지 않아 업무에 지장이 있어 교체를 요청합니다.",
                new BigDecimal("2400000"));
        // 첨부를 하나도 넣지 않는다 - 본문도 첨부를 언급하지 않는다.
        // 모델이 증빙 누락을 짚으려면 [첨부 파일] 구간을 근거로 삼는 수밖에 없다.

        Optional<PreflightRecord> result = preflightService.check(approvalId, FIN_DRAFTER);

        printResult(6, "첨부 0개·본문 무언급(WARN 기대)", result, approvalId);
        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo(PreflightVerdict.WARN);
        assertThat(result.get().getFindings()).isNotEmpty();
        assertThat(result.get().getFindings())
                .as("첨부가 0개인데 증빙 누락을 짚지 못했습니다: %s", result.get().getFindings())
                .anyMatch(f -> matchesCategory(f, RejectReason.MISSING_EVIDENCE));
    }

    /**
     * finding.category 가 기대한 반려 유형을 가리키는지 검사한다.
     *
     * ★ 출력 스키마 예시 자체가 category 를 코드(INSUFFICIENT_CONTENT)가
     * 아니라 자유 텍스트("목적불명확")로 보여준다 - 이 필드가 반드시 {@link RejectReason}
     * 상수 문자열 그대로 나온다는 보장은 스키마 어디에도 없다. 실제로 case2 실행에서
     * 모델이 "증빙 누락"(RejectReason.labelOf(MISSING_EVIDENCE) 와 동일한 한글 라벨)을
     * 돌려준 것을 확인했다 - 의미상 정확한 답인데 코드 문자열과 완전히 같지 않다는
     * 이유로 실패시키면 "실제 문자열이 아니라 실질(substance)을 본다"는 이 평가셋의
     * 원칙을 어긴다. 그래서 코드 문자열과 한글 라벨 둘 다 인정한다.
     */
    private boolean matchesCategory(Finding finding, String expectedReasonCode) {
        String category = finding.getCategory();
        if (category == null) {
            return false;
        }
        return category.equals(expectedReasonCode) || category.equals(RejectReason.labelOf(expectedReasonCode));
    }

    // ── 픽스처 ──────────────────────────────────────────────────

    private Long draft(Long drafterId, String docType, String title, String content, BigDecimal amount) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(docType);
        form.setTitle(title);
        form.setContent(content);
        form.setAmount(amount);
        return approvalService.saveDraft(form, drafterId);
    }

    private void insertRejects(String docType, Long deptId, Long rejectorId, String reasonCategory, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO approval_reject_history "
                            + "(approval_id, doc_type, dept_id, rejector_id, reason_category, reason_text, rejected_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    FK_DOC_ID, docType, deptId, rejectorId, reasonCategory,
                    "평가셋 픽스처 - " + reasonCategory + " " + i,
                    Timestamp.valueOf(LocalDateTime.now()));
        }
    }

    /**
     * 실행 결과를 콘솔에 출력한다 - 평가 기록(저장소 밖)을 손으로 채울 때
     * 실제 모델 출력을 옮겨 적기 위한 것이다(수동 실행 로그를 읽어 기록한다).
     * 토큰 사용량은 {@code PreflightService.check()} 가 버리므로(응답 파싱 후 텍스트만
     * 쓴다) {@code ai_call_log} 에서 이 호출이 방금 남긴 최신 행을 다시 읽는다.
     */
    private void printResult(int caseNo, String label, Optional<PreflightRecord> result, Long approvalId) {
        System.out.println("========== [EVAL CASE " + caseNo + "] " + label + " ==========");
        if (result.isEmpty()) {
            System.out.println("결과 없음 - AI 호출 실패(Optional.empty()). ai_call_log.error_msg 확인 필요.");
        } else {
            PreflightRecord record = result.get();
            System.out.println("verdict=" + record.getVerdict());
            List<Finding> findings = record.getFindings();
            if (findings == null || findings.isEmpty()) {
                System.out.println("findings=[] (지적 없음)");
            } else {
                for (Finding f : findings) {
                    System.out.println("  - severity=" + f.getSeverity()
                            + " category=" + f.getCategory()
                            + " basedOnRejectCount=" + f.getBasedOnRejectCount()
                            + "\n    message=" + f.getMessage()
                            + "\n    suggestion=" + f.getSuggestion());
                }
            }
        }
        try {
            Map<String, Object> log = jdbcTemplate.queryForMap(
                    "SELECT input_tokens, output_tokens, latency_ms, success_yn "
                            + "FROM ai_call_log WHERE feature = 'PREFLIGHT' AND approval_id = ? "
                            + "ORDER BY log_id DESC LIMIT 1",
                    approvalId);
            System.out.println("ai_call_log: input_tokens=" + log.get("input_tokens")
                    + " output_tokens=" + log.get("output_tokens")
                    + " latency_ms=" + log.get("latency_ms")
                    + " success_yn=" + log.get("success_yn"));
        } catch (Exception e) {
            System.out.println("ai_call_log 조회 실패 (참고용 출력이므로 테스트에는 영향 없음): " + e.getMessage());
        }
        System.out.println("==================================================");
    }
}
