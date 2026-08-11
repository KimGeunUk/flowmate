package com.flowmate.ai.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.domain.DraftHint;
import com.flowmate.ai.domain.DraftHintCommand;
import com.flowmate.ai.domain.DraftSuggestion;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기안 본문 제안 배선 검증. 실제 API 호출은 없다 - {@code ai.enabled} 기본값이 false 라
 * {@link FakeLlmClient} 가 배선된다.
 *
 * 사원 20(심재현, 개발팀=dept 7)로 기안한다. 개발팀 + CONTRACT 는 데모 시드가 손대지
 * 않는 조합이라, 이 테스트가 직접 심은 반려 이력이 곧 집계 결과다
 * (PreflightServiceIT 가 dept7+GENERAL 을 고른 것과 같은 이유).
 */
@SpringBootTest
@Transactional
class DraftHintServiceIT {

    private static final Long DEV_DRAFTER = 20L;   // 심재현, 개발팀 사원
    private static final Long DEV_REJECTOR = 14L;  // 신동혁 - FK 없는 필러 값
    private static final Long FK_DOC_ID = 1L;      // reject_history.approval_id FK 용

    @Autowired
    private DraftHintService draftHintService;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFake() {
        fakeLlmClient.getReceived().clear();
        fakeLlmClient.setFixedResult(DraftSuggestion.class, suggestion("[계약 개요]\n계약 상대방: [거래처명]"));
    }

    @Test
    @DisplayName("★ 근거 건수는 서버가 센 값이다 — 모델은 그 필드를 채울 수조차 없다")
    void basisComesFromServerNotFromModel() {
        insertRejects(RejectReason.MISSING_EVIDENCE, 4);
        insertRejects(RejectReason.PROCEDURE_ERROR, 2);

        Optional<DraftHint> result = draftHintService.suggest(command("계약 체결의 건", ""), DEV_DRAFTER);

        assertThat(result).isPresent();
        assertThat(result.get().getBasedOn())
                .extracting(p -> p.getReasonCategory() + ":" + p.getCount())
                .containsExactly("MISSING_EVIDENCE:4", "PROCEDURE_ERROR:2");
    }

    @Test
    @DisplayName("★ 모델의 출력 타입에는 basedOn 이 없다 — 채워 보낼 경로 자체를 없앴다")
    void modelOutputTypeCannotCarryTheBasis() {
        // 이 필드가 모델의 출력 타입에 있으면 두 가지가 잘못된다.
        //  1) 모델이 임의의 숫자를 채우고 서버가 덮어쓰는 것을 잊으면 그대로 나간다
        //  2) RejectPattern 은 불변이라 기본 생성자가 없어서, 모델이 채워 보내는
        //     순간 Jackson 이 역직렬화에 실패해 제안 전체가 버려진다
        // 실제로 2번으로 테스트가 깨져서 타입을 분리했다.
        assertThat(DraftSuggestion.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactly("serialVersionUID", "draft");
    }

    @Test
    @DisplayName("★ 프롬프트에는 반려 유형과 건수만 들어간다 — 반려 원문이 나갈 자리가 없다")
    void promptCarriesOnlyCategoriesAndCounts() {
        insertRejects(RejectReason.MISSING_EVIDENCE, 3);

        draftHintService.suggest(command("계약 체결의 건", ""), DEV_DRAFTER);

        String prompt = fakeLlmClient.lastRequest().getPrompt();
        assertThat(prompt).contains("증빙 누락", "3건");
        // 시드·테스트가 넣은 반려 원문이 프롬프트에 섞이지 않는다
        assertThat(prompt).doesNotContain("첨부해 주세요", "reason_text");
    }

    @Test
    @DisplayName("지금까지 쓴 내용을 프롬프트에 함께 넣는다 — 이미 적은 것을 지우지 않게")
    void alreadyWrittenContentIsSentAlong() {
        draftHintService.suggest(command("계약 체결의 건", "상대방은 ○○물류입니다."), DEV_DRAFTER);

        assertThat(fakeLlmClient.lastRequest().getPrompt()).contains("상대방은 ○○물류입니다.");
    }

    @Test
    @DisplayName("아무것도 안 쓴 상태로 눌러도 동작한다 — 처음부터 뼈대를 잡아 준다")
    void emptyContentIsAllowed() {
        assertThat(draftHintService.suggest(command("계약 체결의 건", ""), DEV_DRAFTER)).isPresent();
        assertThat(fakeLlmClient.lastRequest().getPrompt()).contains("아직 아무것도 쓰지 않았습니다");
    }

    @Test
    @DisplayName("문서 유형이 없으면 LLM 을 부르지 않는다")
    void missingDocTypeShortCircuits() {
        DraftHintCommand command = new DraftHintCommand();
        command.setTitle("유형을 아직 안 골랐다");

        assertThat(draftHintService.suggest(command, DEV_DRAFTER)).isEmpty();
        assertThat(fakeLlmClient.getReceived()).isEmpty();
    }

    @Test
    @DisplayName("★ 모델이 draft 를 비워 보내면 빈 결과다 — 빈 제안을 화면에 띄우지 않는다")
    void blankDraftIsTreatedAsFailure() {
        fakeLlmClient.setFixedResult(DraftSuggestion.class, suggestion("   "));

        assertThat(draftHintService.suggest(command("계약 체결의 건", ""), DEV_DRAFTER)).isEmpty();
    }

    private DraftHintCommand command(String title, String content) {
        DraftHintCommand command = new DraftHintCommand();
        command.setDocType(DocType.CONTRACT);
        command.setTitle(title);
        command.setContent(content);
        return command;
    }

    private DraftSuggestion suggestion(String draft) {
        DraftSuggestion value = new DraftSuggestion();
        value.setDraft(draft);
        return value;
    }

    /** 개발팀(7) + CONTRACT 조합에 반려 이력을 심는다 */
    private void insertRejects(String category, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO approval_reject_history "
                            + "(approval_id, doc_type, dept_id, rejector_id, reason_category, reason_text, rejected_at) "
                            + "VALUES (?, ?, 7, ?, ?, '첨부해 주세요', NOW())",
                    FK_DOC_ID, DocType.CONTRACT, DEV_REJECTOR, category);
        }
    }
}
