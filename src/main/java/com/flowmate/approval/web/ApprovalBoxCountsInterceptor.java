package com.flowmate.approval.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.flowmate.approval.domain.ApprovalBoxCounts;
import com.flowmate.approval.domain.BoxTab;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.org.security.LoginEmployee;

/**
 * 결재 건수를 모든 화면 모델에 싣는다 — 좌측 메뉴(sidebar.jsp)의 배지가 어느
 * 화면에서든 보여야 하기 때문이다. 결재함에 들어가야만 "내 차례 3건"을 알 수
 * 있으면, 정작 그것을 알아야 할 사람이 결재함에 들어갈 이유를 모른다.
 *
 * ★ 왜 @ControllerAdvice 가 아니라 인터셉터인가.
 *   @ModelAttribute 를 단 @ControllerAdvice 는 @RestController 에도 걸린다.
 *   그러면 AI 요약·사전점검 같은 AJAX 호출마다 이 집계 쿼리가 한 번씩 더
 *   나간다 — 화면에 쓰이지도 않는데. postHandle 은 뷰를 그리는 요청에만
 *   ModelAndView 를 주므로(@ResponseBody 는 null) 그 구분이 공짜로 된다.
 *
 * ★ 실패하면 조용히 넘어간다.
 *   이 값이 없으면 배지가 안 보일 뿐이고, 있으면 안 되는 일은 배지 하나
 *   때문에 화면 전체가 500 이 되는 것이다. 특히 오류 화면을 그리는 중에
 *   여기서 또 예외가 나면 원래 오류가 무엇이었는지조차 알 수 없게 된다.
 * 폴백 원칙(보조 장치가 죽어도 본 기능은 산다)과 같다.
 */
@Component
public class ApprovalBoxCountsInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApprovalBoxCountsInterceptor.class);

    /** 모델에 실리는 이름. sidebar.jsp·home.jsp·box.jsp 가 이 이름으로 읽는다 */
    static final String COUNTS_ATTRIBUTE = "boxCounts";
    static final String TABS_ATTRIBUTE = "boxTabs";

    private final ApprovalQueryService queryService;

    public ApprovalBoxCountsInterceptor(ApprovalQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // 뷰가 없는 요청(@ResponseBody, 정적 자원)과 리다이렉트는 모델을 그리지 않는다
        if (modelAndView == null || modelAndView.getViewName() == null
                || modelAndView.getViewName().startsWith("redirect:")) {
            return;
        }
        Long empId = loginEmpId();
        if (empId == null) {
            return;   // 로그인 화면 등 — 셀 사람이 없다
        }
        try {
            ApprovalBoxCounts counts = queryService.countBoxTabs(empId);
            modelAndView.addObject(COUNTS_ATTRIBUTE, counts);
            modelAndView.addObject(TABS_ATTRIBUTE, BoxTab.options(counts));
        } catch (RuntimeException e) {
            log.warn("결재 건수 집계에 실패했습니다. 배지 없이 화면을 그립니다.", e);
        }
    }

    private Long loginEmpId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginEmployee)) {
            return null;
        }
        return ((LoginEmployee) authentication.getPrincipal()).getEmpId();
    }
}
