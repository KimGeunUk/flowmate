package com.flowmate.org.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.org.domain.DeptTreeItem;
import com.flowmate.org.domain.Employee;

@Mapper
public interface DepartmentMapper {

    /** 사용 중인 부서 전체를 계층 순서(깊이 우선, 형제는 sort_order 순)로 반환한다 */
    List<DeptTreeItem> findDeptTree();

    /**
     * 주어진 부서에서 루트까지 올라가며 각 부서의 최고 직급자 1명을 반환한다.
     * 가까운 부서가 먼저 온다. 결재선 정책이 이 순서에 의존한다.
     */
    List<Employee> findDeptHeadChain(@Param("deptId") Long deptId);
}
