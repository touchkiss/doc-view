package com.star.easydoc.service.translator.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 本地翻译
 *
 * @author Administrator
 * @date 2024/08/03
 */
public class LocalTranslator extends AbstractTranslator {
    private static final Logger LOGGER = Logger.getInstance(LocalTranslator.class);

    private record Dictionary(Map<String, String> englishToChinese, Map<String, String> chineseToEnglish) {}

    private static final class Dictionaries {
        private static final Dictionary INSTANCE = loadDictionary();
    }

    @Override
    protected String translateCh2En(String text, PsiElement psiElement) {
        return Dictionaries.INSTANCE.chineseToEnglish().getOrDefault(text, text);
    }

    @Override
    protected String translateEn2Ch(String text, PsiElement psiElement) {
        Map<String, String> words = Dictionaries.INSTANCE.englishToChinese();
        String[] splits = text.split(StringUtils.SPACE);
        return Arrays.stream(splits).map(word -> words.getOrDefault(word, word))
                .collect(Collectors.joining());
    }

    private static Dictionary loadDictionary() {
        try (var resource = LocalTranslator.class.getResourceAsStream("/easydoc/words.json")) {
            if (resource == null) {
                throw new IOException("Missing dictionary resource");
            }
            String json = IOUtils.toString(resource, StandardCharsets.UTF_8);
            Map<String, String> words = JSON.parseObject(json, new TypeReference<Map<String, String>>() {});
            Map<String, String> reverse = new HashMap<>();
            words.forEach((word, meaning) -> reverse.putIfAbsent(meaning, word));
            return new Dictionary(Map.copyOf(words), Map.copyOf(reverse));
        } catch (IOException e) {
            LOGGER.warn("Local dictionary loading failed", e);
            return new Dictionary(Map.of(), Map.of());
        }
    }
}
