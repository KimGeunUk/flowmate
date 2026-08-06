package com.flowmate.common.web;

import java.util.List;

/**
 * 목록 화면의 페이징 상태를 담는다.
 * JSP 의 common/pagination.jsp 가 이 객체의 getter 만 보고 링크를 그린다.
 *
 * 페이지 번호는 1부터 시작한다. DB의 OFFSET 계산은 검색 조건 객체가 담당하고,
 * 이 클래스는 "몇 페이지짜리이고 어느 블록을 보여줄지"만 계산한다.
 */
public class Page<T> {

    /** 페이징 링크에 한 번에 보여주는 페이지 개수 */
    private static final int BLOCK_SIZE = 10;

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalCount;

    public Page(List<T> content, int page, int size, long totalCount) {
        if (content == null) {
            throw new IllegalArgumentException("content는 null일 수 없습니다");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page는 1 이상이어야 합니다: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount는 0 이상이어야 합니다: " + totalCount);
        }
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalCount = totalCount;
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * 전체 페이지 수 계산. Service 가 Page 를 만들기 전에 요청 페이지를 보정할 때도 필요하므로
     * static 으로 빼 둔다. 같은 식을 Service 에 복사하면 두 곳이 어긋날 수 있다.
     *
     * 결과가 0건이어도 1페이지로 본다. 화면에 "1 / 0" 같은 표시가 나오지 않게 한다.
     */
    public static int totalPagesOf(long totalCount, int size) {
        if (totalCount == 0) {
            return 1;
        }
        return (int) ((totalCount + size - 1) / size);
    }

    public int getTotalPages() {
        return totalPagesOf(totalCount, size);
    }

    public boolean isFirst() {
        return page <= 1;
    }

    public boolean isLast() {
        return page >= getTotalPages();
    }

    public int getStartPage() {
        return ((page - 1) / BLOCK_SIZE) * BLOCK_SIZE + 1;
    }

    public int getEndPage() {
        return Math.min(getStartPage() + BLOCK_SIZE - 1, getTotalPages());
    }

    public boolean isHasPrevBlock() {
        return getStartPage() > 1;
    }

    public boolean isHasNextBlock() {
        return getEndPage() < getTotalPages();
    }

    public int getPrevBlockPage() {
        return Math.max(getStartPage() - 1, 1);
    }

    public int getNextBlockPage() {
        return Math.min(getEndPage() + 1, getTotalPages());
    }
}
