package com.flowmate.ai.mask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 외부 LLM 으로 나가는 텍스트에서 민감정보를 토큰으로 치환한다.
 *
 * ★ 설계 원칙 — 오탐은 허용하고 미탐은 불허한다.
 *
 *   계좌번호 패턴 \d{2,6}-\d{2,6}-\d{2,8} 은 넓어서 문서번호나 날짜 일부를
 *   계좌로 오인할 수 있다. 그래도 좁히지 않는다.
 *   오탐: 요약 품질이 조금 떨어진다.
 *   미탐: 개인정보가 외부로 나간다.
 *   이 비대칭은 의도된 선택이다. 정확도를 높인다는 이유로 패턴을 좁히지 말 것.
 *   (SensitiveDataMaskerTest 의 docNoLooksLikeAccountAndThatIsAccepted 가 이를 고정한다)
 *
 * 순서가 중요하다. 주민번호(6-7)를 계좌(2~6-2~6-2~8)보다 먼저 검사하지 않으면
 * 계좌 패턴이 주민번호의 일부를 먼저 먹는다.
 */
@Component
public class SensitiveDataMasker {

    /** 검사 순서가 곧 우선순위다. 좁은 패턴이 넓은 패턴보다 앞에 온다. */
    private static final Rule[] RULES = {
        new Rule("RRN",   Pattern.compile("\\d{6}-\\d{7}")),
        new Rule("CARD",  Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}")),
        new Rule("PHONE", Pattern.compile("01\\d-\\d{3,4}-\\d{4}")),
        new Rule("BIZ",   Pattern.compile("\\d{3}-\\d{2}-\\d{5}")),
        new Rule("ACCT",  Pattern.compile("\\d{2,6}-\\d{2,6}-\\d{2,8}")),
        new Rule("EMAIL", Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"))
    };

    /**
     * 마스킹한다. 복원용 매핑을 함께 돌려준다.
     *
     * 복원 매핑을 돌려주되 요약·점검 기능에서는 쓰지 않는다.
     * 요약문에 계좌번호가 필요 없고, 복원하면 마스킹의 목적이 절반 사라진다.
     */
    public MaskResult mask(String text) {
        if (text == null || text.isEmpty()) {
            return new MaskResult(text, new LinkedHashMap<>());
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        Map<String, String> valueToToken = new LinkedHashMap<>();
        String result = text;

        for (Rule rule : RULES) {
            Matcher m = rule.pattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            int counter = countExisting(mapping, rule.label);
            while (m.find()) {
                String matched = m.group();
                String token = valueToToken.get(matched);
                if (token == null) {
                    token = "[[" + rule.label + "_" + (++counter) + "]]";
                    valueToToken.put(matched, token);
                    mapping.put(token, matched);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(token));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return new MaskResult(result, mapping);
    }

    /** 토큰을 원문으로 되돌린다. 기본적으로 쓰지 않는다 — 마스킹의 목적이 절반 사라지기 때문이다. */
    public String restore(String masked, Map<String, String> mapping) {
        if (masked == null) {
            return null;
        }
        String result = masked;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private int countExisting(Map<String, String> mapping, String label) {
        int n = 0;
        for (String token : mapping.keySet()) {
            if (token.startsWith("[[" + label + "_")) {
                n++;
            }
        }
        return n;
    }

    private static final class Rule {
        private final String label;
        private final Pattern pattern;

        private Rule(String label, Pattern pattern) {
            this.label = label;
            this.pattern = pattern;
        }
    }
}
