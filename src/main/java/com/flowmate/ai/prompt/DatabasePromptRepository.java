package com.flowmate.ai.prompt;

import com.flowmate.ai.mapper.PromptMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ai_prompt} 테이블에서 프롬프트를 읽는다 - 커스터마이징 지점 4
 * ({@link PromptRepository})의 두 번째 구현.
 * {@code ai.prompt-repository=database} 로 켠다 - {@code PromptRepositoryConfig} 참고.
 *
 * 관리 화면은 아직 없다 - 지금은 {@code ai_prompt} 행을 psql 로 고치면 재배포
 * 없이 프롬프트가 바뀐다는 것까지 증명한다.
 *
 * ★ File 구현과 달리 캐시에 TTL 을 둔다({@link #DEFAULT_CACHE_TTL}). 이 구현체를
 * 고르는 이유 자체가 "재배포 없이 프롬프트를 바꾼다"인데 무기한 캐시하면 뜬
 * 동안 값이 절대 안 바뀌어 그 이유가 무효가 된다. 반대로 매 호출마다 DB 를
 * 때리면 캐시 미스마다 I/O 가 늘어난다. 짧은 TTL 이 그 사이다.
 */
public class DatabasePromptRepository implements PromptRepository {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    private final PromptMapper promptMapper;
    private final Duration cacheTtl;
    private final Map<String, CachedPrompt> cache = new ConcurrentHashMap<>();

    public DatabasePromptRepository(PromptMapper promptMapper) {
        this(promptMapper, DEFAULT_CACHE_TTL);
    }

    /** 테스트 전용 - 실제 5분을 기다리지 않고 TTL 만료를 재현하기 위한 생성자 */
    DatabasePromptRepository(PromptMapper promptMapper, Duration cacheTtl) {
        this.promptMapper = promptMapper;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public String load(String feature, String version) {
        String key = feature + "." + version;
        CachedPrompt cached = cache.get(key);
        if (cached != null && !cached.isExpired(cacheTtl)) {
            return cached.body;
        }
        String body = readFromDb(feature, version);
        cache.put(key, new CachedPrompt(body, LocalDateTime.now()));
        return body;
    }

    /** FilePromptRepository 와 같은 계약(PromptRepository 인터페이스 참고) - 없으면 예외, 조용히 빈 문자열이 아니다 */
    private String readFromDb(String feature, String version) {
        String body = promptMapper.findBody(feature, version);
        if (body == null) {
            throw new IllegalArgumentException(
                    "프롬프트가 ai_prompt 테이블에 없습니다: feature=" + feature + ", version=" + version);
        }
        return body;
    }

    private static final class CachedPrompt {
        private final String body;
        private final LocalDateTime cachedAt;

        private CachedPrompt(String body, LocalDateTime cachedAt) {
            this.body = body;
            this.cachedAt = cachedAt;
        }

        private boolean isExpired(Duration ttl) {
            return cachedAt.plus(ttl).isBefore(LocalDateTime.now());
        }
    }
}
