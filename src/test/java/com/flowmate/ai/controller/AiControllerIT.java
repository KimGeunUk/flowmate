package com.flowmate.ai.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.domain.PreflightResult;
import com.flowmate.ai.domain.PreflightVerdict;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.flowmate.approval.domain.ApprovalLimits;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;

/**
 * ★ {@code fetch()} 는 {@code $.ajaxSetup} 경로를 타지 않아
 * CSRF 헤더가 안 붙고 조용히 403 이 되는 그 현상을, 실제 Spring Security 필터 체인을
 * 통과시켜 고정한다(단정만이 아니라 실제로 재현한다).
 *
 * LoginIT 과 같은 방식으로 실제 로그인 세션을 만든다 - {@code @WithMockUser} 는
 * {@code AuthenticationPrincipal}이 기대하는 {@code LoginEmployee} 타입을 주지 않으므로
 * 여기서는 쓸 수 없다. 로그인 계정(2020003)은 사원 18(곽수빈, 개발팀) - 문서 1
 * (EXP-2026-0001)의 기안자라서 사전점검 권한 검사(findDoc)를 자연스럽게 통과한다
 * (SummaryServiceIT 와 같은 픽스처).
 *
 * ★ {@code @Transactional} + {@code FakeLlmClient} 고정값 리셋이 둘 다 필요하다:
 *   - {@code FakeLlmClient} 는 싱글턴이고 Failsafe 는 기본적으로 같은 포크에서 IT
 *     클래스들이 Spring 컨텍스트를 공유한다 - PreflightServiceIT 가 남긴
 *     {@code setFixedResult(PreflightResult.class, ...)} 값이 그대로 남아 있을 수
 *     있다(LlmChainIT/SummaryServiceIT 의 resetFake() 주석과 같은 함정).
 *   - 이 값이 우연히 WARN 으로 남아 있으면 preflightService.check() 가
 *     ai_preflight_result 에 실제로 행을 쓴다. 이 클래스가 @Transactional 이 아니면
 *     그 행이 롤백 없이 그대로 커밋된다 - 실행 순서에 따라 조용히 DB 를 오염시키는
 *     경로였다. 결정적인 PASS 고정값으로 리셋하고 트랜잭션으로 감싸 이중으로 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @BeforeEach
    void resetFake() {
        fakeLlmClient.getReceived().clear();
        fakeLlmClient.setDelayMillis(0);
        fakeLlmClient.setExceptionToThrow(null);
        fakeLlmClient.setEchoPrompt(false);

        PreflightResult pass = new PreflightResult();
        pass.setVerdict(PreflightVerdict.PASS);
        pass.setFindings(List.of());
        fakeLlmClient.setFixedResult(PreflightResult.class, pass);
    }

    @Test
    @DisplayName("★ CSRF 헤더 없이 사전점검을 호출하면 403 - flowmateFetch 가 없다면 실제로 벌어지는 일")
    void preflightWithoutCsrfHeaderIsForbidden() throws Exception {
        MockHttpSession session = loginAsKwak();

        mockMvc.perform(post("/api/ai/approvals/1/preflight").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF 헤더(flowmateFetch 가 붙이는 것과 같은 이름·값)를 붙이면 통과한다")
    void preflightWithCsrfHeaderSucceeds() throws Exception {
        MockHttpSession session = loginAsKwak();

        // csrf().asHeader() - 폼 파라미터가 아니라 HTTP 헤더로 토큰을 보낸다.
        // flowmateFetch(common.js)가 meta 태그에서 읽어 붙이는 것과 같은 전달 방식이다.
        mockMvc.perform(post("/api/ai/approvals/1/preflight")
                        .session(session)
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
    }

    private MockHttpSession loginAsKwak() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("empNo", "2020003")
                        .param("password", "flowmate1!")
                        .with(csrf()))
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    @WithUserDetails("2020003")
    @DisplayName("★ 기안 제안에 지나치게 긴 본문을 보내면 400 — LLM 에 닿기 전에 거부한다")
    void draftHintRejectsOverlongContent() throws Exception {
        // 이 엔드포인트만 요청 본문을 그대로 프롬프트에 넣는다. 상한이 없으면
        // 인증된 사용자가 임의 길이 텍스트를 유료 API 로 흘려보낼 수 있다.
        //
        // ★ 400 이 나오려면 AiController 에 핸들러가 있어야 한다. GlobalExceptionHandler
        //   는 basePackages 를 approval/attendance 로 좁혀 두어 이 패키지를 덮지 않으므로,
        //   처음 구현했을 때는 500 이 났다.
        String body = "{\"docType\":\"EXPENSE\",\"title\":\"t\",\"content\":\""
                + "가".repeat(ApprovalLimits.CONTENT + 1) + "\"}";

        mockMvc.perform(post("/api/ai/draft-hint").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails("2020003")
    @DisplayName("상한 안쪽이면 정상 경로를 탄다 — 거부가 길이 때문이라는 것을 확인한다")
    void draftHintAcceptsContentWithinLimit() throws Exception {
        String body = "{\"docType\":\"EXPENSE\",\"title\":\"출장비 정산\",\"content\":\"짧은 본문\"}";

        // FakeLlmClient 가 빈 초안을 주므로 503 이다. 400 이 아니라는 것이 요점이다.
        mockMvc.perform(post("/api/ai/draft-hint").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable());
    }
}
