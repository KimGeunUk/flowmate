package com.flowmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 6 최소 구성.
 * WebSecurityConfigurerAdapter 는 제거되었으므로 SecurityFilterChain 빈으로 배선한다.
 *
 * CSRF 는 끄지 않는다. JSP 폼은 hidden input 으로, jQuery AJAX 는
 * static/js/common.js 의 ajaxSetup 이 헤더로 토큰을 보낸다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 시드 비밀번호는 pgcrypto 의 crypt(pw, gen_salt('bf', 10)) 으로 만들어져
     * $2a$10$ 형식이다. BCryptPasswordEncoder 가 그 형식을 그대로 검증한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // ★ login.jsp 까지 여는 이유: Security 필터가 FORWARD 디스패치도
                //   다시 검사하므로 포워드 대상도 열려 있어야 한다. 빼면 GET /login
                //   이 자기 자신으로 무한 리다이렉트되어 비로그인 사용자가 로그인
                //   화면에 도달하지 못한다 - 로그인한 사용자는 두 번째 검사도
                //   통과하므로 다른 화면에서는 증상이 안 보인다.
                //   /WEB-INF/** 는 컨테이너가 외부 요청으로 열어주지 않으므로
                //   여기에 넣어도 공격 표면이 늘지 않는다.
                .requestMatchers("/static/**", "/login", "/WEB-INF/views/login.jsp", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("empNo")
                .passwordParameter("password")
                // 저장된 요청이 있으면 그곳으로 돌아간다 - 문서 상세 링크를 받은
                // 사용자가 로그인 후 링크를 다시 누르지 않아도 되게. 저장된 요청은
                // 서버가 만든 값이라 URL 파라미터로 조작할 수 없다(오픈 리다이렉트 아님).
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
