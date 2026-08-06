package com.flowmate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 컨텍스트가 끝까지 로드되는지만 확인한다.
 *
 * 이름이 *Tests 가 아니라 *IT 인 이유:
 * Task 5 에서 DataSource 를 추가하면 이 테스트가 DB 연결을 요구하게 된다.
 * *Tests 였다면 그 순간부터 mvnw test 가 Docker 없이 실패해
 * "단위 테스트는 Docker 없이 돈다" 는 규칙이 깨진다. Failsafe(*IT)에 두어 경계를 지킨다.
 */
@SpringBootTest
class FlowmateApplicationIT {

    @Test
    @DisplayName("Spring 컨텍스트가 로드된다")
    void contextLoads() {
        // 컨텍스트 로딩 자체가 검증 대상이다. 실패하면 예외로 터진다.
    }
}
