package com.flowmate.ai.mask;

import java.util.Collections;
import java.util.Map;

/**
 * {@link SensitiveDataMasker#mask(String)} 의 결과.
 *
 * masked 는 토큰으로 치환된 텍스트, mapping 은 토큰 → 원문 복원용 매핑이다.
 * mapping 은 기본적으로 쓰지 않는다 — 필요할 때만
 * {@link SensitiveDataMasker#restore(String, Map)} 에 넘긴다.
 */
public class MaskResult {

    private final String masked;
    private final Map<String, String> mapping;

    public MaskResult(String masked, Map<String, String> mapping) {
        this.masked = masked;
        this.mapping = mapping;
    }

    public String getMasked() {
        return masked;
    }

    public Map<String, String> getMapping() {
        return Collections.unmodifiableMap(mapping);
    }
}
