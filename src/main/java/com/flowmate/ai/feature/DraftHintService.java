package com.flowmate.ai.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flowmate.ai.client.LlmClient;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.DraftHint;
import com.flowmate.ai.domain.DraftHintCommand;
import com.flowmate.ai.domain.DraftSuggestion;
import com.flowmate.ai.domain.LlmJson;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.domain.RejectPattern;
import com.flowmate.ai.prompt.PromptRepository;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.mapper.RejectHistoryMapper;
import com.flowmate.config.AiProperties;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 기능 4 — 기안 본문 제안. 작성자가 버튼을 눌렀을 때만 돈다.
 *
 * ★ 사전점검과 같은 데이터를 반대 방향으로 쓴다.
 *   사전점검  상신 직전에 "이대로 올리면 이 유형으로 반려될 수 있다"
 *   이 기능    쓰기 시작할 때  "이 부서에서 자주 반려된 항목을 미리 넣어 두자"
 *   둘 다 근거는 {@code approval_reject_history} 의 유형별 빈도 하나뿐이다.
 *
 * ★ 과거 승인 문서의 본문을 읽지 않는다. "이 부서에서 승인된 문서는 이렇게 쓴다"가
 *   더 좋은 근거처럼 보이지만, 기안자가 볼 권한이 없는 문서의 본문이 제안을 통해
 *   간접적으로 새어 나갈 수 있다. 반려 원문 대신 유형과 건수만 쓰는 것과 같은 판단이다.
 *
 * ★ 타이핑 중이 아니라 버튼을 눌렀을 때만 부른다. 입력이 바뀔 때마다 부르면 캐시 키가
 *   매번 달라져 히트율이 0 이 되고, 실측 지연(1.1~1.6초)이 타이핑 속도를 못 따라가
 *   도착할 때마다 낡은 제안이 된다. 호출 시점을 사용자가 정하면 그 둘이 모두 사라진다.
 */
@Service
public class DraftHintService {

    private static final String PROMPT_FEATURE = "draft-hint";
    private static final String PROMPT_VERSION = "v1";

    /** 사전점검과 같은 창을 본다 - 두 기능이 다른 과거를 보면 말이 어긋난다 */
    private static final int RECENT_REJECT_LIMIT = 10;

    private final RejectHistoryMapper rejectHistoryMapper;
    private final EmployeeMapper employeeMapper;
    private final PromptRepository promptRepository;
    private final LlmClient llmClient;
    private final AiProperties aiProperties;

    public DraftHintService(RejectHistoryMapper rejectHistoryMapper,
                            EmployeeMapper employeeMapper,
                            PromptRepository promptRepository,
                            LlmClient llmClient,
                            AiProperties aiProperties) {
        this.rejectHistoryMapper = rejectHistoryMapper;
        this.employeeMapper = employeeMapper;
        this.promptRepository = promptRepository;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }

    /**
     * 본문 초안을 제안한다. 실패하면 예외가 아니라 빈 결과다 - 제안이 안 와도 기안
     * 작성은 그대로 이어져야 한다(폴백 원칙).
     *
     * ★ 문서를 저장하기 전에 부르므로 approvalId 가 없다. 화면이 보낸 값과 로그인
     *   사원의 부서만 쓰므로 남의 문서에 닿을 경로가 없다.
     */
    public Optional<DraftHint> suggest(DraftHintCommand command, Long empId) {
        if (!aiProperties.getFeatures().isDraftHint()) {
            return Optional.empty();
        }
        if (command == null || command.getDocType() == null) {
            return Optional.empty();
        }
        Employee drafter = employeeMapper.findById(empId);
        if (drafter == null) {
            return Optional.empty();
        }

        List<RejectPattern> patterns = aggregatePatterns(command.getDocType(), drafter.getDeptId());

        return llmClient.complete(buildRequest(command, patterns, empId))
                .flatMap(this::parseSafely)
                .map(suggestion -> toHint(suggestion, patterns));
    }

    /** 같은 유형·부서의 최근 반려를 유형별로 센다. 없으면 전사로 넓힌다 */
    private List<RejectPattern> aggregatePatterns(String docType, Long deptId) {
        List<String> categories =
                rejectHistoryMapper.findRecentReasonCategories(docType, deptId, RECENT_REJECT_LIMIT);
        if (categories.isEmpty()) {
            categories = rejectHistoryMapper.findRecentReasonCategoriesCompanyWide(docType, RECENT_REJECT_LIMIT);
        }
        Map<String, Long> counts = categories.stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> new RejectPattern(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(RejectPattern::getCount).reversed())
                .collect(Collectors.toList());
    }

    private LlmRequest buildRequest(DraftHintCommand command, List<RejectPattern> patterns, Long empId) {
        String written = command.getContent() == null || command.getContent().isBlank()
                ? "(아직 아무것도 쓰지 않았습니다)"
                : command.getContent();

        LlmRequest request = new LlmRequest();
        request.setFeature(AiFeature.DRAFT_HINT);
        request.setPromptVersion(PROMPT_VERSION);
        request.setPrompt(promptRepository.load(PROMPT_FEATURE, PROMPT_VERSION)
                + "\n\n[문서 유형]\n" + DocType.labelOf(command.getDocType())
                + "\n\n[문서 제목]\n" + (command.getTitle() == null ? "(미입력)" : command.getTitle())
                + "\n\n[작성 중인 내용]\n" + written
                + "\n\n" + buildPatternsSection(patterns));
        request.setEmpId(empId);
        request.setOutputType(DraftSuggestion.class);
        return request;
    }

    /** 사전점검과 같은 모양 - 유형과 건수뿐이고 반려 원문은 들어갈 자리가 없다 */
    private String buildPatternsSection(List<RejectPattern> patterns) {
        if (patterns.isEmpty()) {
            return "[이 부서에서 자주 나온 반려 유형]\n해당 조합의 과거 반려 이력이 없습니다.";
        }
        StringBuilder sb = new StringBuilder("[이 부서에서 자주 나온 반려 유형]\n");
        for (RejectPattern p : patterns) {
            sb.append("- ").append(RejectReason.labelOf(p.getReasonCategory()))
                    .append(": ").append(p.getCount()).append("건\n");
        }
        return sb.toString();
    }

    /** 빈 초안은 실패로 본다 - 빈 제안을 화면에 띄우느니 안내 문구가 낫다 */
    private Optional<DraftSuggestion> parseSafely(LlmResponse response) {
        try {
            DraftSuggestion suggestion = LlmJson.mapper().readValue(response.getText(), DraftSuggestion.class);
            return suggestion.getDraft() == null || suggestion.getDraft().isBlank()
                    ? Optional.empty()
                    : Optional.of(suggestion);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** 모델이 쓴 초안에 서버가 센 근거를 붙여 응답을 만든다 */
    private DraftHint toHint(DraftSuggestion suggestion, List<RejectPattern> patterns) {
        DraftHint hint = new DraftHint();
        hint.setDraft(suggestion.getDraft());
        hint.setBasedOn(patterns);
        return hint;
    }
}
