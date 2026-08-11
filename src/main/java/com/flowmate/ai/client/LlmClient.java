package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;

/**
 * ★ 커스터마이징 지점 4 — AI 제공자
 *
 * 구현 2종:
 *   - ClaudeLlmClient : 실제 Anthropic API 호출 (ai.enabled=true)
 *   - FakeLlmClient   : 고정 응답 (ai.enabled=false, 기본값)
 *
 * 데코레이터 4종이 같은 인터페이스로 서로를 감싼다.
 *
 * ★ 반환이 Optional 인 이유 — 폴백 원칙:
 *   "AI 실패가 업무 실패가 되어서는 안 된다."
 *   구현체는 어떤 경우에도 예외를 던지지 않고 Optional.empty() 를 돌려준다.
 *   호출자는 try/catch 가 아니라 isPresent() 로 분기한다.
 */
public interface LlmClient {
    Optional<LlmResponse> complete(LlmRequest request);
}
