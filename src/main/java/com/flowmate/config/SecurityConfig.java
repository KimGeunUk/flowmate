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
                // /login 컨트롤러는 뷰 이름 "login"을 /WEB-INF/views/login.jsp 로 포워드한다.
                // Boot 는 Security 필터를 FORWARD 디스패치에도 다시 태우므로, 포워드 대상도
                // permitAll 이어야 한다. 그렇지 않으면 비로그인 사용자가 로그인 화면 자체를
                // 보지 못하고 다시 /login 으로 리다이렉트되는 루프가 생긴다.
                // /WEB-INF/** 는 서블릿 컨테이너가 외부 요청으로는 직접 열어주지 않으므로
                // 여기서 permitAll 을 줘도 새로운 공격 표면이 생기지 않는다.
                .requestMatchers("/static/**", "/login", "/WEB-INF/views/login.jsp", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("empNo")
                .passwordParameter("password")
                // true 를 주는 이유: 저장된 이전 요청으로 돌아가지 않고 항상 홈으로 보낸다.
                // 데모 중 예상 못한 화면으로 튀는 것을 막는다.
                .defaultSuccessUrl("/", true)
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
