package com.flowmate.org.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.org.domain.DeptTreeItem;

/**
 * 재귀 CTE 검증. 시드 데이터(부서 7개, 3단 계층)를 전제로 한다.
 * 쓰기가 있는 테스트는 @Transactional 로 롤백된다.
 */
@SpringBootTest
@Transactional
class DepartmentMapperIT {

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("조직도는 깊이 우선으로, 형제는 sort_order 순으로 정렬되어 나온다")
    void returnsDepthFirstOrderedBySortOrder() {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(tree).extracting(DeptTreeItem::getDeptName)
                .containsExactly("대표이사실", "경영지원본부", "인사팀", "재무팀",
                                 "사업본부", "마케팅팀", "개발팀");
    }

    @Test
    @DisplayName("루트는 depth 1, 본부는 2, 팀은 3이다")
    void assignsDepthByLevel() {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(findByName(tree, "대표이사실").getDepth()).isEqualTo(1);
        assertThat(findByName(tree, "경영지원본부").getDepth()).isEqualTo(2);
        assertThat(findByName(tree, "인사팀").getDepth()).isEqualTo(3);
    }

    @Test
    @DisplayName("부서별 사원 수를 함께 반환하고 하위 부서 인원은 합산하지 않는다")
    void includesEmployeeCountPerDepartment() {
        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(findByName(tree, "개발팀").getEmpCount()).isEqualTo(7);
        assertThat(findByName(tree, "대표이사실").getEmpCount()).isEqualTo(1);
        assertThat(findByName(tree, "경영지원본부").getEmpCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용하지 않는 부서는 그 하위까지 조직도에서 빠진다")
    void excludesUnusedDepartmentAndItsDescendants() {
        jdbcTemplate.update("UPDATE department SET use_yn = 'N' WHERE dept_code = 'HQ_MGMT'");

        List<DeptTreeItem> tree = departmentMapper.findDeptTree();

        assertThat(tree).extracting(DeptTreeItem::getDeptName)
                .doesNotContain("경영지원본부", "인사팀", "재무팀")
                .contains("사업본부", "개발팀");
    }

    private DeptTreeItem findByName(List<DeptTreeItem> tree, String name) {
        return tree.stream()
                .filter(item -> name.equals(item.getDeptName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("부서를 찾지 못했습니다: " + name));
    }
}
