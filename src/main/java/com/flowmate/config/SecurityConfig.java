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
                // /login 컨트롤러는 뷰 이름 "login"을 /WEB-INF/views/login.jsp 로 포워드하고,
                // 이 배선에서는 Security 필터가 그 FORWARD 디스패치도 다시 검사한다.
                // 따라서 포워드 대상도 permitAll 이어야 한다.
                //
                // ★ 실측: 이 항목을 빼면 GET /login 이 302 -> /login 무한 자기 리다이렉트가 되어
                //   비로그인 사용자가 로그인 화면 자체에 도달할 수 없다.
                //   다른 화면에서는 드러나지 않는다 — 두 번째 검사도 authenticated 로 통과하기 때문이다.
                //   즉 비로그인 사용자가 반드시 봐야 하는 단 하나의 화면만 막힌다.
                //
                // /WEB-INF/** 는 서블릿 컨테이너가 외부 요청으로 직접 열어주지 않으므로
                // 여기서 permitAll 을 줘도 새로운 공격 표면이 생기지 않는다.
                .requestMatchers("/static/**", "/login", "/WEB-INF/views/login.jsp", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("empNo")
                .passwordParameter("password")
                // 저장된 요청이 있으면 그곳으로 돌아간다 (로드맵 C5).
                // 문서 상세가 딥링크 대상이 되었으므로 항상 홈으로 보내면
                // 링크를 받은 사용자가 로그인 후 다시 링크를 눌러야 한다.
                //
                // 오픈 리다이렉트 위험은 없다 — 저장된 요청은 실제로 거부된 요청에서
                // 서버가 만든 값이고 URL 파라미터로 조작할 수 없다.
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
