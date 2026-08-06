package com.flowmate.org.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.flowmate.org.domain.DeptTreeItem;

@Mapper
public interface DepartmentMapper {

    /** 사용 중인 부서 전체를 계층 순서(깊이 우선, 형제는 sort_order 순)로 반환한다 */
    List<DeptTreeItem> findDeptTree();
}
