package com.flowmate.org.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.common.web.Page;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;
import com.flowmate.org.mapper.EmployeeMapper;

@Service
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    public EmployeeService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    /**
     * 목록과 건수를 한 트랜잭션에서 읽어 Page 로 조립한다.
     * 건수 조회를 Controller 가 따로 부르지 않게 하는 것이 목적이다 —
     * 두 쿼리가 갈라지면 조건이 어긋나 페이징이 깨진다.
     */
    @Transactional(readOnly = true)
    public Page<Employee> search(EmployeeSearchCond cond) {
        long totalCount = employeeMapper.countSearch(cond);

        // ★ 목록 조회 전에 요청 페이지를 실제 마지막 페이지로 보정한다.
        //
        // 11페이지를 보던 중 검색을 좁히면 pagination.jsp 가 #searchForm 을 재전송하면서
        // page=11 을 그대로 보낸다. totalCount 가 20으로 줄면 startPage(11) > endPage(2) 가 되어
        // <c:forEach begin=11 end=2> 가 예외 없이 링크를 0개 그린다 — 페이징이 조용히 죽는다.
        //
        // 건수 조회가 목록 조회보다 먼저이므로 여기서 보정하면 재조회가 필요없다.
        int totalPages = Page.totalPagesOf(totalCount, cond.getSize());
        if (cond.getPage() > totalPages) {
            cond.setPage(totalPages);
        }

        List<Employee> content = employeeMapper.search(cond);
        return new Page<>(content, cond.getPage(), cond.getSize(), totalCount);
    }
}
