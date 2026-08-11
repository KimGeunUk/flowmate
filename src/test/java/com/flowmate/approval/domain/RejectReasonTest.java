package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 반려 유형의 한글 이름과 선택 상자 옵션.
 *
 * DocTypeTest·LeaveTypeTest 와 같은 이유로 둔다. 반려 모달이 ALL(코드 목록)을
 * 그대로 받아 INSUFFICIENT_CONTENT 를 화면에 내보내고 있었다 — labelOf 는 진작
 * 있었는데 화면만 안 쓰고 있었다. 하필 이것은 결재자가 **반드시 골라야 하는**
 * 필수 항목이라, 영문 코드를 보고 고르게 되어 있었다.
 *
 * isValid 의 null 처리도 여기서 지킨다 — ALL 은 List.of 로 만든 불변 리스트라
 * contains(null) 이 false 가 아니라 NullPointerException 을 던진다. 화면에서
 * 유형을 고르지 않고 보내면 정확히 그 null 이 들어온다.
 */
class RejectReasonTest {

    @Test
    @DisplayName("★ 모든 반려 유형에 고유한 한글 이름이 있다 — 유형을 추가하고 이름을 빠뜨리면 여기서 걸린다")
    void everyReasonHasItsOwnLabel() {
        List<String> labels = RejectReason.ALL.stream().map(RejectReason::labelOf).toList();

        assertThat(labels).doesNotHaveDuplicates();
        assertThat(labels).allSatisfy(label -> assertThat(label).isNotBlank());
        // labelOf 는 모르는 값을 "기타"로 떨어뜨린다. 그 폴백은 DB 에 옛 유형이 남아
        // 있을 때를 위한 것이지, 새 유형의 이름을 빠뜨렸을 때 봐주라고 있는 게 아니다.
        assertThat(RejectReason.ALL.stream().filter(c -> "기타".equals(RejectReason.labelOf(c))).toList())
                .containsExactly(RejectReason.OTHER);
    }

    @Test
    @DisplayName("선택 상자 옵션은 ALL 의 순서를 지키며 코드와 한글 이름을 함께 나른다")
    void optionsCarryCodeAndLabelInDeclaredOrder() {
        List<RejectReason.Option> options = RejectReason.options();

        assertThat(options).hasSameSizeAs(RejectReason.ALL);
        assertThat(options).extracting(RejectReason.Option::getCode)
                .containsExactlyElementsOf(RejectReason.ALL);
        assertThat(options).allSatisfy(o ->
                assertThat(o.getLabel()).isEqualTo(RejectReason.labelOf(o.getCode())));
    }

    @Test
    @DisplayName("유형을 고르지 않고 보내면(null) 예외가 아니라 false 다")
    void nullIsInvalidWithoutThrowing() {
        assertThat(RejectReason.isValid(null)).isFalse();
        assertThat(RejectReason.isValid("")).isFalse();
        assertThat(RejectReason.isValid("SOMETHING_ELSE")).isFalse();
        assertThat(RejectReason.isValid(RejectReason.MISSING_EVIDENCE)).isTrue();
    }
}
