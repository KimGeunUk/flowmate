package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mask.SensitiveDataMasker;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 호출 직전에 프롬프트를 마스킹해서 위임하는 데코레이터를 검증한다 (계획서 3 D8).
 *
 * 이 데코레이터 하나만으로 "원문이 안 나간다" 를 완전히 증명하지는 못한다 -
 * 체인 순서(캐싱보다 안쪽)까지 함께 봐야 하고 그건 Task 6 의 LlmChainIT 가 한다.
 * 여기서는 이 데코레이터 자신의 책임 - 위임 직전 마스킹 - 만 검증한다.
 */
class MaskingLlmClientTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    private LlmRequest request(String prompt) {
        LlmRequest r = new LlmRequest();
        r.setFeature(AiFeature.SUMMARY);
        r.setPromptVersion("v1");
        r.setPrompt(prompt);
        r.setEmpId(7L);
        r.setApprovalId(42L);
        return r;
    }

    @Test
    @DisplayName("위임하기 전에 민감정보를 토큰으로 치환한다")
    void masksSensitiveDataBeforeDelegating() {
        FakeLlmClient fake = new FakeLlmClient();
        MaskingLlmClient masking = new MaskingLlmClient(fake, masker);

        masking.complete(request("주민등록번호는 901231-1234567 입니다"));

        LlmRequest received = fake.lastRequest();
        assertThat(received.getPrompt()).doesNotContain("901231-1234567");
        assertThat(received.getPrompt()).contains("[[RRN_1]]");
    }

    @Test
    @DisplayName("prompt 이외의 필드는 그대로 전달한다")
    void preservesOtherFieldsUnchanged() {
        FakeLlmClient fake = new FakeLlmClient();
        MaskingLlmClient masking = new MaskingLlmClient(fake, masker);

        masking.complete(request("평범한 본문"));

        LlmRequest received = fake.lastRequest();
        assertThat(received.getFeature()).isEqualTo(AiFeature.SUMMARY);
        assertThat(received.getPromptVersion()).isEqualTo("v1");
        assertThat(received.getEmpId()).isEqualTo(7L);
        assertThat(received.getApprovalId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("응답은 복원하지 않고 원본 그대로 돌려준다 - 정책 1")
    void returnsDelegateResponseUnchanged() {
        FakeLlmClient fake = new FakeLlmClient();
        LlmResponse fixed = new LlmResponse();
        fixed.setText("요약: [[RRN_1]] 토큰은 그대로 둔다");
        fixed.setModel("fake-model");
        fixed.setInputTokens(5);
        fixed.setOutputTokens(5);
        fake.setFixedResponse(fixed);
        MaskingLlmClient masking = new MaskingLlmClient(fake, masker);

        Optional<LlmResponse> result = masking.complete(request("주민등록번호는 901231-1234567 입니다"));

        assertThat(result).isPresent();
        assertThat(result.get().getText()).isEqualTo("요약: [[RRN_1]] 토큰은 그대로 둔다");
    }
}
