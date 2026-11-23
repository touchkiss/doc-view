package com.liuzhihang.doc.view.service.translator;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.common.util.CollectionUtil;
import com.liuzhihang.doc.view.common.util.StringUtil;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.service.translator.impl.*;
import org.apache.commons.lang3.StringUtils;

/**
 * @author wangchao
 * @date 2019/08/25
 */
public class TranslatorService {

    private volatile EasyDocConfig config;
    private Map<String, Translator> translatorMap;
    private static final Object LOCK = new Object();

    /**
     * 初始化
     *
     * @param config 配置
     */
    public void init(EasyDocConfig config) {
        if (translatorMap != null && this.config != null) {
            return;
        }
        synchronized (LOCK) {
            if (translatorMap != null && this.config != null) {
                return;
            }
            translatorMap = ImmutableMap.<String, Translator>builder()
                .put(Consts.BAIDU_TRANSLATOR, new BaiduTranslator().init(config))
                .put(Consts.ALIYUN_TRANSLATOR, new AliyunTranslator().init(config))
                .put(Consts.TENCENT_TRANSLATOR, new TencentTranslator().init(config))
                .put(Consts.JINSHAN_TRANSLATOR, new JinshanTranslator().init(config))
                .put(Consts.YOUDAO_AI_TRANSLATOR, new YoudaoAiTranslator().init(config))
                .put(Consts.YOUDAO_TRANSLATOR, new YoudaoTranslator().init(config))
                .put(Consts.MICROSOFT_TRANSLATOR, new MicrosoftTranslator().init(config))
                .put(Consts.MICROSOFT_FREE_TRANSLATOR, new BingFreeTranslator().init(config))
                .put(Consts.GOOGLE_TRANSLATOR, new GoogleTranslator().init(config))
                .put(Consts.GOOGLE_FREE_TRANSLATOR, new GoogleFreeTranslator().init(config))
                .put(Consts.SIMPLE_SPLITTER, new SimpleSplitterTranslator().init(config))
                .put(Consts.LOCAL_DICTIONARY, new LocalTranslator().init(config))
                .put(Consts.CUSTOM_URL, new CustomTranslator().init(config))
                .put(Consts.OPENAI_GPT, new OpenAiTranslator().init(config))
                .put(Consts.CHATGLM_GPT, new OpenAiTranslator().init(config))
                .build();
            this.config = config;
        }
    }

    /**
     * 确保已初始化，如果未初始化则自动加载配置
     */
    private void ensureInitialized() {
        if (this.config == null) {
            EasyDocConfig easyDocConfig = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
            init(easyDocConfig);
        }
    }

    /**
     * 英译中
     *
     * @param source 源
     * @return {@link String}
     */
    public String translate(String source, PsiElement psiElement) {
        return prepareTranslation(source, psiElement).get();
    }

    public Supplier<String> prepareTranslation(String source, PsiElement element) {
        ensureInitialized();
        Map<String, String> mappings = config.getWordMapWithProject(element == null ? null : element.getProject());
        Translator translator = translatorMap.get(config.getTranslator());
        return () -> translate(source, element, mappings, translator);
    }

    private String translate(String source, PsiElement psiElement, Map<String, String> mappings, Translator translator) {
        String key = source.toLowerCase(Locale.ROOT);
        if (mappings.containsKey(key)) {
            return StringUtils.defaultString(mappings.get(key));
        }

        List<String> words = StringUtil.split(source);
        if (words.stream().anyMatch(mappings::containsKey)) {
            // 有自定义单词，使用默认模式，单个单词翻译
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                // 检查单词是否在映射表中（包括映射值为空的情况）
                if (mappings.containsKey(word)) {
                    // 在映射表中，直接使用映射值（可能为空，表示跳过翻译）
                    String res = mappings.get(word);
                    if (StringUtils.isNotBlank(res)) {
                        sb.append(res);
                    }
                    // 如果映射值为空，则跳过该单词（不追加任何内容）
                } else {
                    // 不在映射表中，使用其他翻译器翻译
                    String res = getFromOthers(word, psiElement, translator);
                    if (StringUtils.isBlank(res)) {
                        res = word;
                    }
                    sb.append(res);
                }
            }
            return sb.toString();
        } else {
            // 没有自定义单词，使用整句翻译，翻译更准确
            return getFromOthers(StringUtils.join(words, StringUtils.SPACE), psiElement, translator);
        }
    }

    /**
     * 英译中
     *
     * @param source 源
     * @return {@link String}
     */
    public String translateWithClass(String source, String className, Project project, PsiElement element) {
        return prepareTranslationWithClass(source, className, project, element).get();
    }

    public Supplier<String> prepareTranslationWithClass(String source, String className, Project project,
        PsiElement element) {
        ensureInitialized();
        if (!EasyDocConfig.ONLY_TRANSLATE.equals(config.getDocPriority()) && StringUtils.isNotBlank(className)) {
            PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className,
                GlobalSearchScope.projectScope(project));
            PsiDocComment docComment = aClass == null ? null : aClass.getDocComment();
            if (docComment != null) {
                for (PsiElement description : docComment.getDescriptionElements()) {
                    if (description instanceof PsiDocToken) {
                        String doc = description.getText().replaceAll("[ \n\t*]+", "");
                        if (StringUtils.isNotBlank(doc)) {
                            return () -> doc;
                        }
                    }
                }
            }
        }
        return prepareTranslation(source, element);
    }

    /**
     * 自动翻译
     *
     * @param source 源
     * @param psiElement 元素
     * @return {@link String}
     */
    public String autoTranslate(String source, PsiElement psiElement) {
        ensureInitialized();
        Translator translator = translatorMap.get(config.getTranslator());
        if (Objects.isNull(translator)) {
            return StringUtils.EMPTY;
        }
        return translator.en2Ch(source.replace("\n", " "), psiElement);
    }

    /**
     * 中译英
     *
     * @param source 源中文
     * @return {@link String}
     */
    public String translateCh2En(String source, PsiElement psiElement) {
        ensureInitialized();
        if (StringUtils.isBlank(source)) {
            return "";
        }
        Translator translator = translatorMap.get(config.getTranslator());
        if (translator == null) {
            return "";
        }
        String ch = translator.ch2En(source, psiElement);
        String[] chs = StringUtils.split(ch);
        List<String> chList = chs == null ? Lists.newArrayList() : Lists.newArrayList(chs);
        chList = chList.stream()
            .filter(c -> !Consts.STOP_WORDS.contains(c.toLowerCase()))
            .map(str -> str.replaceAll("[,.'\\-+;:`~]+", ""))
            .collect(Collectors.toList());

        if (CollectionUtil.isEmpty(chList)) {
            return "";
        }
        if (chList.size() == 1) {
            return chList.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chList.size(); i++) {
            if (StringUtils.isBlank(chList.get(i))) {
                continue;
            }
            if (Consts.STOP_WORDS.contains(chList.get(i).toLowerCase())) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(chList.get(i).toLowerCase());
            } else {
                String lowCh = chList.get(i).toLowerCase();
                sb.append(StringUtils.substring(lowCh, 0, 1).toUpperCase()).append(StringUtils.substring(lowCh, 1));
            }
        }
        return sb.toString();
    }

    private String getFromOthers(String word, PsiElement psiElement, Translator translator) {
        if (Objects.isNull(translator)) {
            return StringUtils.EMPTY;
        }
        String res = translator.en2Ch(word, psiElement);
        if (res != null) {
            res = res.replace("的", "");
        }
        return res;
    }

    public void clearCache() {
        ensureInitialized();
        translatorMap.values().forEach(Translator::clearCache);
    }
}
