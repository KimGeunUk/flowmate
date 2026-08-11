package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 입력 상한이 **실제 DB 컬럼 크기와 같은지** 확인한다.
 *
 * ★ 이 테스트가 필요한 이유: 숫자가 세 곳(화면 maxlength · DB 컬럼 · 서버 검증)에
 *   흩어져 있다. 어느 하나만 바뀌면 나머지와 어긋나는데, 그 증상이 "화면에서는
 *   막았는데 API 로는 통과" 또는 "서버가 막았는데 DB 는 더 받을 수 있었다"처럼
 *   조용하다. 그래서 코드가 아니라 **DB 에 물어본다** - 스키마를 고치면 여기서 걸린다.
 */
@SpringBootTest
class ApprovalLimitsIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("★ 상수가 실제 DB 컬럼 크기와 같다 — 스키마를 고치면 여기서 걸린다")
    void constantsMatchActualColumnSizes() {
        assertThat(columnLength("approval_doc", "title")).isEqualTo(ApprovalLimits.TITLE);
        assertThat(columnLength("leave_request", "reason")).isEqualTo(ApprovalLimits.REASON);
        assertThat(columnLength("approval_line", "comment")).isEqualTo(ApprovalLimits.COMMENT);
        assertThat(columnLength("approval_history", "comment")).isEqualTo(ApprovalLimits.COMMENT);
    }

    @Test
    @DisplayName("본문만 DB 제약이 없다 — 상한은 AI 프롬프트 크기를 위한 것이다")
    void contentHasNoDatabaseLimitByDesign() {
        // TEXT 컬럼은 character_maximum_length 가 null 이다.
        assertThat(columnLength("approval_doc", "content")).isNull();
        assertThat(ApprovalLimits.CONTENT).isPositive();
    }

    @Test
    @DisplayName("경계값: 상한과 같으면 통과하고 하나만 넘어도 거부한다")
    void boundaryIsInclusive() {
        assertThatCode(() -> ApprovalLimits.check("A".repeat(200), ApprovalLimits.TITLE, "제목"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> ApprovalLimits.check("A".repeat(201), ApprovalLimits.TITLE, "제목"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200자")
                .hasMessageContaining("201자");
    }

    @Test
    @DisplayName("null 은 통과시킨다 — 값의 유무는 필수 검증이 따로 판단한다")
    void nullPassesLengthCheck() {
        assertThatCode(() -> ApprovalLimits.check(null, ApprovalLimits.TITLE, "제목"))
                .doesNotThrowAnyException();
    }

    private Integer columnLength(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = ?",
                Integer.class, table, column);
    }
}
