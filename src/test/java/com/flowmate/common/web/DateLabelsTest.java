package com.flowmate.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.attendance.domain.Attendance;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 화면에 나가는 날짜 형식.
 *
 * JSP 에서 LocalDateTime 을 그대로 찍으면 ISO 의 T 구분자와 마이크로초가 노출된다
 * (2026-08-11T10:51:08.547246). 시드 데이터는 초가 0 이라 짧게 나오는 바람에 눈에
 * 덜 띄었고, 앱이 실제로 만든 문서에서만 길게 나왔다 - README 스크린샷을 찍다가
 * 발견했다.
 */
class DateLabelsTest {

    @Test
    @DisplayName("★ 초·나노초를 잘라내고 T 구분자를 공백으로 바꾼다")
    void dateTimeDropsSecondsAndIsoSeparator() {
        LocalDateTime withMicros = LocalDateTime.of(2026, 8, 11, 10, 51, 8, 547246000);

        assertThat(DateLabels.dateTime(withMicros)).isEqualTo("2026-08-11 10:51");
        assertThat(DateLabels.dateTime(withMicros)).doesNotContain("T", ".");
    }

    @Test
    @DisplayName("시각만 필요한 표(내 근태)에서는 날짜를 빼고 적는다")
    void timeKeepsOnlyHourAndMinute() {
        assertThat(DateLabels.time(LocalDateTime.of(2026, 8, 11, 9, 5))).isEqualTo("09:05");
    }

    @Test
    @DisplayName("★ null 은 빈 문자열이다 — 화면에 \"null\" 이 찍히면 안 된다")
    void nullBecomesEmptyString() {
        // 퇴근 미등록·미처리 결재선처럼 값이 없는 경우가 정상 경로에 있다.
        assertThat(DateLabels.dateTime(null)).isEmpty();
        assertThat(DateLabels.time(null)).isEmpty();
    }

    @Test
    @DisplayName("도메인 객체가 그 형식을 화면에 노출한다")
    void domainObjectsExposeFormattedLabels() {
        ApprovalDoc doc = new ApprovalDoc();
        doc.setDraftedAt(LocalDateTime.of(2026, 3, 5, 10, 0));
        assertThat(doc.getDraftedAtLabel()).isEqualTo("2026-03-05 10:00");

        Attendance attendance = new Attendance();
        attendance.setCheckIn(LocalDateTime.of(2026, 8, 11, 9, 0));
        assertThat(attendance.getCheckInLabel()).isEqualTo("09:00");
        assertThat(attendance.getCheckOutLabel())
                .as("퇴근 미등록은 정상 경로다")
                .isEmpty();
    }
}
