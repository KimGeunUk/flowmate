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
    @DisplayName("알 수 없는 유형은 일반문서로 떨어진다 — DB 에 옛 유형이 남아 있어도 화면이 깨지지 않는다")
    void unknownDocTypeFallsBackToGeneral() {
        assertThat(DocType.labelOf("SOMETHING_ELSE")).isEqualTo("일반문서");
        assertThat(DocType.labelOf(null)).isEqualTo("일반문서");
    }
}
