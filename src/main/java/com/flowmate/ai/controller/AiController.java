package com.flowmate.ai.controller;

import com.flowmate.ai.domain.PreflightRecord;
import com.flowmate.ai.domain.SummaryResult;
import com.flowmate.ai.feature.PreflightService;
import com.flowmate.ai.feature.SummaryService;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowmate.org.security.LoginEmployee;

/**
 * AI 기능 REST API. JSON 만 주고받는다 - JSP 뷰를 반환하지 않으므로
 * {@code GlobalExceptionHandler}(approval.controller/attendance.controller 전용,
 * HTML 오류 화면을 반환한다)의 범위 밖이다. 이 컨트롤러는 같은 예외를 JSON 친화적인
 * 상태 코드로 직접 변환한다.
 *
 * ★ AJAX 호출 경로가 둘로 나뉜다: 요약은 jQuery {@code $.ajax} 를 쓴다
 * ({@code common.js} 의 {@code $.ajaxSetup} 이 CSRF 헤더를 자동으로 붙인다). 사전점검은
 * write.jsp 의 상신 버튼을 눌렀을 때 서버 응답을 기다렸다가 분기해야 하는데, 그 흐름은
 * {@code fetch()} 로 짜는 편이 자연스럽다 - 그런데 {@code fetch} 는 {@code $.ajaxSetup}
 * 경로를 타지 않아 CSRF 헤더가 안 붙고 조용히 403 이 난다. 사전 점검이 도입한
 * {@code flowmateFetch}(common.js) 가 그 헤더를 같은 출처(meta 태그)에서 읽어 붙인다.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final SummaryService summaryService;
    private final PreflightService preflightService;

    public AiController(SummaryService summaryService, PreflightService preflightService) {
        this.summaryService = summaryService;
        this.preflightService = preflightService;
    }

    /**
     * 문서 요약. 권한은 {@code SummaryService} 가 {@code ApprovalQueryService.findDoc}
     * 로 검사한다. AI 실패(빈 응답·스키마 이탈)는 503 으로 - 문서 자체는 정상이므로
     * 화면은 이 상태만 보고 요약 영역에 안내 문구를 띄운다.
     */
    @PostMapping("/approvals/{approvalId}/summary")
    public ResponseEntity<SummaryResult> summary(@PathVariable Long approvalId,
                                                  @AuthenticationPrincipal LoginEmployee loginEmployee) {
        Optional<SummaryResult> result = summaryService.summarize(approvalId, loginEmployee.getEmpId());
        return result.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    /**
     * 상신 전 사전 점검. 상신 버튼을 누른 시점에
     * write.jsp 의 스크립트가 부른다.
     *
     * ★ D8: AI 호출이 실패하면(빈 응답·타임아웃·스키마 이탈 등 무엇이든)
     * {@code preflightService.check} 가 예외 없이 Optional.empty() 를 돌려주고, 이
     * 메서드는 그것을 503 으로 바꾼다. 화면 스크립트는 이 503 을(그리고 네트워크
     * 오류·타임아웃도 똑같이) "모달 없이 바로 상신"으로 처리한다 - 점검 실패가
     * 상신을 막으면 안 된다는 폴백 원칙이 여기서도 그대로 이어진다.
     */
    @PostMapping("/approvals/{approvalId}/preflight")
    public ResponseEntity<PreflightRecord> preflight(@PathVariable Long approvalId,
                                                      @AuthenticationPrincipal LoginEmployee loginEmployee) {
        Optional<PreflightRecord> result = preflightService.check(approvalId, loginEmployee.getEmpId());
        return result.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    /**
     * '무시하고 상신'. 권한 검사는 {@code PreflightService.ignore} 가
     * resultId 로 찾은 문서에 {@code ApprovalQueryService.findDoc} 을 다시 태워서 한다 -
     * 로그인 principal 이 아닌 값으로 다른 사람의 점검 결과를 무시 처리할 수 없다.
     */
    @PostMapping("/preflight/{resultId}/ignore")
    public ResponseEntity<Void> ignorePreflight(@PathVariable Long resultId,
                                                @AuthenticationPrincipal LoginEmployee loginEmployee) {
        preflightService.ignore(resultId, loginEmployee.getEmpId());
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(ApprovalNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
        // 본문 없이 상태 코드만 - 프런트는 !ok 로 분기하고 안내 문구는 화면이 정한다.
    }

    @ExceptionHandler(ApprovalAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void handleAccessDenied() {
    }
}
