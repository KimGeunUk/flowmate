package com.flowmate.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flowmate.approval.policy.ApprovalLinePolicy;
import com.flowmate.approval.policy.DefaultApprovalLinePolicy;
import com.flowmate.approval.policy.SimpleTwoStepLinePolicy;

/**
 * ★ 커스터마이징 지점의 교체 배선.
 *
 * application.yml 의 flowmate.approval.line-policy 값으로 구현체를 바꾼다.
 *   default      → DefaultApprovalLinePolicy (부서 트리 상향 + 금액별 임원)
 *   simple-two-step → SimpleTwoStepLinePolicy (소속 부서장 1명)
 *
 * 설정이 없으면 default 를 쓴다. 회사마다 결재 규칙이 다르므로 코드를 고치지 않고
 * 설정 파일만 바꿔 결재 규칙을 교체하는 것이 이 구조의 목적이다.
 */
@Configuration
public class ApprovalPolicyConfig {

    @Bean
    @ConditionalOnProperty(name = "flowmate.approval.line-policy", havingValue = "simple-two-step")
    public ApprovalLinePolicy simpleTwoStepLinePolicy() {
        return new SimpleTwoStepLinePolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "flowmate.approval.line-policy", havingValue = "default",
                           matchIfMissing = true)
    public ApprovalLinePolicy defaultApprovalLinePolicy() {
        return new DefaultApprovalLinePolicy();
    }
}
