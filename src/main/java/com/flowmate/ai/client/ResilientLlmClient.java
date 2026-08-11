package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 타임아웃과 예외를 흡수한다. 폴백 원칙의 실행 지점이다.
 *
 * ★ SDK 클라이언트의 타임아웃 설정만으로 부족한 이유:
 *   그건 HTTP 계층만 막는다. 이 데코레이터의 계약은
 *   "무슨 일이 있어도 지정 시간 안에 돌아온다" 이므로 별도로 건다.
 *
 * 어떤 예외도 밖으로 내보내지 않는다 — Error 는 제외한다.
 * OutOfMemoryError 를 삼키면 죽어야 할 프로세스가 이상하게 살아있게 된다.
 */
public class ResilientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmClient.class);

    private final LlmClient delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    public ResilientLlmClient(LlmClient delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        Future<Optional<LlmResponse>> future = executor.submit(() -> delegate.complete(request));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("AI 호출 타임아웃 {}초 — feature={}", timeout.getSeconds(), request.getFeature());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // ★ 인터럽트 상태를 복원한다
            return Optional.empty();
        } catch (ExecutionException e) {
            log.warn("AI 호출 실패 — feature={}", request.getFeature(), e.getCause());
            return Optional.empty();
        }
    }
}
