package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiCallLog;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mapper.AiCallLogMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 일일 호출 상한 판정. Spring 없이 돈다 - 스텁 두 개로 조건을 전부 통제한다.
 *
 * ★ Clock 을 주입받는 설계인 이유: "오늘"의 경계를 테스트가 정할 수 있어야 한다.
 *   시스템 시각에 의존하면 자정 직전에 돌린 빌드만 깨지는 테스트가 된다.
 */
class QuotaLlmClientTest {

    /** 2026-08-12 14:30 KST 로 고정 */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T05:30:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("상한 미만이면 그대로 위임한다")
    void belowLimitDelegates() {
        CountingDelegate delegate = new CountingDelegate();
        QuotaLlmClient client = new QuotaLlmClient(delegate, countingMapper(9), 10, FIXED_CLOCK);

        assertThat(client.complete(request())).isPresent();
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 상한에 도달하면 위임하지 않는다 - API 를 때리지 않는 것이 핵심이다")
    void atLimitDoesNotDelegate() {
        CountingDelegate delegate = new CountingDelegate();
        QuotaLlmClient client = new QuotaLlmClient(delegate, countingMapper(10), 10, FIXED_CLOCK);

        assertThat(client.complete(request())).isEmpty();
        assertThat(delegate.calls).isZero();
    }

    @Test
    @DisplayName("경계: 상한-1 은 통과하고 상한은 막힌다")
    void boundaryIsExclusiveOnTheLimit() {
        assertThat(new QuotaLlmClient(new CountingDelegate(), countingMapper(4), 5, FIXED_CLOCK)
                .complete(request())).isPresent();
        assertThat(new QuotaLlmClient(new CountingDelegate(), countingMapper(5), 5, FIXED_CLOCK)
                .complete(request())).isEmpty();
    }

    @Test
    @DisplayName("상한 0 은 무제한이다 - 로컬 개발의 기본값이라 아무것도 막지 않는다")
    void zeroMeansUnlimited() {
        CountingDelegate delegate = new CountingDelegate();
        QuotaLlmClient client = new QuotaLlmClient(delegate, countingMapper(9_999), 0, FIXED_CLOCK);

        assertThat(client.complete(request())).isPresent();
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 셀 수 없으면 막는다(fail-closed) - 비용 방어가 목적이라 반대로 가면 상한이 없는 것과 같다")
    void countingFailureBlocksTheCall() {
        CountingDelegate delegate = new CountingDelegate();
        AiCallLogMapper broken = new AiCallLogMapper() {
            @Override
            public void insert(AiCallLog log) { }

            @Override
            public long countSince(LocalDateTime since) {
                throw new IllegalStateException("DB 접속 불가");
            }
        };

        QuotaLlmClient client = new QuotaLlmClient(delegate, broken, 10, FIXED_CLOCK);

        assertThat(client.complete(request())).isEmpty();
        assertThat(delegate.calls).isZero();
    }

    @Test
    @DisplayName("★ 경계는 그날 00:00 이다 - 어제 호출은 오늘 상한을 잡아먹지 않는다")
    void boundaryIsStartOfTodayInTheClockZone() {
        List<LocalDateTime> asked = new ArrayList<>();
        AiCallLogMapper recording = new AiCallLogMapper() {
            @Override
            public void insert(AiCallLog log) { }

            @Override
            public long countSince(LocalDateTime since) {
                asked.add(since);
                return 0L;
            }
        };

        new QuotaLlmClient(new CountingDelegate(), recording, 10, FIXED_CLOCK).complete(request());

        assertThat(asked).containsExactly(LocalDateTime.of(2026, 8, 12, 0, 0));
    }

    private LlmRequest request() {
        LlmRequest request = new LlmRequest();
        request.setFeature(AiFeature.DRAFT_HINT);
        request.setPromptVersion("v1");
        request.setPrompt("본문을 제안해 주세요");
        return request;
    }

    /** countSince 가 항상 같은 값을 돌려주는 스텁 */
    private AiCallLogMapper countingMapper(long used) {
        return new AiCallLogMapper() {
            @Override
            public void insert(AiCallLog log) { }

            @Override
            public long countSince(LocalDateTime since) {
                return used;
            }
        };
    }

    /** 위임 횟수를 세는 스텁. 항상 성공 응답을 돌려준다 */
    private static class CountingDelegate implements LlmClient {
        private int calls;

        @Override
        public Optional<LlmResponse> complete(LlmRequest request) {
            calls++;
            return Optional.of(new LlmResponse());
        }
    }
}
