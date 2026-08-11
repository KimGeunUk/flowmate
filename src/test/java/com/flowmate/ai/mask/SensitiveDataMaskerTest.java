package com.flowmate.ai.mask;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 외부 LLM 으로 나가는 텍스트에서 민감정보 6종(주민번호·계좌·전화·사업자번호·카드·이메일)을
 * 치환하는지 검증한다.
 *
 * ★ 오탐 허용·미탐 불허라는 비대칭을 13번({@link #docNoLooksLikeAccountAndThatIsAccepted()}),
 *   14번({@link #rrnIsMaskedEvenInsideLongerDigits()}) 테스트가 고정한다.
 *   나중에 "정확도를 높이자"며 패턴을 좁히더라도 이 둘은 계속 통과해야 한다 — 통과하지
 *   않게 됐다면 패턴을 좁힌 것이 잘못이지, 테스트가 틀린 것이 아니다.
 */
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    @DisplayName("주민등록번호를 [[RRN_1]] 로 치환하고 원문은 남기지 않는다")
    void masksResidentRegistrationNumber() {
        MaskResult result = masker.mask("주민등록번호는 901231-1234567 입니다");

        assertThat(result.getMasked()).isEqualTo("주민등록번호는 [[RRN_1]] 입니다");
        assertThat(result.getMasked()).doesNotContain("901231-1234567");
    }

    @Test
    @DisplayName("계좌번호를 [[ACCT_1]] 로 치환한다")
    void masksAccountNumber() {
        MaskResult result = masker.mask("계좌번호 110-234-567890 로 입금해주세요");

        assertThat(result.getMasked()).isEqualTo("계좌번호 [[ACCT_1]] 로 입금해주세요");
    }

    @Test
    @DisplayName("휴대폰 번호를 [[PHONE_1]] 로 치환한다")
    void masksMobilePhone() {
        MaskResult result = masker.mask("연락처는 010-1234-5678 입니다");

        assertThat(result.getMasked()).isEqualTo("연락처는 [[PHONE_1]] 입니다");
    }

    @Test
    @DisplayName("사업자등록번호를 [[BIZ_1]] 로 치환한다")
    void masksBusinessNumber() {
        MaskResult result = masker.mask("사업자번호 123-45-67890 확인 요망");

        assertThat(result.getMasked()).isEqualTo("사업자번호 [[BIZ_1]] 확인 요망");
    }

    @Test
    @DisplayName("카드번호를 [[CARD_1]] 로 치환한다")
    void masksCardNumber() {
        MaskResult result = masker.mask("카드번호 1234-5678-9012-3456 로 결제했습니다");

        assertThat(result.getMasked()).isEqualTo("카드번호 [[CARD_1]] 로 결제했습니다");
    }

    @Test
    @DisplayName("이메일을 [[EMAIL_1]] 로 치환한다")
    void masksEmail() {
        MaskResult result = masker.mask("담당자 이메일은 hong@flowmate.co.kr 입니다");

        assertThat(result.getMasked()).isEqualTo("담당자 이메일은 [[EMAIL_1]] 입니다");
    }

    @Test
    @DisplayName("같은 종류가 여러 번 나오면 각각 다른 번호를 붙인다")
    void numbersEachOccurrenceSeparately() {
        MaskResult result = masker.mask("010-1111-2222 로 먼저 연락했고 010-3333-4444 로 다시 연락했다");

        assertThat(result.getMasked()).isEqualTo("[[PHONE_1]] 로 먼저 연락했고 [[PHONE_2]] 로 다시 연락했다");
    }

    @Test
    @DisplayName("같은 값이 반복되면 같은 토큰을 쓴다 - _1, _2 로 따로 세지 않는다")
    void sameValueGetsSameToken() {
        MaskResult result = masker.mask("010-1234-5678 로 연락주세요. 다시 한 번, 010-1234-5678 입니다");

        assertThat(result.getMasked()).isEqualTo("[[PHONE_1]] 로 연락주세요. 다시 한 번, [[PHONE_1]] 입니다");
    }

    @Test
    @DisplayName("한 문장에 주민번호·계좌·이메일이 섞여 있어도 셋 다 치환한다")
    void masksMultipleTypesInOneText() {
        MaskResult result = masker.mask("주민번호 901231-1234567 계좌 110-234-567890 이메일 hong@flowmate.co.kr");

        assertThat(result.getMasked())
                .isEqualTo("주민번호 [[RRN_1]] 계좌 [[ACCT_1]] 이메일 [[EMAIL_1]]");
    }

    @Test
    @DisplayName("민감정보가 없는 일반 텍스트는 그대로 둔다")
    void leavesOrdinaryTextUntouched() {
        String text = "3월 출장비 정산 540,000원";

        MaskResult result = masker.mask(text);

        assertThat(result.getMasked()).isEqualTo(text);
    }

    @Test
    @DisplayName("null 과 빈 문자열은 예외 없이 그대로 돌려준다")
    void handlesNullAndEmpty() {
        assertThat(masker.mask(null).getMasked()).isNull();
        assertThat(masker.mask("").getMasked()).isEmpty();
    }

    @Test
    @DisplayName("restore 로 마스킹된 텍스트를 원문으로 되돌릴 수 있다")
    void restoresWhenAsked() {
        String original = "주민등록번호는 901231-1234567 입니다";
        MaskResult result = masker.mask(original);

        String restored = masker.restore(result.getMasked(), result.getMapping());

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("★ 문서번호처럼 생긴 숫자가 계좌로 오인돼도 그것이 설계다 - 테스트가 통과한다")
    void docNoLooksLikeAccountAndThatIsAccepted() {
        // "2026-01-0001" 은 EXP-2026-0001 같은 문서번호의 숫자 부분과 같은 모양이다.
        // 계좌번호 패턴(\d{2,6}-\d{2,6}-\d{2,8})이 넓어서 이런 문서번호도 계좌로 잡는다.
        // 오탐이지만 의도된 것이다 - 미탐(개인정보 누출)보다 훨씬 안전한 쪽으로 치우친 설계다.
        MaskResult result = masker.mask("문서번호 2026-01-0001 은 계좌번호가 아니다");

        assertThat(result.getMasked()).contains("[[ACCT_1]]");
        assertThat(result.getMasked()).doesNotContain("2026-01-0001");
    }

    @Test
    @DisplayName("★ 공백 없이 한글에 바로 붙어도 주민번호를 잡는다 - 단어 경계에 의존하지 않는다")
    void rrnIsMaskedEvenInsideLongerDigits() {
        MaskResult result = masker.mask("주민번호901231-1234567입니다");

        assertThat(result.getMasked()).isEqualTo("주민번호[[RRN_1]]입니다");
    }
}
