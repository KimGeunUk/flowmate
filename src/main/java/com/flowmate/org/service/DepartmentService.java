package com.flowmate.org.service;

import java.util.ArrayList;
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

    /**
     * 주어진 부서와 그 하위 부서 전체의 dept_id 목록. 부서 근태 현황(계획서 4
     * Task 7)이 "본인 부서와 하위만" 볼 수 있게 하는 데 쓴다.
     *
     * 새 재귀 쿼리를 추가하지 않는다 — findDeptTree() 가 이미 깊이 우선 순서로
     * 전체 트리를 갖고 있으므로(설계서 §6.1의 하향 재귀 CTE, sort_path 로
     * 정렬됨), 그 순서만으로 부분트리를 뽑아낸다: 찾는 부서를 만난 뒤부터
     * depth 가 그 부서보다 큰 동안은 전부 하위 부서이고, depth 가 다시
     * 같거나 작아지는 순간 형제/상위로 넘어간 것이므로 멈춘다
     * (계획서 4 Task 7 — "Phase 1의 하향 재귀 CTE 를 재사용한다").
     */
    @Transactional(readOnly = true)
    public List<Long> findDeptAndDescendantIds(Long deptId) {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();
        List<Long> ids = new ArrayList<>();
        Integer rootDepth = null;

        for (DeptTreeItem item : tree) {
            if (rootDepth == null) {
                if (item.getDeptId().equals(deptId)) {
                    rootDepth = item.getDepth();
                    ids.add(item.getDeptId());
                }
                continue;
            }
            if (item.getDepth() <= rootDepth) {
                break;
            }
            ids.add(item.getDeptId());
        }
        return ids;
    }
}
