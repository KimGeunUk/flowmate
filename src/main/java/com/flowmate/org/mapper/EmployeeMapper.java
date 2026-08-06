package com.flowmate.org.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.domain.EmployeeSearchCond;

@Mapper
public interface EmployeeMapper {

    /** 목록 조회. 비밀번호 해시는 선택하지 않는다. */
    List<Employee> search(EmployeeSearchCond cond);

    /** 같은 조건의 전체 건수 (페이징 계산용) */
    long countSearch(EmployeeSearchCond cond);

    /** 로그인 인증용. 비밀번호 해시를 포함한다. 없으면 null. */
    Employee findByEmpNo(String empNo);
}
