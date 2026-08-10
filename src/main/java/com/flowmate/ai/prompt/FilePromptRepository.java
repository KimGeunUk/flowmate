package com.flowmate.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code classpath:prompts/{feature}.{version}.txt} 를 읽는다 - 커스터마이징 지점 4
 * ({@link PromptRepository})의 첫 번째 구현. {@code ai.prompt-repository=file}(기본값)
 * 로 켠다 - {@code PromptRepositoryConfig} 참고.
 *
 * ★ 읽은 것을 캐시한다 — 파일 I/O 를 매 호출마다 하지 않는다. 프롬프트 파일은
 *   애플리케이션이 떠 있는 동안 바뀌지 않으므로 한 번 읽으면 충분하다(재배포해야
 *   바뀐다 - {@link DatabasePromptRepository} 는 그 전제가 달라 TTL 을 둔다, 그
 *   클래스 주석 참고).
 *
 * ★ @Repository 를 붙이지 않는다 - PromptRepositoryConfig 가 @Bean 으로 조건부
 *   배선한다(ApprovalLinePolicy/WorkTimePolicy/LeaveGrantPolicy 의 두 구현체와
 *   같은 관례 - 그 구현체들도 컴포넌트 스캔 대상이 아니다). 컴포넌트 스캔과
 *   @Bean 배선을 동시에 켜 두면 PromptRepository 타입 빈이 둘 생겨 컨텍스트
 *   기동이 NoUniqueBeanDefinitionException 으로 실패한다.
 */
public class FilePromptRepository implements PromptRepository {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String load(String feature, String version) {
        String key = feature + "." + version;
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String content = readFile(key);
        cache.put(key, content);
        return content;
    }

    private String readFile(String key) {
        String path = "/prompts/" + key + ".txt";
        try (InputStream in = FilePromptRepository.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("프롬프트 파일이 없습니다: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("프롬프트 파일을 읽을 수 없습니다: " + path, e);
        }
    }
}
