package com.flowmate.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 고정 응답을 돌려주는 구현. 두 가지 역할을 겸한다.
 *
 *   1) 테스트 대역 — 받은 요청을 기록하므로 "무엇이 바깥으로 나갔는지" 단정할 수 있다
 *   2) ai.enabled=false 일 때의 실제 구현 — API 키 없이 앱이 뜬다 (계획서 3 D3)
 *
 * 2번이 중요하다. 이것 덕분에 저장소를 클론한 사람이 키 없이 빌드하고 실행할 수 있다.
 *
 * 지연·예외 주입은 이 클래스 자체를 위한 것이 아니라 이 클래스를 감싸는
 * ResilientLlmClient 의 타임아웃·폴백 경로를 테스트하기 위한 것이다.
 */
public class FakeLlmClient implements LlmClient {

    private final List<LlmRequest> received = new ArrayList<>();
    private final Map<Class<?>, Object> fixedResultsByType = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmResponse fixedResponse = defaultResponse();
    private long delayMillis;
    private RuntimeException exceptionToThrow;
    private boolean echoPrompt;

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        received.add(request);

        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }

        if (echoPrompt) {
            LlmResponse echoed = new LlmResponse();
            echoed.setText(request.getPrompt());
            echoed.setModel(fixedResponse.getModel());
            echoed.setInputTokens(fixedResponse.getInputTokens());
            echoed.setOutputTokens(fixedResponse.getOutputTokens());
            return Optional.of(echoed);
        }

        if (request.getOutputType() != null) {
            return Optional.of(typedResponse(request.getOutputType()));
        }

        return Optional.of(fixedResponse);
    }

    /**
     * outputType 이 있는 요청(계획서 5 Task 2, 구조화 출력 배선)을 위한 고정 응답.
     * 실제 Claude 호출 없이도 타입 경로를 테스트로 훈련시킬 수 있어야 하므로, 등록된
     * 고정값이 없으면 그 타입의 기본 생성자로 빈 인스턴스를 만들어 직렬화한다 - 필드가
     * 전부 null 이라도 그 타입의 JSON 스키마는 지키는 "그럴듯한" 응답이다.
     */
    private LlmResponse typedResponse(Class<?> outputType) {
        Object value = fixedResultsByType.get(outputType);
        if (value == null) {
            value = instantiateDefault(outputType);
        }

        LlmResponse response = new LlmResponse();
        response.setText(toJson(value));
        response.setModel(fixedResponse.getModel());
        response.setInputTokens(fixedResponse.getInputTokens());
        response.setOutputTokens(fixedResponse.getOutputTokens());
        return response;
    }

    private Object instantiateDefault(Class<?> outputType) {
        try {
            return outputType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "FakeLlmClient 가 outputType 의 기본 인스턴스를 만들 수 없습니다 - "
                    + "기본 생성자가 있는 POJO 여야 하거나, setFixedResult(type, value) 로 "
                    + "직접 값을 등록해야 합니다: " + outputType, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("FakeLlmClient 가 구조화 응답을 JSON 으로 만들 수 없습니다", e);
        }
    }

    /** 지금까지 받은 요청 전부. 마스킹 검증(계획서 3 D8)에 쓴다 — 받은 순서대로 쌓인다 */
    public List<LlmRequest> getReceived() {
        return received;
    }

    /** 가장 최근에 받은 요청. 받은 적이 없으면 null */
    public LlmRequest lastRequest() {
        if (received.isEmpty()) {
            return null;
        }
        return received.get(received.size() - 1);
    }

    /** 고정 응답을 바꾼다 — 두 번째 구현 교체 증명(계획서 3 D4)에서 Claude 와 다른 값을 주는 데 쓴다 */
    public void setFixedResponse(LlmResponse fixedResponse) {
        this.fixedResponse = fixedResponse;
    }

    /**
     * outputType 별 고정값을 등록한다(계획서 5 Task 2). 요청에 그 outputType 이 실려 오면
     * 이 값을 JSON 으로 직렬화해 돌려준다 - 기본 생성자로 만든 빈 인스턴스로는 검증할 수
     * 없는 구체적인 필드 값을 단정해야 하는 테스트(예: Task 3 이후의 기능별 IT)를 위한 것이다.
     */
    public void setFixedResult(Class<?> outputType, Object value) {
        fixedResultsByType.put(outputType, value);
    }

    /** ResilientLlmClient 타임아웃 테스트용 — 응답하기 전에 이만큼 잠든다 */
    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    /** 폴백 테스트용 — complete() 가 이 예외를 던지게 한다. null 이면 던지지 않는다 */
    public void setExceptionToThrow(RuntimeException exceptionToThrow) {
        this.exceptionToThrow = exceptionToThrow;
    }

    /**
     * 캐싱 데코레이터 순서 검증용(계획서 3 D8) — 받은 프롬프트를 그대로 응답 텍스트로 돌려준다.
     *
     * 고정 응답만 쓰면 그 응답은 입력과 무관한 상수라서, "캐시에 저장된 결과에 원문이 없다"는
     * 단정이 항상 참이 되어 아무것도 증명하지 못한다. 실제 Claude 가 요약 중 입력 내용을
     * 일부 그대로 옮길 수 있는 상황을 흉내내야, 그 응답이 캐시에 저장됐을 때 원문이 아니라
     * 마스킹된 토큰만 들어있다는 것이 비로소 의미 있는 단정이 된다.
     */
    public void setEchoPrompt(boolean echoPrompt) {
        this.echoPrompt = echoPrompt;
    }

    private static LlmResponse defaultResponse() {
        LlmResponse response = new LlmResponse();
        response.setText("[FAKE] 고정 응답입니다 - ai.enabled=false");
        response.setInputTokens(10);
        response.setOutputTokens(10);
        response.setModel("fake-model");
        return response;
    }
}
