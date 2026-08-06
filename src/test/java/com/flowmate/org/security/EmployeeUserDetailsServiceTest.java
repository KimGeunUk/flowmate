package com.flowmate.org.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;

@ExtendWith(MockitoExtension.class)
class EmployeeUserDetailsServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeUserDetailsService userDetailsService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setEmpId(18L);
        employee.setEmpNo("2020003");
        employee.setEmpName("곽수빈");
        employee.setDeptId(7L);
        employee.setDeptName("개발팀");
        employee.setPositionName("사원");
        employee.setPositionLevel(1);
        employee.setHireDate(LocalDate.of(2020, 3, 2));
        employee.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        employee.setRole("USER");
        employee.setUseYn("Y");
    }

    @Test
    @DisplayName("권한 문자열에 ROLE_ 접두사를 붙인다")
    void prefixesRoleWithRoleUnderscore() {
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("사원번호가 아이디이고 비밀번호 해시를 그대로 전달한다")
    void usesEmpNoAsUsernameAndPassesHash() {
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.getUsername()).isEqualTo("2020003");
        assertThat(loaded.getPassword()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
        assertThat(loaded.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("없는 사원번호면 UsernameNotFoundException 을 던진다")
    void throwsWhenEmpNoNotFound() {
        when(employeeMapper.findByEmpNo("9999999")).thenReturn(null);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("9999999"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("퇴직 처리된 사원은 계정이 비활성으로 표시된다")
    void retiredEmployeeIsDisabled() {
        employee.setUseYn("N");
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("상단 메뉴가 쓸 사원명·부서·직급을 함께 노출한다")
    void exposesOrgLabelsForHeader() {
        when(employeeMapper.findByEmpNo("2020003")).thenReturn(employee);

        LoginEmployee loaded = (LoginEmployee) userDetailsService.loadUserByUsername("2020003");

        assertThat(loaded.getEmpId()).isEqualTo(18L);
        assertThat(loaded.getEmpName()).isEqualTo("곽수빈");
        assertThat(loaded.getDeptId()).isEqualTo(7L);
        assertThat(loaded.getDeptName()).isEqualTo("개발팀");
        assertThat(loaded.getPositionName()).isEqualTo("사원");
    }
}
