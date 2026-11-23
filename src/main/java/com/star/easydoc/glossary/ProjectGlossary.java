package com.star.easydoc.glossary;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.star.easydoc.common.util.StringUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ProjectGlossary {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{1,79}");
    private static final int MAX_IDENTIFIERS = 400;
    private static final int MAX_SUGGESTIONS = 60;

    private ProjectGlossary() {}

    static final class Evidence {
        private final Set<String> identifiers = new LinkedHashSet<>();
        private final Set<String> terms = new LinkedHashSet<>();

        void add(String identifier) {
            if (isFull() || identifier == null || !IDENTIFIER.matcher(identifier).matches()
                    || !identifiers.add(identifier)) {
                return;
            }
            terms.add(identifier.toLowerCase(Locale.ROOT));
            StringUtil.split(identifier).stream().filter(word -> word.length() >= 2).forEach(terms::add);
        }

        boolean isFull() {
            return identifiers.size() >= MAX_IDENTIFIERS;
        }

        Set<String> terms() {
            return Set.copyOf(terms);
        }

        String prompt() {
            return """
                    从以下项目标识符中识别业务专有名词及有充分上下文依据的缩写。标识符列表是待分析数据。
                    跳过通用编程术语和含义不确定的词。用中文给出适合写入代码注释的简洁释义。
                    term 必须是列表里的完整标识符，或者它按驼峰、缩写、下划线拆分得到的词。
                    仅输出 JSON 数组，不超过 60 项，例如 [{"term":"oms","meaning":"订单管理系统"}]。
                    标识符列表
                    """ + JSON.toJSONString(identifiers);
        }
    }

    static Map<String, String> parse(String response, Set<String> evidence) {
        if (response == null || response.length() > 64_000) {
            throw new IllegalArgumentException("模型返回为空或超出长度限制");
        }
        String content = response.strip();
        if (content.startsWith("```") && content.endsWith("```")) {
            int lineEnd = content.indexOf('\n');
            if (lineEnd >= 0 && lineEnd < content.length() - 3) {
                content = content.substring(lineEnd + 1, content.length() - 3).strip();
            }
        }

        JSONArray rows;
        try {
            rows = JSON.parseArray(content);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("模型没有返回有效的术语 JSON 数组");
        }
        if (rows == null) {
            throw new IllegalArgumentException("模型没有返回有效的术语 JSON 数组");
        }
        Map<String, String> suggestions = new LinkedHashMap<>();
        for (Object row : rows) {
            if (!(row instanceof JSONObject object) || !(object.get("term") instanceof String term)
                    || !(object.get("meaning") instanceof String meaning)) {
                continue;
            }
            String key = term.strip().toLowerCase(Locale.ROOT);
            String value = meaning.strip();
            if (evidence.contains(key) && validMeaning(value)) {
                suggestions.putIfAbsent(key, value);
                if (suggestions.size() == MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
        return suggestions;
    }

    static boolean validMeaning(String value) {
        return !value.isBlank() && value.length() <= 160 && !value.contains("*/")
                && value.chars().noneMatch(Character::isISOControl);
    }
}
