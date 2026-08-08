package com.flowmate.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * {@code classpath:prompts/{feature}.{version}.txt} 를 읽는다.
 *
 * ★ 읽은 것을 캐시한다 — 파일 I/O 를 매 호출마다 하지 않는다. 프롬프트 파일은
 *   애플리케이션이 떠 있는 동안 바뀌지 않으므로 한 번 읽으면 충분하다.
 */
@Repository
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
