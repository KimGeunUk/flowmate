package com.flowmate.ai.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmate.ai.client.LlmClient;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.Finding;
import com.flowmate.ai.domain.LlmJson;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.domain.PreflightRecord;
import com.flowmate.ai.domain.PreflightResult;
import com.flowmate.ai.domain.PreflightVerdict;
import com.flowmate.ai.domain.RejectPattern;
import com.flowmate.ai.mapper.PreflightResultMapper;
import com.flowmate.ai.prompt.PromptRepository;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.mapper.RejectHistoryMapper;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.common.exception.ApprovalNotFoundException;
import com.flowmate.config.AiProperties;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 2 — 상신 전 사전 점검 (★ 이 Phase 의 핵심).
 *
 * 흐름:
 *   1) 같은 doc_type + dept_id 의 최근 반려 이력 10건 조회(없으면 전사로 확대)
 *   2) reason_category 별 빈도 집계
 *   3) [현재 문서 요지] + [과거 반려 패턴 + 빈도] 프롬프트 조립 → 구조화 출력으로 findings 수신
 *   4) WARN 이면 ai_preflight_result 에 기록 - PASS 는 기록하지 않는다({@link PreflightRecord}
 *      클래스 주석 참고)
 *
 * ★ 점검은 보조 장치다. LLM 호출이 실패하면(Optional.empty) 이 서비스도
 * 예외 없이 Optional.empty() 를 돌려준다 - {@code AiController} 는 그 상태를 503 으로
 * 바꾸고, 화면 스크립트는 그 어떤 실패(503·네트워크 오류·타임아웃)도 "모달 없이 바로
 * 상신"으로 처리한다. 사전 점검이 상신을 막을 수 있다면 그 자체로
 * 폴백 원칙 위반이다.
 *
 * ★ 권한: SummaryService/LeaveContextService 와 같은 원칙 - 새 규칙을 만들지 않고
 * {@link ApprovalQueryService#findDoc(Long, Long)} 를 그대로 태운다.
 */
@Service
public class PreflightService {

    /** PromptRepository 조회 키(소문자) - SummaryService 와 같은 규약(그 클래스 주석 참고) */
    private static final String PROMPT_FEATURE = "preflight";
    private static final String PROMPT_VERSION = "v1";

    /** 최근 반려 이력 10건 */
    private static final int RECENT_REJECT_LIMIT = 10;

    private final ApprovalQueryService approvalQueryService;
    private final RejectHistoryMapper rejectHistoryMapper;
    private final PreflightResultMapper preflightResultMapper;
    private final PromptRepository promptRepository;
    private final LlmClient llmClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PreflightService(ApprovalQueryService approvalQueryService,
                            RejectHistoryMapper rejectHistoryMapper,
                            PreflightResultMapper preflightResultMapper,
                            PromptRepository promptRepository,
                            LlmClient llmClient,
                            AiProperties aiProperties) {
        this.approvalQueryService = approvalQueryService;
        this.rejectHistoryMapper = rejectHistoryMapper;
        this.preflightResultMapper = preflightResultMapper;
        this.promptRepository = promptRepository;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }

    /**
     * 사전 점검. WARN 이면 반환하기 전에 ai_preflight_result 에 저장하고 그 resultId 를
     * 함께 돌려준다({@code AiController} 가 '무시하고 상신' 요청에서 그 id 를 쓴다).
     *
     * ★ 일부러 @Transactional 을 붙이지 않는다. 이 메서드는 조회 → **LLM 네트워크
     *   호출** → (WARN 일 때만) INSERT 순서로 돈다. 트랜잭션으로 묶으면 LLM 호출이
     *   끝날 때까지 커넥션 하나를 붙잡고 있게 되고(타임아웃이 30초다), 사전 점검은
     *   상신할 때마다 도므로 동시 상신 몇 건이면 병목이 LLM 처리량이 아니라
     *   커넥션 풀 고갈이 된다. 묶어서 얻는 것도 없다 - INSERT 는 한 문장이다.
     *
     * ★ 기능 플래그(커스터마이징 지점 5)가 꺼져 있으면 즉시 빈 결과다 - 반려 이력
     *   집계도 문서 조회도 하지 않는다. 화면은 별도로 같은 플래그를 보고 스크립트
     *   자체를 등록하지 않으므로, 여기서 empty 를 주는 것은 API 를 직접 두드리는
     *   경우에 대한 방어선이다.
     */
    public Optional<PreflightRecord> check(Long approvalId, Long viewerId) {
        if (!aiProperties.getFeatures().isPreflight()) {
            return Optional.empty();
        }
        ApprovalDoc doc = approvalQueryService.findDoc(approvalId, viewerId);

        List<RejectPattern> patterns = aggregatePatterns(doc.getDocType(), doc.getDeptId());
        LlmRequest request = buildRequest(doc, patterns, approvalId, viewerId);

        return llmClient.complete(request)
                .flatMap(this::parseSafely)
                .map(result -> toRecord(result, approvalId));
    }

    /**
     * '무시하고 상신'을 기록한다. resultId 로 행을 찾아 그 행의
     * approvalId 로 다시 열람 권한을 검사한다 - 다른 사람의 점검 결과 id 를 넣어
     * 아무 문서나 무시 처리하지 못하게 막는다(viewer identity 는 항상 로그인
     * principal 에서 온다).
     */
    @Transactional
    public void ignore(Long resultId, Long actorId) {
        PreflightRecord record = preflightResultMapper.findById(resultId);
        if (record == null) {
            throw new ApprovalNotFoundException(resultId);
        }
        approvalQueryService.findDoc(record.getApprovalId(), actorId);
        preflightResultMapper.markIgnored(resultId);
    }

    /**
     * 같은 doc_type + dept_id 의 최근 반려 이력을 reason_category 별로 집계한다.
     * 없으면 전사로 확대한다.
     */
    private List<RejectPattern> aggregatePatterns(String docType, Long deptId) {
        List<String> categories =
                rejectHistoryMapper.findRecentReasonCategories(docType, deptId, RECENT_REJECT_LIMIT);
        if (categories.isEmpty()) {
            categories = rejectHistoryMapper.findRecentReasonCategoriesCompanyWide(docType, RECENT_REJECT_LIMIT);
        }
        return toPatterns(categories);
    }

    private List<RejectPattern> toPatterns(List<String> categories) {
        Map<String, Long> counts = categories.stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> new RejectPattern(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(RejectPattern::getCount).reversed())
                .collect(Collectors.toList());
    }

    private LlmRequest buildRequest(ApprovalDoc doc, List<RejectPattern> patterns,
                                    Long approvalId, Long viewerId) {
        String instructions = promptRepository.load(PROMPT_FEATURE, PROMPT_VERSION);
        String documentSection = "[문서 유형]\n" + doc.getDocTypeLabel()
                + "\n\n[문서 제목]\n" + doc.getTitle()
                + "\n\n[문서 본문]\n" + doc.getContent();
        String patternsSection = buildPatternsSection(patterns);

        LlmRequest request = new LlmRequest();
        request.setFeature(AiFeature.PREFLIGHT);
        request.setPromptVersion(PROMPT_VERSION);
        request.setPrompt(instructions + "\n\n" + documentSection + "\n\n" + patternsSection);
        request.setEmpId(viewerId);
        request.setApprovalId(approvalId);
        request.setOutputType(PreflightResult.class);
        return request;
    }

    /**
     * ★ 여기 들어가는 것은 reasonCategory 와 count 뿐이다 - RejectPattern 자체가
     * reason_text 를 담을 수 없는 모양이므로(그 클래스 주석 참고), 이 메서드가 아무리
     * 문자열을 조합해도 반려 원문이 프롬프트에 섞일 방법이 없다.
     */
    private String buildPatternsSection(List<RejectPattern> patterns) {
        if (patterns.isEmpty()) {
            return "[과거 반려 패턴]\n해당 조합의 과거 반려 이력이 없습니다.";
        }
        StringBuilder sb = new StringBuilder("[과거 반려 패턴 - 최근 반려 이력 기준 유형별 건수]\n");
        for (RejectPattern p : patterns) {
            sb.append("- ").append(RejectReason.labelOf(p.getReasonCategory()))
                    .append(" (").append(p.getReasonCategory()).append("): ")
                    .append(p.getCount()).append("건\n");
        }
        return sb.toString();
    }

    /**
     * 구조화 출력이라도 응답 JSON 이 스키마를 벗어날 가능성은 남는다(SummaryService 와
     * 같은 이유). 예외 대신 Optional.empty() 로 폴백 원칙을 지킨다.
     *
     * ★ {@link LlmJson#mapper()} 를 쓴다 - 모델이 덤으로 붙인 필드 때문에 응답
     * 전체가 버려지지 않게 하려는 것이다(그 클래스 주석에 실제 사례가 있다).
     * 요약에서 먼저 터졌지만 점검이라고 안전한 것은 아니다.
     */
    private Optional<PreflightResult> parseSafely(LlmResponse response) {
        try {
            return Optional.of(LlmJson.mapper().readValue(response.getText(), PreflightResult.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * verdict=WARN 일 때만 ai_preflight_result 에 저장한다({@link PreflightRecord}
     * 클래스 주석 참고). PASS 는 저장 없이 그대로 반환한다 - resultId 는 null 로 남고,
     * 화면은 모달을 띄우지 않으므로 애초에 그 id 를 쓸 일이 없다.
     */
    private PreflightRecord toRecord(PreflightResult result, Long approvalId) {
        List<Finding> findings = result.getFindings() != null ? result.getFindings() : List.of();

        PreflightRecord record = new PreflightRecord();
        record.setApprovalId(approvalId);
        record.setVerdict(result.getVerdict());
        record.setFindings(findings);
        record.setIgnoredYn("N");

        if (PreflightVerdict.WARN.equals(result.getVerdict())) {
            record.setFindingsJson(toJson(findings));
            preflightResultMapper.insert(record);
        }
        return record;
    }

    private String toJson(List<Finding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("사전 점검 결과를 저장용 JSON 으로 변환할 수 없습니다", e);
        }
    }
}
