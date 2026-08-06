package com.flowmate.org.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;

/**
 * 사원번호를 아이디로 쓰는 인증 원천.
 * 비밀번호 비교는 DaoAuthenticationProvider 가 PasswordEncoder 로 수행하므로 여기서 하지 않는다.
 */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeMapper employeeMapper;

    public EmployeeUserDetailsService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String empNo) throws UsernameNotFoundException {
        Employee employee = employeeMapper.findByEmpNo(empNo);
        if (employee == null) {
            throw new UsernameNotFoundException("사원번호를 찾을 수 없습니다: " + empNo);
        }
        // 퇴직자(use_yn='N')는 예외를 던지지 않고 isEnabled()=false 로 넘긴다.
        // 계정 상태 판단을 Spring Security 의 표준 흐름에 맡기기 위한 것이다.
        return new LoginEmployee(employee);
    }
}
