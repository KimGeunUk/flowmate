package com.flowmate.ai.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmate.ai.client.LlmClient;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.domain.SummaryResult;
import com.flowmate.ai.prompt.PromptRepository;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.config.AiProperties;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 기능 1 — 결재 문서 요약 (설계서 §6.4.5, 계획서 5 Task 3).
 *
 * ★ 권한: 문서를 볼 수 있는 사람만 요약도 볼 수 있어야 한다. 별도 규칙을 만들지
 * 않고 {@link ApprovalQueryService#findDoc(Long, Long)} 을 그대로 태운다 - 그
 * 메서드가 던지는 {@code ApprovalNotFoundException}/{@code ApprovalAccessDeniedException}
 * 은 "AI 실패"가 아니라 진짜 권한 문제이므로 여기서 삼키지 않고 그대로 올려보낸다
 * (호출자인 {@code AiController} 가 404/403 으로 바꾼다).
 *
 * ★ 반면 LLM 호출 자체의 실패(빈 응답, 스키마를 벗어난 JSON)는 예외가 아니라
 * {@code Optional.empty()} 로 흡수한다 (계획서 5 D8) - 문서 조회 권한 검사를 통과한
 * 뒤부터는 "AI 가 지금 안 될 뿐, 문서는 정상"이라는 뜻이어야 한다.
 */
@Service
public class SummaryService {

    /**
     * ★ PromptRepository 조회 키는 소문자다 - AiFeature.SUMMARY("SUMMARY")와 다른
     * 값 집합이다. 파일 구현({@code FilePromptRepository})은
     * {@code classpath:prompts/{feature}.{version}.txt} 를 그대로 읽으므로 실제
     * 파일명(summary.v1.txt)과 대소문자가 같아야 한다 - Linux Tomcat(WAR 배포
     * 대상)의 classpath 조회는 대소문자를 구분한다. AiFeature.SUMMARY 는
     * ai_result_cache/ai_call_log 컬럼 값(대문자 관례)이라는 별개의 용도다.
     */
    private static final String PROMPT_FEATURE = "summary";
    private static final String PROMPT_VERSION = "v1";

    private final ApprovalQueryService approvalQueryService;
    private final PromptRepository promptRepository;
    private final LlmClient llmClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SummaryService(ApprovalQueryService approvalQueryService,
                          PromptRepository promptRepository,
                          LlmClient llmClient,
                          AiProperties aiProperties) {
        this.approvalQueryService = approvalQueryService;
        this.promptRepository = promptRepository;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }

    /**
     * 문서 요약. 완료 기준(설계서 §9 5-1): 같은 문서를 두 번 부르면 두 번째는
     * {@code llmClient}(캐싱 데코레이터가 가장 바깥)가 캐시에서 응답한다 - 이
     * 메서드는 매번 같은 cache_key 가 나오도록 매번 같은 프롬프트를 다시 조립한다.
     *
     * ★ 기능 플래그(계획서 5 Task 7, 커스터마이징 지점 5): {@code ai.features.summary}
     * 가 꺼져 있으면 문서 조회조차 하지 않고 즉시 빈 결과다 - llmClient 는 물론
     * {@code approvalQueryService} 도 건드리지 않는다. 화면(ApprovalBoxController/
     * detail.jsp)은 이 메서드의 결과가 아니라 같은 플래그를 별도로 보고 요약
     * 영역 자체를 렌더링하지 않으므로, 여기서 empty 를 돌려주는 것은 "권한 있는
     * 사용자가 API 를 직접 두드렸을 때"에 대한 방어선이다.
     */
    public Optional<SummaryResult> summarize(Long approvalId, Long viewerId) {
        if (!aiProperties.getFeatures().isSummary()) {
            return Optional.empty();
        }
        ApprovalDoc doc = approvalQueryService.findDoc(approvalId, viewerId);

        LlmRequest request = buildRequest(doc, approvalId, viewerId);
        return llmClient.complete(request).flatMap(this::parseSafely);
    }

    private LlmRequest buildRequest(ApprovalDoc doc, Long approvalId, Long viewerId) {
        String instructions = promptRepository.load(PROMPT_FEATURE, PROMPT_VERSION);
        String documentSection = "[문서 제목]\n" + doc.getTitle() + "\n\n[문서 본문]\n" + doc.getContent();

        LlmRequest request = new LlmRequest();
        request.setFeature(AiFeature.SUMMARY);
        request.setPromptVersion(PROMPT_VERSION);
        request.setPrompt(instructions + "\n\n" + documentSection);
        request.setEmpId(viewerId);
        request.setApprovalId(approvalId);
        request.setOutputType(SummaryResult.class);
        return request;
    }

    /**
     * 구조화 출력이라도 응답 JSON 이 스키마를 벗어날 가능성은 남는다(모델의 거절
     * 경로는 이미 {@code ClaudeLlmClient} 가 걸러내지만, 그 밖의 형식 이탈까지
     * 전부 막지는 못한다). 그런 경우도 예외 대신 {@code Optional.empty()} 로
     * 처리해 D8 원칙을 끝까지 지킨다.
     */
    private Optional<SummaryResult> parseSafely(LlmResponse response) {
        try {
            return Optional.of(objectMapper.readValue(response.getText(), SummaryResult.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
