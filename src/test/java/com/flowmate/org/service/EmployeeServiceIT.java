package com.flowmate.org.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.common.web.Page;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;

/**
 * 페이지 범위 보정 검증.
 *
 * 이 보정이 없으면 startPage > endPage 가 되어 pagination.jsp 의
 * c:forEach begin/end 가 예외 없이 링크를 0개 그린다 — 페이징이 조용히 죽는다.
 */
@SpringBootTest
@Transactional
class EmployeeServiceIT {

    @Autowired
    private EmployeeService employeeService;

    @Test
    @DisplayName("전체 페이지를 넘는 페이지를 요청하면 마지막 페이지로 보정하고 결과를 채워 준다")
    void clampsPageBeyondLastPage() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setPage(11);

        Page<Employee> paging = employeeService.search(cond);

        assertThat(paging.getTotalCount()).isEqualTo(20);
        assertThat(paging.getTotalPages()).isEqualTo(2);
        assertThat(paging.getPage()).isEqualTo(2);
        assertThat(paging.getStartPage()).isLessThanOrEqualTo(paging.getEndPage());
        assertThat(paging.getContent()).isNotEmpty();
        // 보정된 값이 화면 폼으로도 되돌아가야 다음 클릭이 정상 범위에서 출발한다
        assertThat(cond.getPage()).isEqualTo(2);
    }

    @Test
    @DisplayName("검색을 좁혀 결과가 한 페이지로 줄어도 페이징 링크 범위가 유효하다")
    void clampsWhenSearchNarrowsResults() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setPage(2);
        cond.setKeyword("곽수빈");

        Page<Employee> paging = employeeService.search(cond);

        assertThat(paging.getTotalCount()).isEqualTo(1);
        assertThat(paging.getTotalPages()).isEqualTo(1);
        assertThat(paging.getPage()).isEqualTo(1);
        assertThat(paging.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("결과가 0건이면 1페이지로 보고 빈 목록을 반환한다")
    void handlesEmptyResult() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setKeyword("존재하지않는이름");

        Page<Employee> paging = employeeService.search(cond);

        assertThat(paging.getTotalCount()).isZero();
        assertThat(paging.getTotalPages()).isEqualTo(1);
        assertThat(paging.getContent()).isEmpty();
    }
}
