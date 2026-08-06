package com.flowmate.org.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmployeeSearchCondTest {

    @Test
    @DisplayName("아무것도 설정하지 않으면 1페이지 10건, offset 0이다")
    void defaultsToFirstPage() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        assertThat(cond.getPage()).isEqualTo(1);
        assertThat(cond.getSize()).isEqualTo(10);
        assertThat(cond.getOffset()).isEqualTo(0);
        assertThat(cond.getLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("페이지 번호가 0이나 음수로 들어오면 1로 보정한다")
    void clampsPageToAtLeastOne() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setPage(0);
        assertThat(cond.getPage()).isEqualTo(1);

        cond.setPage(-5);
        assertThat(cond.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("size가 상한을 넘으면 100으로 자르고 0 이하면 기본값 10으로 되돌린다")
    void clampsSizeToAllowedRange() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setSize(1000);
        assertThat(cond.getSize()).isEqualTo(100);

        cond.setSize(0);
        assertThat(cond.getSize()).isEqualTo(10);

        cond.setSize(30);
        assertThat(cond.getSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 제거하고, 공백만 있으면 null로 만든다")
    void normalizesKeyword() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setKeyword("  곽수빈  ");
        assertThat(cond.getKeyword()).isEqualTo("곽수빈");

        cond.setKeyword("   ");
        assertThat(cond.getKeyword()).isNull();

        cond.setKeyword("");
        assertThat(cond.getKeyword()).isNull();

        cond.setKeyword(null);
        assertThat(cond.getKeyword()).isNull();
    }

    @Test
    @DisplayName("offset은 (페이지 - 1) * size 다")
    void offsetFollowsPageAndSize() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setPage(3);
        cond.setSize(10);

        assertThat(cond.getOffset()).isEqualTo(20);
    }

    @Test
    @DisplayName("페이지 번호가 아주 커도 offset 이 음수로 뒤집히지 않는다")
    void offsetDoesNotOverflowIntoNegative() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setPage(300000000);

        // int 로 계산하면 2999999990 이 -1294967306 으로 감싸고,
        // 그 값이 OFFSET 에 들어가도 예외가 나지 않는다.
        assertThat(cond.getOffset()).isEqualTo(2999999990L);
        assertThat(cond.getOffset()).isPositive();
    }

    @Test
    @DisplayName("LIKE 와일드카드 문자를 이스케이프하고 원본 검색어는 그대로 유지한다")
    void escapesLikeWildcards() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        cond.setKeyword("EMP_2024");
        assertThat(cond.getKeyword()).isEqualTo("EMP_2024");
        assertThat(cond.getKeywordEscaped()).isEqualTo("EMP\\_2024");

        cond.setKeyword("50%");
        assertThat(cond.getKeywordEscaped()).isEqualTo("50\\%");

        // 역슬래시를 먼저 치환하지 않으면 이중 이스케이프가 깨진다
        cond.setKeyword("a\\b");
        assertThat(cond.getKeywordEscaped()).isEqualTo("a\\\\b");

        cond.setKeyword(null);
        assertThat(cond.getKeywordEscaped()).isNull();
    }
}
