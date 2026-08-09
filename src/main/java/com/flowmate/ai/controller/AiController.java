package com.flowmate.ai.controller;

import com.flowmate.ai.domain.SummaryResult;
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
 * AI 기능 REST API (설계서 §6.4). JSON 만 주고받는다 - JSP 뷰를 반환하지 않으므로
 * {@code GlobalExceptionHandler}(approval.controller/attendance.controller 전용,
 * HTML 오류 화면을 반환한다)의 범위 밖이다. 이 컨트롤러는 같은 예외를 JSON 친화적인
 * 상태 코드로 직접 변환한다.
 *
 * ★ AJAX 는 jQuery {@code $.ajax} 를 쓴다(계획서 5 D5 는 fetch() 래퍼를 Task 6 의
 * 사전점검 모달을 위해 도입한다 - 아직 없다). {@code common.js} 가 이미
 * {@code $.ajaxSetup} 으로 모든 jQuery AJAX 요청에 CSRF 헤더를 붙이므로, 이 API를
 * jQuery 로 호출하는 한 별도 배선이 필요 없다.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final SummaryService summaryService;

    public AiController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * 문서 요약. 권한은 {@code SummaryService} 가 {@code ApprovalQueryService.findDoc}
     * 로 검사한다. AI 실패(빈 응답·스키마 이탈)는 503 으로 - 문서 자체는 정상이므로
     * 화면은 이 상태만 보고 요약 영역에 안내 문구를 띄운다(계획서 5 D8).
     */
    @PostMapping("/approvals/{approvalId}/summary")
    public ResponseEntity<SummaryResult> summary(@PathVariable Long approvalId,
                                                  @AuthenticationPrincipal LoginEmployee loginEmployee) {
        Optional<SummaryResult> result = summaryService.summarize(approvalId, loginEmployee.getEmpId());
        return result.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
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
