package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mapper.AiCallLogMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 하루 호출 건수 상한. 넘으면 위임하지 않고 {@code Optional.empty()} 를 돌려준다.
 *
 * ★ 이 데코레이터가 생긴 이유: 공개 배포에서는 데모 계정이 README 에 적혀 있어
 *   누구나 로그인할 수 있다. PREFLIGHT 와 DRAFT_HINT 는 설계상 캐시하지 않으므로
 *   (CachingLlmClient 의 NEVER_CACHED) 요청을 반복하면 그대로 API 호출이 되고,
 *   막는 장치가 없으면 요금이 무한히 올라간다.
 *
 * ★ 체인에서의 위치가 의미를 만든다 - Caching 바로 안쪽이다.
 *   캐시 히트는 여기까지 오지 않으므로 상한을 소모하지 않는다. 즉 상한은
 *   "요청 수"가 아니라 **"실제로 API 를 때린 수"**를 센다.
 *
 * ★ 셀 수 없으면 막는다(fail-closed). 이 프로젝트의 다른 곳은 "AI 실패가 업무
 *   실패가 되어서는 안 된다"를 따르지만 여기만 반대로 간다 - 목적이 비용 방어라,
 *   셀 수 없는 상태에서 계속 호출하면 상한이 아예 없는 것과 같기 때문이다.
 *   다만 예외를 던지지는 않으므로 사용자가 보는 화면은 평소의 폴백 그대로다.
 *
 * ★ 상한 도달을 WARN 으로 남기는 이유: 폴백 문구만 보면 상한 도달과 API 장애가
 *   구별되지 않는다. 운영자는 둘을 구별할 수 있어야 한다(LlmConfig.requireApiKey
 *   가 키 누락을 기동 실패로 만든 것과 같은 판단이다).
 */
public class QuotaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(QuotaLlmClient.class);

    private final LlmClient delegate;
    private final AiCallLogMapper logMapper;
    private final int dailyLimit;
    private final Clock clock;

    /**
     * @param dailyLimit 하루 최대 호출 수. 0 이하면 무제한(로컬 개발의 기본값)
     * @param clock      "오늘"의 경계를 정한다. 테스트가 시각을 통제할 수 있도록 주입받는다
     */
    public QuotaLlmClient(LlmClient delegate, AiCallLogMapper logMapper,
                          int dailyLimit, Clock clock) {
        this.delegate = delegate;
        this.logMapper = logMapper;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        if (dailyLimit <= 0) {
            return delegate.complete(request);
        }

        long used;
        try {
            used = logMapper.countSince(LocalDate.now(clock).atStartOfDay());
        } catch (Exception e) {
            log.warn("AI 호출 건수를 셀 수 없어 호출을 막는다 - 셀 수 없는 상태의 무제한 호출이 더 위험하다", e);
            return Optional.empty();
        }

        if (used >= dailyLimit) {
            log.warn("AI 일일 호출 상한 도달 - 호출하지 않고 폴백한다 (feature={}, {}/{})",
                    request.getFeature(), used, dailyLimit);
            return Optional.empty();
        }

        return delegate.complete(request);
    }
}
