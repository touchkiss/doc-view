package com.liuzhihang.doc.view.service.gpt;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.liuzhihang.doc.view.common.util.StringUtil;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.service.gpt.impl.ChatGlmGptSupplier;
import com.liuzhihang.doc.view.service.gpt.impl.OpenAiGptSupplier;
import org.apache.commons.lang3.StringUtils;

/**
 * @author wangchao
 * @date 2024/03/05
 */
public class GptService {

    private static final Pattern SOURCE_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private volatile EasyDocConfig config;
    private Map<String, GptSupplier> gptSupplierMap;
    private static final Object LOCK = new Object();

    /**
     * 初始化
     *
     * @param config 配置
     */
    public void init(EasyDocConfig config) {
        if (gptSupplierMap != null && this.config != null) {
            return;
        }
        synchronized (LOCK) {
            if (gptSupplierMap != null && this.config != null) {
                return;
            }
            gptSupplierMap = ImmutableMap.<String, GptSupplier>builder()
                .put(Consts.CHATGLM_GPT, new ChatGlmGptSupplier().init(config))
                .put(Consts.OPENAI_GPT, new OpenAiGptSupplier().init(config))
                .build();
            this.config = config;
        }
    }

    /**
     * 聊天
     *
     * @param content 消息
     * @return {@code String}
     */
    public String chat(String content) {
        ensureInitialized();
        return chat(config.getTranslator(), content);
    }

    public String chat(String provider, String content) {
        ensureInitialized();
        GptSupplier translator = gptSupplierMap.get(provider);
        if (Objects.isNull(translator)) {
            return StringUtils.EMPTY;
        }
        return translator.chat(content);
    }

    public record CommentInput(String provider, String prompt) {}

    public CommentInput captureCommentInput(PsiElement declaration, String kind, String provider) {
        ensureInitialized();
        String folder = Consts.OPENAI_GPT.equals(provider) ? "openai" : "chatglm";
        String prompt;
        try (var resource = getClass().getResourceAsStream("/easydoc/prompts/" + folder + "/" + kind + ".prompt")) {
            if (resource == null) {
                throw new IllegalStateException("找不到 AI 注释模板");
            }
            prompt = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 AI 注释模板", e);
        }
        String source = declaration.getText();
        prompt = prompt.replace("{author}", StringUtils.defaultString(config.getAuthor()))
                .replace("{date}", java.time.LocalDate.now().toString())
                .replace("{code}", source);
        return new CommentInput(provider, withProjectTerms(declaration, source, prompt));
    }

    public String withProjectTerms(PsiElement declaration, String source, String prompt) {
        ensureInitialized();
        Map<String, String> mappings = config.getWordMapWithProject(declaration.getProject());
        Map<String, String> relevant = new TreeMap<>();
        SOURCE_IDENTIFIER.matcher(source).results().forEach(match -> {
            String identifier = match.group();
            addMapping(identifier.toLowerCase(Locale.ROOT), mappings, relevant);
            StringUtil.split(identifier).forEach(word -> addMapping(word, mappings, relevant));
        });
        if (!relevant.isEmpty()) {
            prompt += "\n\n项目术语释义（请采用下列释义）\n"
                    + com.alibaba.fastjson2.JSON.toJSONString(relevant);
        }
        return prompt;
    }

    public String requestComment(CommentInput input) {
        String response = chat(input.provider(), input.prompt());
        String normalized = response == null ? "" : response.strip();
        int reasoningEnd = normalized.lastIndexOf("</think>");
        if (reasoningEnd >= 0) {
            normalized = normalized.substring(reasoningEnd + "</think>".length()).strip();
        }
        int start = normalized.indexOf("/**");
        int end = start < 0 ? -1 : normalized.indexOf("*/", start + 3);
        if (end < 0) {
            throw new ModelRequestException("模型没有返回完整的 Javadoc（/** … */）。");
        }
        return normalized.substring(start, end + 2);
    }

    public static String endpointHost(String endpoint) {
        try {
            String host = java.net.URI.create(endpoint).getHost();
            return host == null ? "已配置的模型地址" : host;
        } catch (IllegalArgumentException | NullPointerException e) {
            return "已配置的模型地址";
        }
    }

    private static void addMapping(String term, Map<String, String> mappings, Map<String, String> relevant) {
        String meaning = mappings.get(term);
        if (StringUtils.isNotBlank(meaning)) {
            relevant.put(term, meaning);
        }
    }

    private void ensureInitialized() {
        if (config == null) {
            init(ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState());
        }
    }
}
