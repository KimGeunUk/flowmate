package com.flowmate.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flowmate.attendance.policy.DefaultWorkTimePolicy;
import com.flowmate.attendance.policy.FlatLeaveGrantPolicy;
import com.flowmate.attendance.policy.FlexWorkTimePolicy;
import com.flowmate.attendance.policy.LeaveGrantPolicy;
import com.flowmate.attendance.policy.TenureBasedLeaveGrantPolicy;
import com.flowmate.attendance.policy.WorkTimePolicy;

/**
 * ★ 커스터마이징 지점 2·3 의 교체 배선 (계획서 4 D7).
 *
 * ApprovalPolicyConfig(지점 1)·LlmConfig(지점 4)와 정확히 같은 형태다 —
 * application.yml 값으로 구현체를 고르고, 설정이 없으면 default 로 떨어진다.
 *
 *   flowmate.attendance.work-time-policy    default(고정 출퇴근제) | flex(자율출퇴근제)
 *   flowmate.attendance.leave-grant-policy  flat(전원 15일)        | tenure(근속 기반)
 *
 * ★ 두 쌍 모두 "default 값"쪽에만 matchIfMissing = true 를 둔다. 둘 다 두면
 * 설정이 비어 있을 때 두 구현이 동시에 매칭되어 NoUniqueBeanDefinitionException 이
 * 컨텍스트 기동 시점에 난다 — 그런데 이건 mvnw test(컨텍스트를 띄우지 않는
 * 단위 테스트)로는 잡히지 않고, mvnw verify(@SpringBootTest 가 실제 컨텍스트를
 * 띄우는 통합 테스트)에서만 드러난다. 조건을 겹치지 않게 짜는 것이 아니라
 * "정확히 한쪽에만 matchIfMissing" 규칙으로 원천 차단한다.
 */
@Configuration
public class AttendancePolicyConfig {

    @Bean
    @ConditionalOnProperty(name = "flowmate.attendance.work-time-policy", havingValue = "flex")
    public WorkTimePolicy flexWorkTimePolicy() {
        return new FlexWorkTimePolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "flowmate.attendance.work-time-policy", havingValue = "default",
                           matchIfMissing = true)
    public WorkTimePolicy defaultWorkTimePolicy() {
        return new DefaultWorkTimePolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "flowmate.attendance.leave-grant-policy", havingValue = "tenure")
    public LeaveGrantPolicy tenureBasedLeaveGrantPolicy() {
        return new TenureBasedLeaveGrantPolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "flowmate.attendance.leave-grant-policy", havingValue = "flat",
                           matchIfMissing = true)
    public LeaveGrantPolicy flatLeaveGrantPolicy() {
        return new FlatLeaveGrantPolicy();
    }
}
