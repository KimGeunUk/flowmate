package com.flowmate.org.domain;

/**
 * 사원 목록 검색 조건. Spring MVC 가 요청 파라미터를 이 객체의 setter 로 바인딩하고,
 * MyBatis 가 getter 로 #{keyword} · #{limit} · #{offset} 을 읽는다.
 *
 * 값 보정을 setter 에서 끝내는 이유:
 * 잘못된 page=0 이나 size=100000 이 SQL 까지 흘러가지 않게 막는 곳을 한 군데로 모은다.
 *
 * 참고: select 의 "전체" 옵션은 value="" 로 보내는데, Spring 의 String→Long 변환기가
 * 빈 문자열을 null 로 바꿔주므로 deptId 에 별도 처리가 필요하지 않다.
 */
public class EmployeeSearchCond {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private int page = 1;
    private int size = DEFAULT_SIZE;
    private String keyword;
    private Long deptId;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size < 1) {
            this.size = DEFAULT_SIZE;
            return;
        }
        this.size = Math.min(size, MAX_SIZE);
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            this.keyword = null;
            return;
        }
        this.keyword = keyword.trim();
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    /** SQL 의 LIMIT 값 */
    public int getLimit() {
        return size;
    }

    /**
     * SQL 의 OFFSET 값.
     *
     * long 으로 계산하는 이유: page 는 위쪽 상한이 없다(요청 파라미터를 손으로 고치면
     * 얼마든 커진다). int 로 곱하면 Java 는 예외 없이 음수로 감싸고, 그 값이
     * OFFSET 으로 들어가도 오류가 나지 않아 조용히 빈 결과가 된다.
     * 오버플로 방지를 이 객체가 책임진다 — 호출하는 Service 의 순서에 의존하지 않는다.
     */
    public long getOffset() {
        return (long) (page - 1) * size;
    }

    /**
     * LIKE 패턴에 넣을 검색어. `\` `%` `_` 를 이스케이프한다.
     *
     * 이스케이프하지 않으면 사용자가 입력한 % 와 _ 가 와일드카드로 해석된다.
     * 사원번호에 밑줄이 있는 경우(EMP_2024_01) _ 가 "임의의 한 글자" 가 되어
     * 의도보다 넓은 결과가 나온다. 주입 위험은 없지만(바인딩 파라미터) 결과가 조용히 틀어진다.
     *
     * 화면 표시용은 getKeyword() 를 쓴다. 이스케이프된 값을 폼에 되돌리면
     * 사용자가 입력하지 않은 역슬래시가 보인다.
     *
     * `\` 를 가장 먼저 치환해야 한다. 나중에 하면 앞서 넣은 이스케이프 문자를 또 이스케이프한다.
     */
    public String getKeywordEscaped() {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_");
    }
}
