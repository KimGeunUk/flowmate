package com.flowmate.org.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;

/**
 * 사원 조회 동적 SQL 검증. 시드 20명(개발팀 7명)을 전제로 한다.
 */
@SpringBootTest
@Transactional
class EmployeeMapperIT {

    private static final long DEPT_ID_DEV_TEAM = 7L;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Test
    @DisplayName("조건이 없으면 전체 20명 중 첫 페이지 10명만 반환한다")
    void returnsFirstPageWithoutConditions() {
        EmployeeSearchCond cond = new EmployeeSearchCond();

        assertThat(employeeMapper.countSearch(cond)).isEqualTo(20);
        assertThat(employeeMapper.search(cond)).hasSize(10);
    }

    @Test
    @DisplayName("두 번째 페이지는 남은 10명을 반환하고 첫 페이지와 겹치지 않는다")
    void secondPageDoesNotOverlapFirst() {
        EmployeeSearchCond first = new EmployeeSearchCond();
        EmployeeSearchCond second = new EmployeeSearchCond();
        second.setPage(2);

        List<String> firstNos = employeeMapper.search(first).stream().map(Employee::getEmpNo).toList();
        List<String> secondNos = employeeMapper.search(second).stream().map(Employee::getEmpNo).toList();

        assertThat(secondNos).hasSize(10).doesNotContainAnyElementsOf(firstNos);
    }

    @Test
    @DisplayName("부서로 걸러면 그 부서 사원만 나온다")
    void filtersByDepartment() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setDeptId(DEPT_ID_DEV_TEAM);

        assertThat(employeeMapper.countSearch(cond)).isEqualTo(7);
        assertThat(employeeMapper.search(cond)).hasSize(7)
                .allSatisfy(emp -> assertThat(emp.getDeptName()).isEqualTo("개발팀"));
    }

    @Test
    @DisplayName("검색어는 이름과 사원번호 양쪽에 부분 일치로 걸린다")
    void searchesByNameOrEmpNo() {
        EmployeeSearchCond byName = new EmployeeSearchCond();
        byName.setKeyword("곽수빈");
        assertThat(employeeMapper.search(byName)).extracting(Employee::getEmpNo)
                .containsExactly("2020003");

        EmployeeSearchCond byEmpNo = new EmployeeSearchCond();
        byEmpNo.setKeyword("2016");
        assertThat(employeeMapper.countSearch(byEmpNo)).isEqualTo(4);
    }

    @Test
    @DisplayName("LIKE 와일드카드를 입력해도 리터럴로 검색된다")
    void treatsLikeWildcardsAsLiterals() {
        EmployeeSearchCond percent = new EmployeeSearchCond();
        percent.setKeyword("%");

        // % 가 와일드카드로 해석되면 20명이 다 걸린다. 이스케이프되면 0명이다.
        assertThat(percent.getKeywordEscaped()).isEqualTo("\\%");
        assertThat(employeeMapper.countSearch(percent)).isZero();

        EmployeeSearchCond underscore = new EmployeeSearchCond();
        underscore.setKeyword("_");
        assertThat(employeeMapper.countSearch(underscore)).isZero();
    }

    @Test
    @DisplayName("조회 결과는 부서명과 직급명을 함께 담고, 목록 조회에는 비밀번호가 실리지 않는다")
    void joinsOrgLabelsAndHidesPassword() {
        EmployeeSearchCond cond = new EmployeeSearchCond();
        cond.setKeyword("정도현");

        Employee emp = employeeMapper.search(cond).get(0);

        assertThat(emp.getDeptName()).isEqualTo("대표이사실");
        assertThat(emp.getPositionName()).isEqualTo("이사");
        assertThat(emp.getPositionLevel()).isEqualTo(6);
        assertThat(emp.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("사원번호로 조회하면 로그인에 필요한 비밀번호 해시까지 반환한다")
    void findByEmpNoIncludesPasswordHash() {
        Employee emp = employeeMapper.findByEmpNo("2020003");

        assertThat(emp).isNotNull();
        assertThat(emp.getEmpName()).isEqualTo("곽수빈");
        assertThat(emp.getRole()).isEqualTo("USER");
        assertThat(emp.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    @DisplayName("없는 사원번호로 조회하면 null 이다")
    void findByEmpNoReturnsNullWhenAbsent() {
        assertThat(employeeMapper.findByEmpNo("9999999")).isNull();
    }
}
