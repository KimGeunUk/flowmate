package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결재 문서 상태·결재선 상태의 한글 이름.
 *
 * DocTypeTest 와 같은 이유로 둔다. 화면은 상태에 따라 배지 색을 이미 구분하고
 * 있었으므로(status--draft 등) 글자가 DRAFT 로 나와도 "깨져 보이지" 않았고,
 * 그래서 근태 화면이 한글을 쓰는 동안 결재 화면 세 곳이 영문으로 남아 있는 것을
 * 아무도 알아채지 못했다. 색이 맞으면 눈이 글자를 지나친다.
 *
 * labelOf 는 모르는 값을 그대로 돌려주므로, 상태를 추가하고 이름을 빠뜨려도
 * 예외 없이 영문이 그대로 화면에 나간다 — 그것을 여기서 잡는다.
 */
class StatusLabelTest {

    @Test
    @DisplayName("★ 모든 문서 상태에 고유한 한글 이름이 있다")
    void everyApprovalStatusHasItsOwnLabel() {
        List<String> labels = ApprovalStatus.ALL.stream().map(ApprovalStatus::labelOf).toList();

        assertThat(labels).doesNotHaveDuplicates();
        // 이름을 빠뜨리면 labelOf 가 코드를 그대로 돌려준다 — 그것을 잡는 단정이다.
        assertThat(ApprovalStatus.ALL).allSatisfy(code ->
                assertThat(ApprovalStatus.labelOf(code)).isNotEqualTo(code));
    }

    @Test
    @DisplayName("★ 모든 결재선 상태에 고유한 한글 이름이 있다")
    void everyLineStatusHasItsOwnLabel() {
        List<String> labels = LineStatus.ALL.stream().map(LineStatus::labelOf).toList();

        assertThat(labels).doesNotHaveDuplicates();
        assertThat(LineStatus.ALL).allSatisfy(code ->
                assertThat(LineStatus.labelOf(code)).isNotEqualTo(code));
    }

    @Test
    @DisplayName("알 수 없는 상태는 받은 값을 그대로 돌려준다 — DB 에 옛 값이 있어도 화면이 깨지지 않는다")
    void unknownStatusPassesThrough() {
        assertThat(ApprovalStatus.labelOf("SOMETHING_ELSE")).isEqualTo("SOMETHING_ELSE");
        assertThat(LineStatus.labelOf("SOMETHING_ELSE")).isEqualTo("SOMETHING_ELSE");
        assertThat(ApprovalStatus.labelOf(null)).isNull();
        assertThat(LineStatus.labelOf(null)).isNull();
    }

    @Test
    @DisplayName("도메인 객체가 그 이름을 화면에 노출한다 — JSP 가 코드를 다시 해석하지 않아도 되게")
    void domainObjectsExposeTheLabel() {
        ApprovalDoc doc = new ApprovalDoc();
        doc.setStatus(ApprovalStatus.PENDING);
        assertThat(doc.getStatusLabel()).isEqualTo("결재중");

        ApprovalLine line = new ApprovalLine();
        line.setStatus(LineStatus.CURRENT);
        assertThat(line.getStatusLabel()).isEqualTo("결재 차례");
    }
}
