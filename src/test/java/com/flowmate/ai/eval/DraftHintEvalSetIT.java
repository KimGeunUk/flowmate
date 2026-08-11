package com.flowmate.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.DraftHint;
import com.flowmate.ai.domain.DraftHintCommand;
import com.flowmate.ai.feature.DraftHintService;
import com.flowmate.approval.domain.DocType;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기안 본문 제안 고정 평가셋 3건.
 *
 * ★ {@code @Tag("llm")} - 실제 Gemini API 를 부른다. 기본 빌드에서는 제외된다.
 * 수동 실행은 {@link PreflightEvalSetIT} 와 같다:
 *
 * <pre>
 * $env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')
 * .\mvnw.cmd verify "-Dit.test=DraftHintEvalSetIT" "-Dgroups=llm" `
 *     "-Dflowmate.eval.excludedGroups=" "-Dai.enabled=true"
 * </pre>
 *
 * ★ 여기 있는 셋은 단위 테스트로 확인할 수 없는 것들이다 - 배선이 아니라 **모델이
 * 실제로 그렇게 행동하는가**를 본다. 특히 2·3번은 이 기능이 해서는 안 되는 일을
 * 고정한다: 쓰던 내용을 지우는 것, 그리고 없던 개인정보 토큰을 만들어 내는 것.
 */
@Tag("llm")
@SpringBootTest
@Transactional
class DraftHintEvalSetIT {

    private static final Long DEV_DRAFTER = 20L;   // 심재현, 개발팀 사원

    /** 마스킹 계층이 만드는 토큰 모양. 모델이 이런 것을 새로 지어내면 안 된다 */
    private static final Pattern MASK_TOKEN = Pattern.compile("\\[\\[[A-Z_0-9]+\\]\\]");

    @Autowired
    private DraftHintService draftHintService;

    @Test
    @DisplayName("1. 빈 상태에서 부르면 그 문서 유형의 뼈대를 잡아 준다")
    void case1_scaffoldFromScratch() {
        DraftHint hint = suggest("부산 지사 방문 출장비 정산", "");

        // 지출결의라면 목적·내역·증빙이 뼈대에 들어가야 한다
        assertThat(hint.getDraft()).contains("[");   // 자리표시자를 쓴다
        assertThat(hint.getDraft().length()).isGreaterThan(50);
    }

    @Test
    @DisplayName("★ 2. 이미 쓴 내용을 지우지 않는다 — 작성자가 적은 사실은 초안에 그대로 살아 있다")
    void case2_keepsWhatTheAuthorAlreadyWrote() {
        // 이 기능이 할 수 있는 가장 나쁜 일이 "쓰던 걸 말없이 덮어쓰는 것"이다.
        // 화면도 [본문에 넣기] 를 눌러야 적용되게 막아 두었지만, 모델 쪽에서도
        // 이미 적힌 사실이 사라지지 않아야 한다.
        DraftHint hint = suggest("부산 지사 방문 출장비 정산",
                "부산 지사에 다녀왔습니다. 교통비 84,000원.");

        assertThat(hint.getDraft())
                .as("작성자가 적은 사실(부산 지사, 84,000원)이 초안에 남아 있어야 한다")
                .contains("부산 지사")
                .contains("84,000");
    }

    @Test
    @DisplayName("★ 3. 없는 개인정보 토큰을 지어내지 않는다 — 실제로 겪은 결함의 회귀")
    void case3_neverInventsMaskingTokens() {
        // 처음 프롬프트에는 "[[RRN_1]] 같은 토큰은 그대로 두라"는 문장이 **리터럴
        // 예시**로 들어 있었다. 요약·사전점검 프롬프트는 같은 문장을 써도 문제가
        // 없었는데, 이 기능은 모델에게 "자리표시자를 만들라"고 시키기 때문에 두
        // 종류를 혼동해 출장자 이름 자리에 [[RRN_1]] 을 채워 넣었다 - 입력에
        // 주민번호가 하나도 없었는데도.
        //
        // 작성자 입장에서는 자기가 쓰지도 않은 값이 지워진 것으로 보인다.
        DraftHint hint = suggest("거래처 미팅 식대 정산", "");

        assertThat(MASK_TOKEN.matcher(hint.getDraft()).find())
                .as("입력에 없던 마스킹 토큰이 초안에 나타나면 안 된다:%n%s", hint.getDraft())
                .isFalse();
    }

    private DraftHint suggest(String title, String content) {
        DraftHintCommand command = new DraftHintCommand();
        command.setDocType(DocType.EXPENSE);
        command.setTitle(title);
        command.setContent(content);

        Optional<DraftHint> result = draftHintService.suggest(command, DEV_DRAFTER);
        assertThat(result).as("실제 API 호출이 성공해야 한다").isPresent();
        return result.get();
    }
}
