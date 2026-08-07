package com.flowmate.org.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.DeptTreeItem;
import com.flowmate.org.domain.Employee;
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

    /**
     * 결재선 생성을 위한 부서장 체인. 기안자 부서에서 루트까지, 가까운 부서가 먼저다.
     *
     * approval 모듈이 이 결과를 자기 타입(ApproverCandidate)으로 변환한다.
     * org 모듈이 approval 의 타입을 알지 않게 하려는 것이다 (설계서 §4.3).
     */
    @Transactional(readOnly = true)
    public List<Employee> findDeptHeadChain(Long deptId) {
        return departmentMapper.findDeptHeadChain(deptId);
    }
}
