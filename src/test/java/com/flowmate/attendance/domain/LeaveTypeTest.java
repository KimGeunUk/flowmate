package com.flowmate.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 연차 유형의 한글 이름과 선택 상자 옵션. DocTypeTest 와 같은 이유로 둔다.
 *
 * 기안 화면의 연차 유형 선택 상자가 ANNUAL·HALF_AM 을 그대로 보여주고 있었다 —
 * labelOf 는 진작 있었는데 화면이 ALL(코드 목록)을 받아 쓰고 있었기 때문이다.
 * options() 가 코드와 이름을 함께 나르면 화면이 그 둘을 다시 짝지을 일이 없다.
 */
class LeaveTypeTest {

    @Test
    @DisplayName("★ 모든 연차 유형에 고유한 한글 이름이 있다 — 유형을 추가하고 이름을 빠뜨리면 여기서 걸린다")
    void everyLeaveTypeHasItsOwnLabel() {
        List<String> labels = LeaveType.ALL.stream().map(LeaveType::labelOf).toList();

        assertThat(labels).doesNotHaveDuplicates();
        // labelOf 는 모르는 값을 그대로 돌려준다 — 이름을 빠뜨리면 코드가 화면에 나간다.
        assertThat(LeaveType.ALL).allSatisfy(code ->
                assertThat(LeaveType.labelOf(code)).isNotEqualTo(code));
    }

    @Test
    @DisplayName("선택 상자 옵션은 ALL 의 순서를 지키며 코드와 한글 이름을 함께 나른다")
    void optionsCarryCodeAndLabelInDeclaredOrder() {
        List<LeaveType.Option> options = LeaveType.options();

        assertThat(options).hasSameSizeAs(LeaveType.ALL);
        assertThat(options).extracting(LeaveType.Option::getCode).containsExactlyElementsOf(LeaveType.ALL);
        assertThat(options).allSatisfy(o ->
                assertThat(o.getLabel()).isEqualTo(LeaveType.labelOf(o.getCode())));
    }

    @Test
    @DisplayName("반차 판정은 오전·오후 반차만 참이다")
    void onlyHalfDayTypesAreHalfDay() {
        assertThat(LeaveType.isHalfDay(LeaveType.HALF_AM)).isTrue();
        assertThat(LeaveType.isHalfDay(LeaveType.HALF_PM)).isTrue();
        assertThat(LeaveType.isHalfDay(LeaveType.ANNUAL)).isFalse();
        assertThat(LeaveType.isHalfDay(LeaveType.SICK)).isFalse();
        assertThat(LeaveType.isHalfDay(null)).isFalse();
    }
}
