package com.flowmate.org.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flowmate.org.domain.DeptTreeItem;
import com.flowmate.org.mapper.DepartmentMapper;

/**
 * findDeptAndDescendantIds() 는 findDeptTree() 가 이미 깊이 우선 순서로 반환한
 * 목록에서 부분트리를 뽑아낸다(새 재귀 쿼리를 추가하지 않고
 * Phase 1의 하향 재귀 CTE 결과를 재사용한다). 이 순서 의존성이 핵심이므로
 * findDeptTree() 를 목으로 고정해 그 전제를 명시적으로 검증한다.
 *
 * 시드 조직도(README) 그대로 고정한다:
 *   대표이사실(1)
 *   ├─ 경영지원본부(2)
 *   │   ├─ 인사팀(3)
 *   │   └─ 재무팀(3)
 *   └─ 사업본부(2)
 *       ├─ 마케팅팀(3)
 *       └─ 개발팀(3)
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentService departmentService;

    private static final long CEO_OFFICE = 1L;
    private static final long SUPPORT_HQ = 2L;
    private static final long HR_TEAM = 3L;
    private static final long FINANCE_TEAM = 4L;
    private static final long BIZ_HQ = 5L;
    private static final long MARKETING_TEAM = 6L;
    private static final long DEV_TEAM = 7L;

    @BeforeEach
    void setUp() {
        List<DeptTreeItem> tree = List.of(
                item(CEO_OFFICE, null, 1),
                item(SUPPORT_HQ, CEO_OFFICE, 2),
                item(HR_TEAM, SUPPORT_HQ, 3),
                item(FINANCE_TEAM, SUPPORT_HQ, 3),
                item(BIZ_HQ, CEO_OFFICE, 2),
                item(MARKETING_TEAM, BIZ_HQ, 3),
                item(DEV_TEAM, BIZ_HQ, 3));
        when(departmentMapper.findDeptTree()).thenReturn(tree);
    }

    @Test
    @DisplayName("중간 부서를 주면 자기 자신 + 하위 팀만 나오고, 다른 본부 하위는 섞이지 않는다")
    void middleDeptReturnsSelfAndDescendantsOnly() {
        List<Long> ids = departmentService.findDeptAndDescendantIds(SUPPORT_HQ);

        assertThat(ids).containsExactly(SUPPORT_HQ, HR_TEAM, FINANCE_TEAM);
    }

    @Test
    @DisplayName("목록 뒤쪽에 있는 부서도 같은 방식으로 자기 하위만 뽑힌다")
    void laterSiblingBranchAlsoWorks() {
        List<Long> ids = departmentService.findDeptAndDescendantIds(BIZ_HQ);

        assertThat(ids).containsExactly(BIZ_HQ, MARKETING_TEAM, DEV_TEAM);
    }

    @Test
    @DisplayName("리프 부서는 자기 자신 하나만 나온다")
    void leafDeptReturnsOnlyItself() {
        List<Long> ids = departmentService.findDeptAndDescendantIds(HR_TEAM);

        assertThat(ids).containsExactly(HR_TEAM);
    }

    @Test
    @DisplayName("루트를 주면 트리 전체가 나온다")
    void rootReturnsWholeTree() {
        List<Long> ids = departmentService.findDeptAndDescendantIds(CEO_OFFICE);

        assertThat(ids).containsExactly(
                CEO_OFFICE, SUPPORT_HQ, HR_TEAM, FINANCE_TEAM, BIZ_HQ, MARKETING_TEAM, DEV_TEAM);
    }

    @Test
    @DisplayName("존재하지 않는 부서면 빈 목록이다")
    void unknownDeptReturnsEmpty() {
        List<Long> ids = departmentService.findDeptAndDescendantIds(999L);

        assertThat(ids).isEmpty();
    }

    private DeptTreeItem item(long deptId, Long parentDeptId, int depth) {
        DeptTreeItem item = new DeptTreeItem();
        item.setDeptId(deptId);
        item.setParentDeptId(parentDeptId);
        item.setDeptName("dept-" + deptId);
        item.setDeptCode("D" + deptId);
        item.setDepth(depth);
        item.setEmpCount(0);
        return item;
    }
}
