package com.flowmate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

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
}
