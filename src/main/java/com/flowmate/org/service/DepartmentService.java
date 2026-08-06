package com.flowmate.org.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.DeptTreeItem;
import com.flowmate.org.mapper.DepartmentMapper;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    /**
     * 조직도 화면과 사원 목록의 부서 선택 상자가 같은 결과를 쓴다.
     * 부서 수가 수십 개 수준이라 전체를 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public List<DeptTreeItem> findDeptTree() {
        return departmentMapper.findDeptTree();
    }
}
