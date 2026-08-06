package com.flowmate;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 외부 Tomcat 10.1 에 WAR 로 배포될 때의 진입점.
 * main() 은 개발 중 실행에만 쓰이고, 컨테이너 배포 시에는 이 클래스가 사용된다.
 * WAR 패키징에서 이 클래스가 없으면 컨테이너가 Spring 컨텍스트를 시작하지 못한다.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(FlowmateApplication.class);
    }
}
