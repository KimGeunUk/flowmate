package com.flowmate.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    @DisplayName("결과가 0건이면 전체 페이지는 1이고 첫 페이지이면서 마지막 페이지다")
    void emptyResultHasOnePage() {
        Page<String> page = new Page<>(Collections.emptyList(), 1, 10, 0);

        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isTrue();
        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("20건을 10건씩 나누면 2페이지가 된다")
    void exactDivisionHasNoExtraPage() {
        Page<String> page = new Page<>(List.of("a"), 1, 10, 20);

        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isFalse();
    }

    @Test
    @DisplayName("21건을 10건씩 나누면 3페이지가 된다 (나머지가 한 페이지를 더 만든다)")
    void remainderCreatesOneMorePage() {
        Page<String> page = new Page<>(List.of("a"), 3, 10, 21);

        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    @DisplayName("첫 블록에서는 시작 페이지가 1이고 이전 블록이 없다")
    void firstBlockHasNoPrevious() {
        Page<String> page = new Page<>(List.of("a"), 1, 10, 250);

        assertThat(page.getStartPage()).isEqualTo(1);
        assertThat(page.getEndPage()).isEqualTo(10);
        assertThat(page.isHasPrevBlock()).isFalse();
        assertThat(page.isHasNextBlock()).isTrue();
        assertThat(page.getNextBlockPage()).isEqualTo(11);
    }

    @Test
    @DisplayName("11페이지는 두 번째 블록이므로 11~20을 보여주고 이전 블록은 10으로 간다")
    void secondBlockRange() {
        Page<String> page = new Page<>(List.of("a"), 11, 10, 250);

        assertThat(page.getStartPage()).isEqualTo(11);
        assertThat(page.getEndPage()).isEqualTo(20);
        assertThat(page.isHasPrevBlock()).isTrue();
        assertThat(page.getPrevBlockPage()).isEqualTo(10);
        assertThat(page.getNextBlockPage()).isEqualTo(21);
    }

    @Test
    @DisplayName("블록의 끝 페이지는 전체 페이지 수를 넘지 않는다")
    void endPageIsClampedToTotalPages() {
        Page<String> page = new Page<>(List.of("a"), 1, 10, 35);

        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getEndPage()).isEqualTo(4);
        assertThat(page.isHasNextBlock()).isFalse();
    }

    @Test
    @DisplayName("페이지 번호가 1보다 작으면 생성 시점에 거부한다")
    void rejectsPageBelowOne() {
        assertThatThrownBy(() -> new Page<>(List.of("a"), 0, 10, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }
}
