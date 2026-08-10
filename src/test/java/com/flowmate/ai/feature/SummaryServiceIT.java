package com.flowmate.ai.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.domain.SummaryResult;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;
import java.util.LinkedHashMap;
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
 * 기능 1(문서 요약) 배선 검증 (계획서 5 Task 3). {@code ai.enabled=false}(기본값)라서
 * 실제 API 호출 없이 {@link FakeLlmClient} 로 전체 체인(Caching→Masking→Logging→
 * Resilient→Fake)을 태운다.
 *
 * ★ 완료 기준(설계서 §9 5-1): 같은 문서를 두 번 요약하면 두 번째는 캐시에서
 * 나온다 - {@code secondCallIsServedFromCache} 가 {@code ai_result_cache.hit_count}
 * 가 1이 되는 것으로 이를 증명한다.
 *
 * 문서 1(EXP-2026-0001, 곽수빈(18) 기안 DRAFT)을 쓴다 - 기안자 본인만 볼 수 있는
 * 상태라 권한 검사(기안자만 통과)를 자연스럽게 함께 검증할 수 있다.
 */
@SpringBootTest
@Transactional
class SummaryServiceIT {

    private static final Long DOC_ID = 1L;
    private static final Long DRAFTER = 18L;     // 문서 1의 기안자 - 볼 수 있다
    private static final Long OUTSIDER = 5L;     // 기안자도 결재선도 아니다 - 볼 수 없다

    @Autowired
    private SummaryService summaryService;

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

        SummaryResult fixed = new SummaryResult();
        fixed.setSummary(List.of("부산 지사 방문 출장비 정산 요청입니다."));
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("amount", "540,000원");
        facts.put("period", "2026-03-15 ~ 03-17");
        fixed.setKeyFacts(facts);
        fakeLlmClient.setFixedResult(SummaryResult.class, fixed);
    }

    @Test
    @DisplayName("★ 완료 기준: 같은 문서를 두 번 요약하면 두 번째는 캐시에서 나오고 hit_count 가 1이 된다")
    void secondCallIsServedFromCache() {
        Optional<SummaryResult> first = summaryService.summarize(DOC_ID, DRAFTER);
        Optional<SummaryResult> second = summaryService.summarize(DOC_ID, DRAFTER);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().getSummary()).isEqualTo(second.get().getSummary());

        // FakeLlmClient 가 실제로 위임받은 횟수 - 두 번째는 캐시가 가로채 위임하지 않았다.
        assertThat(fakeLlmClient.getReceived()).hasSize(1);

        // 이 테스트(트랜잭션)가 만든 SUMMARY 캐시 행은 이 문서 하나분 하나뿐이다 -
        // 두 호출이 같은 cache_key 로 수렴했다는 뜻이고, 그래서 정확히 한 행에
        // hit_count=1 이 찍힌다(두 번째 호출이 그 행을 찾아 증가시켰다).
        Integer hitCount = jdbcTemplate.queryForObject(
                "SELECT hit_count FROM ai_result_cache WHERE feature = 'SUMMARY'", Integer.class);
        assertThat(hitCount).isEqualTo(1);
    }

    @Test
    @DisplayName("요약 결과가 keyFacts 를 그대로 옮긴다")
    void summaryCarriesKeyFacts() {
        Optional<SummaryResult> result = summaryService.summarize(DOC_ID, DRAFTER);

        assertThat(result).isPresent();
        assertThat(result.get().getKeyFacts()).containsEntry("amount", "540,000원");
    }

    @Test
    @DisplayName("★ 권한: 문서를 볼 수 없는 사람은 요약도 볼 수 없다 - ApprovalQueryService.findDoc 을 그대로 태운다")
    void deniesSummaryToNonViewer() {
        assertThatThrownBy(() -> summaryService.summarize(DOC_ID, OUTSIDER))
                .isInstanceOf(ApprovalAccessDeniedException.class);

        assertThat(fakeLlmClient.getReceived()).isEmpty();   // 권한 검사가 LLM 호출보다 먼저다
    }

    @Test
    @DisplayName("없는 문서를 요약하려 하면 예외가 그대로 전달된다")
    void propagatesNotFound() {
        assertThatThrownBy(() -> summaryService.summarize(999999L, DRAFTER))
                .isInstanceOf(ApprovalNotFoundException.class);
    }

    @Test
    @DisplayName("★ D8: AI 호출이 실패해도 예외 없이 빈 결과다 - 화면이 안내 문구로 대체할 수 있다")
    void aiFailureYieldsEmptyNotException() {
        fakeLlmClient.setExceptionToThrow(new RuntimeException("의도된 테스트 실패"));

        Optional<SummaryResult> result = summaryService.summarize(DOC_ID, DRAFTER);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("★ D8: 구조화 출력이 JSON 이 아니어도 예외 없이 빈 결과다")
    void malformedStructuredOutputYieldsEmptyNotException() {
        // echoPrompt 는 FakeLlmClient.complete() 에서 outputType 검사보다 먼저 걸린다
        // (그 클래스 주석 참조) - 프롬프트 원문(사람이 읽는 지시문, JSON 이 아니다)을
        // 그대로 응답 텍스트로 돌려주므로, 스키마를 벗어난 응답을 손쉽게 흉내낼 수 있다.
        fakeLlmClient.setEchoPrompt(true);

        Optional<SummaryResult> result = summaryService.summarize(DOC_ID, DRAFTER);

        assertThat(result).isEmpty();
    }
}
