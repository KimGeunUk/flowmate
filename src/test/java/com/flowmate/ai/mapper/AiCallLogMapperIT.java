package com.flowmate.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiCallLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 호출 건수 계수 검증.
 *
 * ★ 절대 건수를 단정하지 않고 **증분**을 단정한다. ai_call_log 는 다른 테스트가
 *   남긴 행이 이미 있을 수 있는 공용 테이블이라, "3건이다"는 실행 순서에 따라
 *   깨진다. 이 테스트가 스스로 만든 행만 세는 것이 조건을 통제하는 방법이다.
 *
 * ★ 경계값을 LocalDateTime.now() 로 잡지 않는다. Java 의 now() 는 나노초,
 *   PostgreSQL 의 timestamp 는 마이크로초 정밀도라 방금 넣은 행이 경계보다
 *   과거로 판정될 수 있다 - 아주 가끔 깨지는 테스트가 된다. 그래서 경계도
 *   행의 called_at 도 이 테스트가 직접 지정한다.
 */
@SpringBootTest
@Transactional
class AiCallLogMapperIT {

    @Autowired
    private AiCallLogMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("★ 경계 이후 행만 센다 - 경계와 같은 시각은 포함한다")
    void countsOnlyRowsAtOrAfterTheBoundary() {
        LocalDateTime boundary = LocalDateTime.of(2030, 1, 15, 0, 0);
        long before = mapper.countSince(boundary);

        insertAt(boundary.minusSeconds(1));  // 경계 직전 - 세지 않는다
        insertAt(boundary);                  // 경계와 같은 시각 - 센다
        insertAt(boundary.plusHours(5));     // 경계 이후 - 센다

        assertThat(mapper.countSince(boundary) - before).isEqualTo(2);
    }

    @Test
    @DisplayName("실패한 호출도 센다 - API 를 때린 것은 성공과 같다")
    void failedCallsCountToo() {
        LocalDateTime boundary = LocalDateTime.of(2030, 2, 20, 0, 0);
        long before = mapper.countSince(boundary);

        insertAt(boundary.plusMinutes(1), "N");
        insertAt(boundary.plusMinutes(2), "N");

        assertThat(mapper.countSince(boundary) - before).isEqualTo(2);
    }

    @Test
    @DisplayName("insert 가 남긴 행도 같은 방식으로 세어진다 - 운영 경로와 같은 행이다")
    void rowsWrittenByInsertAreCounted() {
        LocalDateTime boundary = LocalDateTime.now().minusMinutes(1);
        long before = mapper.countSince(boundary);

        AiCallLog entry = new AiCallLog();
        entry.setFeature("SUMMARY");
        entry.setPromptVersion("v1");
        entry.setSuccessYn("Y");
        mapper.insert(entry);

        assertThat(mapper.countSince(boundary) - before).isEqualTo(1);
    }

    private void insertAt(LocalDateTime calledAt) {
        insertAt(calledAt, "Y");
    }

    /** called_at 을 직접 지정해야 하므로 매퍼가 아니라 JdbcTemplate 으로 넣는다 */
    private void insertAt(LocalDateTime calledAt, String successYn) {
        jdbcTemplate.update(
                "INSERT INTO ai_call_log (feature, prompt_version, success_yn, called_at) "
                        + "VALUES ('SUMMARY', 'v1', ?, ?)",
                successYn, calledAt);
    }
}
