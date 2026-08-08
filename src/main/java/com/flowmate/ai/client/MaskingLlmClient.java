package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mask.MaskResult;
import com.flowmate.ai.mask.SensitiveDataMasker;
import java.util.Optional;

/**
 * 프롬프트를 마스킹해서 위임한다.
 *
 * ★ 체인에서 이 위치(캐싱 안쪽, 로깅 바깥쪽)가 중요하다:
 *   - 캐싱보다 안쪽이라야 캐시에 원문이 저장되지 않는다
 *   - 실제 호출보다 바깥이면 어떤 경로로 들어와도 원문이 나가지 않는다
 *   순서가 뒤집혀도 컴파일은 되고 테스트도 대부분 통과한다.
 *   LlmChainIT 가 이 순서를 단정한다.
 *
 * 복원하지 않는다 (설계서 §6.4.2 정책 1). mask() 가 돌려준 매핑을 버린다.
 */
public class MaskingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final SensitiveDataMasker masker;

    public MaskingLlmClient(LlmClient delegate, SensitiveDataMasker masker) {
        this.delegate = delegate;
        this.masker = masker;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        MaskResult maskResult = masker.mask(request.getPrompt());

        LlmRequest maskedRequest = new LlmRequest();
        maskedRequest.setFeature(request.getFeature());
        maskedRequest.setPromptVersion(request.getPromptVersion());
        maskedRequest.setPrompt(maskResult.getMasked());
        maskedRequest.setEmpId(request.getEmpId());
        maskedRequest.setApprovalId(request.getApprovalId());

        // 매핑은 의도적으로 버린다 - 요약·점검 결과에 원문을 복원해 넣지 않는다.
        return delegate.complete(maskedRequest);
    }
}
