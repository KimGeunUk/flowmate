package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결재함 탭의 한글 이름·건수·"할 일 여부".
 *
 * DocTypeTest 와 같은 이유로 둔다 — 탭을 추가하고 이름을 빠뜨리면 화면에
 * 조용히 "기안"이 뜬다.
 */
class BoxTabTest {

    @Test
    @DisplayName("★ 모든 탭에 고유한 한글 이름이 있다")
    void everyTabHasItsOwnLabel() {
        List<String> labels = BoxTab.ALL.stream().map(BoxTab::labelOf).toList();

        assertThat(labels).doesNotHaveDuplicates();
        // labelOf 는 모르는 값을 "기안"으로 떨어뜨린다. 그 폴백에 걸리는 탭은
        // drafted 하나여야 한다 — 둘이면 이름을 빠뜨린 것이다.
        assertThat(BoxTab.ALL.stream().filter(t -> "기안".equals(BoxTab.labelOf(t))).toList())
                .containsExactly(BoxTab.DRAFTED);
    }

    @Test
    @DisplayName("★ 할 일 탭은 대기·반려 둘뿐이다 — 기안·완료는 지나간 기록이다")
    void onlyPendingAndRejectedAreTodo() {
        assertThat(BoxTab.ALL.stream().filter(BoxTab::isTodo).toList())
                .containsExactly(BoxTab.PENDING, BoxTab.REJECTED);
    }

    @Test
    @DisplayName("탭 옵션은 ALL 의 순서를 지키며 이름·건수·할일여부를 함께 나른다")
    void optionsCarryLabelAndCount() {
        ApprovalBoxCounts counts = counts(6, 3, 12, 1);

        List<BoxTab.Option> options = BoxTab.options(counts);

        assertThat(options).extracting(BoxTab.Option::getCode).containsExactlyElementsOf(BoxTab.ALL);
        assertThat(options).extracting(BoxTab.Option::getLabel)
                .containsExactly("기안", "대기", "완료", "반려");
        assertThat(options).extracting(BoxTab.Option::getCount)
                .containsExactly(6L, 3L, 12L, 1L);
        assertThat(options).extracting(BoxTab.Option::isTodo)
                .containsExactly(false, true, false, true);
    }

    @Test
    @DisplayName("★ 집계에 실패해 counts 가 없어도 옵션은 만들어진다 — 배지만 0 이 되고 탭은 남는다")
    void survivesMissingCounts() {
        // 인터셉터가 집계에 실패하면 모델에 boxCounts 가 실리지 않는다.
        // 그때 탭까지 사라지면 화면을 쓸 수 없게 된다 — 배지 하나 때문에
        // 결재함이 못 쓰게 되는 것이 이 방어의 요지다.
        List<BoxTab.Option> options = BoxTab.options(null);

        assertThat(options).hasSameSizeAs(BoxTab.ALL);
        assertThat(options).extracting(BoxTab.Option::getCount).containsOnly(0L);
    }

    @Test
    @DisplayName("countOf 는 탭 코드로 건수를 꺼내고, 모르는 코드는 기안으로 떨어진다")
    void countOfResolvesByTabCode() {
        ApprovalBoxCounts counts = counts(6, 3, 12, 1);

        assertThat(counts.countOf(BoxTab.DRAFTED)).isEqualTo(6);
        assertThat(counts.countOf(BoxTab.PENDING)).isEqualTo(3);
        assertThat(counts.countOf(BoxTab.DONE)).isEqualTo(12);
        assertThat(counts.countOf(BoxTab.REJECTED)).isEqualTo(1);
        assertThat(counts.countOf("something-else")).isEqualTo(6);
        assertThat(counts.countOf(null)).isEqualTo(6);
    }

    @Test
    @DisplayName("할 일 여부는 대기·반려 건수로만 정해진다 — 기안·완료가 아무리 많아도 할 일은 아니다")
    void hasTodoIgnoresRecordTabs() {
        assertThat(counts(99, 0, 99, 0).isHasTodo()).isFalse();
        assertThat(counts(0, 1, 0, 0).isHasTodo()).isTrue();
        assertThat(counts(0, 0, 0, 1).isHasTodo()).isTrue();
    }

    private ApprovalBoxCounts counts(long drafted, long pending, long done, long rejected) {
        ApprovalBoxCounts c = new ApprovalBoxCounts();
        c.setDrafted(drafted);
        c.setPending(pending);
        c.setDone(done);
        c.setRejected(rejected);
        return c;
    }
}
