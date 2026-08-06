package com.flowmate.org.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.flowmate.org.domain.Employee;

/**
 * 세션에 올라가는 로그인 사용자.
 *
 * Employee 를 감싸기만 하고 복제하지 않는다.
 * empName/deptName/positionName 을 노출하는 이유는 common/header.jsp 가
 * 매 화면에서 DB 조회 없이 사용자 정보를 표시할 수 있게 하기 위한 것이다.
 *
 * Controller 는 @AuthenticationPrincipal LoginEmployee 로 이 객체를 받고
 * Service 에는 필요한 식별자(empId 등)만 넘긴다.
 * Service 가 HttpSession 이나 SecurityContext 를 모르게 유지하기 위한 규약이다.
 */
public class LoginEmployee implements UserDetails, CredentialsContainer, Serializable {

    private static final long serialVersionUID = 1L;

    private final Employee employee;

    public LoginEmployee(Employee employee) {
        this.employee = employee;
    }

    public Long getEmpId() {
        return employee.getEmpId();
    }

    public String getEmpName() {
        return employee.getEmpName();
    }

    public Long getDeptId() {
        return employee.getDeptId();
    }

    public String getDeptName() {
        return employee.getDeptName();
    }

    public String getPositionName() {
        return employee.getPositionName();
    }

    public int getPositionLevel() {
        return employee.getPositionLevel();
    }

    public String getRole() {
        return employee.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // DB의 role 은 USER/MANAGER/ADMIN 이고 hasRole("ADMIN") 은 ROLE_ADMIN 을 찾는다.
        // 접두사를 여기서 붙이지 않으면 URL 인가가 항상 실패한다.
        return List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole()));
    }

    @Override
    public String getPassword() {
        return employee.getPasswordHash();
    }

    /**
     * 인증이 끝난 뒤 Spring Security 가 호출해 비밀번호 해시를 지운다.
     *
     * ProviderManager.eraseCredentialsAfterAuthentication 기본값이 true 이므로
     * CredentialsContainer 를 구현하면 자동으로 불린다. 구현하지 않으면 아무 일도
     * 일어나지 않고 BCrypt 해시가 세션에 그대로 남는다 — Tomcat 이 세션을 디스크로
     * 내리면 해시가 파일로도 남는다.
     *
     * Spring 의 기본 UserDetails 구현(User)도 같은 이유로 이 인터페이스를 구현한다.
     * 비밀번호 비교는 인증 시점에만 필요하므로 이후 null 이어도 문제되지 않는다.
     */
    @Override
    public void eraseCredentials() {
        employee.setPasswordHash(null);
    }

    @Override
    public String getUsername() {
        return employee.getEmpNo();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return employee.isActive();
    }
}
