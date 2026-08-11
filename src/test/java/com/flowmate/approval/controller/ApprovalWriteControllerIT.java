package com.flowmate.approval.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.flowmate.approval.domain.ApprovalLimits;
import com.flowmate.approval.domain.DocType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기안 저장을 **HTTP 계층에서** 검증한다.
 *
 * ★ 이 클래스가 생긴 이유: 서비스 테스트는 {@code ApprovalForm} 을 직접 만들어
 *   넣으므로 화면·요청 계층의 구멍을 볼 수 없다. 실제로 그 사각지대에서 결함이
 *   두 번 나왔다 - 금액을 비운 채 기안하면 500 이 나던 것(amount NOT NULL),
 *   그리고 화면 maxlength 를 우회한 긴 제목이 DB 제약 위반으로 500 이 나던 것.
 *   둘 다 서비스 테스트는 전부 통과하는 상태에서 실제 요청으로만 드러났다.
 *
 * {@code @Transactional} 이라 만든 문서는 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalWriteControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithUserDetails("2020003")
    @DisplayName("★ 제목이 상한을 넘으면 500 이 아니라 안내 화면이 나온다")
    void overlongTitleShowsBusinessError() throws Exception {
        mockMvc.perform(post("/approval/draft").with(csrf())
                        .param("docType", DocType.GENERAL)
                        .param("title", "A".repeat(ApprovalLimits.TITLE + 1))
                        .param("content", "본문"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/business"))
                .andExpect(model().attribute("errorMessage",
                        org.hamcrest.Matchers.containsString("제목")));
    }

    @Test
    @WithUserDetails("2020003")
    @DisplayName("본문이 상한을 넘어도 마찬가지다 — DB 가 TEXT 라도 프롬프트 크기 때문에 막는다")
    void overlongContentShowsBusinessError() throws Exception {
        mockMvc.perform(post("/approval/draft").with(csrf())
                        .param("docType", DocType.GENERAL)
                        .param("title", "정상 제목")
                        .param("content", "가".repeat(ApprovalLimits.CONTENT + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/business"))
                .andExpect(model().attribute("errorMessage",
                        org.hamcrest.Matchers.containsString("본문")));
    }

    @Test
    @WithUserDetails("2020003")
    @DisplayName("상한과 정확히 같은 길이는 저장된다 — 경계에서 한 칸 어긋나지 않는다")
    void exactlyAtLimitIsAccepted() throws Exception {
        mockMvc.perform(post("/approval/draft").with(csrf())
                        .param("docType", DocType.GENERAL)
                        .param("title", "A".repeat(ApprovalLimits.TITLE))
                        .param("content", "본문"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithUserDetails("2020003")
    @DisplayName("★ 금액을 비운 채로도 기안된다 — 실제 화면에서 500 이 나던 결함의 HTTP 회귀")
    void draftWithoutAmountSucceedsOverHttp() throws Exception {
        // 서비스 테스트에도 같은 회귀가 있지만, 그 결함은 화면에서 amount 파라미터가
        // 빈 문자열로 오는 경로에서 났다. 여기서만 그 경로를 그대로 재현한다.
        mockMvc.perform(post("/approval/draft").with(csrf())
                        .param("docType", DocType.GENERAL)
                        .param("title", "금액 없는 일반 문서")
                        .param("content", "본문")
                        .param("amount", ""))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("로그인하지 않으면 기안 저장에 닿지 못한다")
    void anonymousCannotSaveDraft() throws Exception {
        mockMvc.perform(post("/approval/draft").with(csrf())
                        .param("docType", DocType.GENERAL)
                        .param("title", "제목"))
                .andExpect(status().is3xxRedirection());
    }
}
