package com.flowmate.common.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 화면에 날짜·시각을 적는 형식을 한곳에 모은다.
 *
 * ★ JSP 에서 {@code ${doc.draftedAt}} 로 바로 찍으면 {@code LocalDateTime.toString()}
 *   이 나온다 - {@code 2026-08-11T10:51:08.547246} 처럼 ISO 의 {@code T} 구분자와
 *   마이크로초가 그대로 보인다. 시드 데이터는 초 단위가 0 이라 {@code 2026-03-05T10:00}
 *   으로 짧게 나오는 바람에 눈에 덜 띄었고, 앱이 실제로 만든 문서에서만 길게 나왔다.
 *
 * ★ JSTL 의 {@code fmt:formatDate} 로는 못 고친다. 그 태그는 {@code java.util.Date}
 *   전용이라 {@code java.time} 타입을 받으면 예외가 난다.
 *
 * 그래서 도메인 객체가 {@code getXxxLabel()} 로 완성된 문자열을 넘긴다 -
 * {@code getStatusLabel()}·{@code getDocTypeLabel()} 과 같은 방식이다. 화면은 받은
 * 것을 그리기만 한다.
 */
public final class DateLabels {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private DateLabels() {
    }

    /** "2026-08-11 10:51". null 이면 빈 문자열 - 화면이 "null" 을 찍지 않게 한다 */
    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    /**
     * "10:51". 날짜가 이미 같은 줄에 있는 표(내 근태)에서 쓴다 -
     * 한 줄에 같은 날짜를 세 번 적을 이유가 없다.
     */
    public static String time(LocalDateTime value) {
        return value == null ? "" : value.format(TIME);
    }
}
