package com.flowmate.org.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 흐름 검증.
 *
 * 이 테스트가 통과하는 것은 pgcrypto 로 만든 $2a$ 해시가
 * BCryptPasswordEncoder 로 실제 검증된다는 뜻이기도 하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미인증 상태로 보호된 화면에 접근하면 로그인 페이지로 리다이렉트된다")
    void unauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/org/employees"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("시드 계정으로 로그인하면 인증되고 홈으로 이동한다")
    void loginSucceedsWithSeedAccount() throws Exception {
        mockMvc.perform(post("/login")
                        .param("empNo", "2020003")
                        .param("password", "flowmate1!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("2020003").withRoles("USER"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 인증되지 않고 실패 URL로 돌아간다")
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/login")
                        .param("empNo", "2020003")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("USER 권한으로 인증된 사용자는 관리자 영역에서 403 을 받는다")
    @WithMockUser(username = "2020003", roles = "USER")
    void nonAdminIsForbiddenFromAdminArea() throws Exception {
        // /admin/** 에는 아직 화면이 없다. 404 가 아니라 403 이 나오는 것이
        // 곧 인가 규칙이 컨트롤러보다 먼저 걸렸다는 뜻이다.
        mockMvc.perform(get("/admin/anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 권한으로 인증된 사용자는 관리자 영역에서 403 을 받지 않는다")
    @WithMockUser(username = "2015001", roles = "ADMIN")
    void adminPassesAdminAreaAuthorization() throws Exception {
        // 인가는 통과하므로 매핑이 없어서 404 가 된다. 403 이 아니라는 점이 검증 대상이다.
        mockMvc.perform(get("/admin/anything"))
                .andExpect(status().isNotFound());
    }
}
