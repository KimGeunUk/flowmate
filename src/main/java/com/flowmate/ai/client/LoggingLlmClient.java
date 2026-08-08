package com.flowmate.ai.client;

import com.flowmate.ai.domain.AiCallLog;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mapper.AiCallLogMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 성공·실패 모두 {@code ai_call_log} 에 남긴다. {@code latency_ms} 를 잰다.
 *
 * ★ 로그 기록이 실패해도 AI 호출 결과를 버리지 않는다.
 *   로그는 부가 기능이다. DB 가 잠깐 이상해서 요약이 안 나오면 안 된다.
 *
 * 이 데코레이터 바로 안쪽은 항상 {@code ResilientLlmClient} 다 - 즉 delegate.complete()
 * 는 예외를 던지지 않고 실패도 {@code Optional.empty()} 로 돌아온다. 그래서 여기서는
 * delegate 호출 자체를 try/catch 로 감쌀 필요가 없고, logMapper.insert(...) 만 감싼다.
 */
public class LoggingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingLlmClient.class);

    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final LlmClient delegate;
    private final AiCallLogMapper logMapper;

    public LoggingLlmClient(LlmClient delegate, AiCallLogMapper logMapper) {
        this.delegate = delegate;
        this.logMapper = logMapper;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        long start = System.currentTimeMillis();
        Optional<LlmResponse> result = delegate.complete(request);
        long latencyMs = System.currentTimeMillis() - start;

        writeLog(request, result, latencyMs);

        return result;
    }

    private void writeLog(LlmRequest request, Optional<LlmResponse> result, long latencyMs) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setFeature(request.getFeature());
            entry.setPromptVersion(request.getPromptVersion());
            entry.setEmpId(request.getEmpId());
            entry.setApprovalId(request.getApprovalId());
            entry.setLatencyMs((int) latencyMs);

            if (result.isPresent()) {
                LlmResponse response = result.get();
                entry.setInputTokens(response.getInputTokens());
                entry.setOutputTokens(response.getOutputTokens());
                entry.setSuccessYn("Y");
            } else {
                entry.setSuccessYn("N");
                entry.setErrorMsg(truncate("AI 호출이 실패했거나 타임아웃됐습니다 - 상세 원인은 애플리케이션 로그 참고"));
            }

            logMapper.insert(entry);
        } catch (Exception e) {
            // ★ 로그 실패를 삼킨다 - 이 데코레이터의 실패가 정상적으로 받은 AI 결과를 버리게 해서는 안 된다.
            log.warn("AI 호출 로그 기록 실패 - AI 결과 자체는 정상 반환한다", e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= ERROR_MSG_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MSG_MAX_LENGTH);
    }
}
