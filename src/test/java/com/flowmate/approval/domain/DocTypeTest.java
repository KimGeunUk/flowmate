package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 문서 유형의 한글 이름과 선택 상자 옵션.
 *
 * 이 클래스는 상수 모음이라 원래 테스트를 두지 않았는데, 화면이 코드(EXPENSE)를
 * 그대로 보여주는 문제를 고치면서 두 가지가 테스트할 값이 되었다:
 *   1) 모든 유형에 한글 이름이 있는가 (없으면 조용히 "일반문서"로 떨어진다)
 *   2) 선택 상자 옵션이 코드와 이름을 같이 나르는가
 */
class DocTypeTest {

    @Test
    @DisplayName("★ 모든 문서 유형에 고유한 한글 이름이 있다 — 유형을 추가하고 이름을 빠뜨리면 여기서 걸린다")
    void everyDocTypeHasItsOwnLabel() {
        // labelOf 는 모르는 값을 "일반문서"로 떨어뜨린다. 그 폴백은 DB 에 옛 유형이
        // 남아 있을 때를 위한 것이지, 새 유형을 추가하며 이름을 빠뜨렸을 때
        // 봐주라고 있는 것이 아니다. 그런데 화면에는 아무 예외 없이 "일반문서"가
        // 뜨므로 눈으로는 알아채기 어렵다 — 그래서 여기서 잡는다.
        List<String> labels = DocType.ALL.stream().map(DocType::labelOf).toList();

        assertThat(labels).doesNotHaveDuplicates();
        assertThat(labels).allSatisfy(label -> assertThat(label).isNotBlank());
    }

    @Test
    @DisplayName("선택 상자 옵션은 ALL 의 순서를 지키며 코드와 한글 이름을 함께 나른다")
    void optionsCarryCodeAndLabelInDeclaredOrder() {
        List<DocType.Option> options = DocType.options();

        assertThat(options).hasSameSizeAs(DocType.ALL);
        assertThat(options).extracting(DocType.Option::getCode).containsExactlyElementsOf(DocType.ALL);
        assertThat(options).allSatisfy(o ->
                assertThat(o.getLabel()).isEqualTo(DocType.labelOf(o.getCode())));
    }

    @Test
    @DisplayName("★ 선택 상자 옵션이 '금액 칸을 쓰는가'를 함께 나른다 — 화면 스크립트가 유형 코드를 몰라도 되게")
    void optionsCarryFieldVisibilityFlags() {
        // 기안 화면은 고른 유형에 따라 금액 칸을 감춘다. 그 판정을 스크립트에
        // 적어 두면(EXPENSE 나 PURCHASE 면 보인다) 유형을 추가할 때 스크립트도
        // 같이 고쳐야 하는데 그걸 강제하는 장치가 없다 — labelOf 를 빠뜨렸던
        // 것과 같은 함정이다. 그래서 판정을 여기 두고 옵션이 실어 나른다.
        assertThat(DocType.options()).allSatisfy(o -> {
            assertThat(o.isUsesAmount()).isEqualTo(DocType.usesAmount(o.getCode()));
            assertThat(o.isUsesLeaveFields()).isEqualTo(DocType.usesLeaveFields(o.getCode()));
        });
    }

    @Test
    @DisplayName("금액은 지출결의·구매요청만 쓰고, 연차 입력칸은 연차신청만 쓴다")
    void onlyMoneyDocTypesUseAmount() {
        assertThat(DocType.usesAmount(DocType.EXPENSE)).isTrue();
        assertThat(DocType.usesAmount(DocType.PURCHASE)).isTrue();
        assertThat(DocType.usesAmount(DocType.LEAVE)).isFalse();
        assertThat(DocType.usesAmount(DocType.CONTRACT)).isFalse();
        assertThat(DocType.usesAmount(DocType.GENERAL)).isFalse();

        // 연차 입력칸을 쓰는 유형은 정확히 하나여야 한다 — 둘이 되면 화면이
        // 같은 영역을 두 유형에서 보여주게 되고, 서버의 LEAVE 분기와 어긋난다.
        assertThat(DocType.ALL.stream().filter(DocType::usesLeaveFields).toList())
                .containsExactly(DocType.LEAVE);
    }

    @Test
    @DisplayName("알 수 없는 유형은 일반문서로 떨어진다 — DB 에 옛 유형이 남아 있어도 화면이 깨지지 않는다")
    void unknownDocTypeFallsBackToGeneral() {
        assertThat(DocType.labelOf("SOMETHING_ELSE")).isEqualTo("일반문서");
        assertThat(DocType.labelOf(null)).isEqualTo("일반문서");
    }
}
