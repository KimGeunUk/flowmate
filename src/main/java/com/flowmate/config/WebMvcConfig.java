package com.flowmate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.flowmate.approval.web.ApprovalBoxCountsInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApprovalBoxCountsInterceptor approvalBoxCountsInterceptor;

    public WebMvcConfig(ApprovalBoxCountsInterceptor approvalBoxCountsInterceptor) {
        this.approvalBoxCountsInterceptor = approvalBoxCountsInterceptor;
    }

    /**
     * src/main/webapp/static/** 를 /static/** 로 노출한다.
     *
     * addResourceLocations 의 "/static/" 는 classpath 가 아니라
     * ServletContext(웹 애플리케이션 루트) 기준 경로다. classpath: 접두사를 붙이면
     * src/main/resources/static 을 찾아 404가 된다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("/static/");
    }

    /**
     * 결재 건수를 모든 화면에 싣는다 (좌측 메뉴 배지).
     *
     * /api/** 를 빼는 이유: 그쪽은 전부 @ResponseBody 라 인터셉터가 어차피
     * ModelAndView 를 받지 못해 아무 일도 하지 않는다. 그래도 명시적으로
     * 제외해 둔다 — "AJAX 호출에는 이 쿼리가 붙지 않는다"를 설정에서 읽을 수
     * 있게 하려는 것이다. 나중에 API 가 뷰를 반환하도록 바뀌어도 조용히
     * 쿼리가 붙지 않는다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(approvalBoxCountsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/static/**", "/login", "/logout");
    }
}
