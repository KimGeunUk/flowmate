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
 * ★ 읽은 것을 무기한 캐시한다 - 파일은 재배포해야 바뀌므로 한 번 읽으면 충분하다
 *   ({@link DatabasePromptRepository} 는 전제가 달라 TTL 을 둔다).
 *
 * ★ @Repository 를 붙이지 않는다 - PromptRepositoryConfig 가 @Bean 으로 조건부
 *   배선하므로, 컴포넌트 스캔까지 켜면 같은 타입 빈이 둘 생겨 기동이 실패한다
 *   (정책 구현체들도 같은 이유로 스캔 대상이 아니다).
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
