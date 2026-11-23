package com.liuzhihang.doc.view.javadoc.service.variable;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.liuzhihang.doc.view.config.EasyDocConfig.CustomValue;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.AuthorVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.DateVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.DocVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.ParamsVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.ReturnVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.SeeVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.SinceVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.ThrowsVariableGenerator;
import com.liuzhihang.doc.view.javadoc.service.variable.impl.VersionVariableGenerator;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.commons.lang3.StringUtils;

/**
 * 变量生成器服务
 *
 * @author wangchao
 * @date 2019/12/08
 */
public class JavadocVariableGeneratorService {
    private static final Logger LOGGER = Logger.getInstance(JavadocVariableGeneratorService.class);
    private static final Pattern PATTERN = Pattern.compile("\\$[a-zA-Z0-9_-]*\\$");

    /**
     * 变量生成器映射
     */
    private final Map<String, VariableGenerator> variableGeneratorMap = ImmutableMap.<String, VariableGenerator>builder()
        .put("author", new AuthorVariableGenerator())
        .put("date", new DateVariableGenerator())
        .put("doc", new DocVariableGenerator())
        .put("params", new ParamsVariableGenerator())
        .put("return", new ReturnVariableGenerator())
        .put("see", new SeeVariableGenerator())
        .put("since", new SinceVariableGenerator())
        .put("throws", new ThrowsVariableGenerator())
        .put("version", new VersionVariableGenerator())
        .build();

    /**
     * 提取模板需要的 PSI 数据，延后求值和替换占位符。
     */
    public Supplier<String> prepare(PsiElement psiElement, String template, Map<String, CustomValue> customValueMap,
        Map<String, Object> innerVariableMap) {

        if (StringUtils.isBlank(template)) {
            return () -> "";
        }

        Matcher matcher = PATTERN.matcher(template);
        List<Entry<String, Supplier<String>>> variables = Lists.newArrayList();
        while (matcher.find()) {
            String placeholder = matcher.group();
            String key = StringUtils.substring(placeholder, 1, -1);
            if (StringUtils.isBlank(key)) {
                return () -> "";
            }
            VariableGenerator variableGenerator = variableGeneratorMap.get(key.toLowerCase());
            Supplier<String> value = variableGenerator == null
                ? () -> generateCustomVariable(customValueMap, innerVariableMap, placeholder)
                : variableGenerator.prepare(psiElement);
            variables.add(Map.entry(placeholder, value));
        }

        return () -> {
            Map<String, String> values = Maps.newHashMap();
            for (Entry<String, Supplier<String>> variable : variables) {
                values.put(variable.getKey(), variable.getValue().get());
            }
            List<String> keys = Lists.newArrayList(values.keySet());
            return StringUtils.replaceEach(template, keys.toArray(new String[0]),
                keys.stream().map(values::get).toArray(String[]::new));
        };
    }

    /**
     * 生成自定义变量
     *
     * @param customValueMap 自定义值
     * @param placeholder 占位符
     * @param innerVariableMap 内部变量映射
     * @return {@link String}
     */
    private String generateCustomVariable(Map<String, CustomValue> customValueMap, Map<String, Object> innerVariableMap,
        String placeholder) {
        Optional<CustomValue> valueOptional = customValueMap.entrySet().stream()
            .filter(entry -> placeholder.equalsIgnoreCase(entry.getKey())).map(Entry::getValue).findAny();
        // 找不到自定义方法，返回原占位符
        if (!valueOptional.isPresent()) {
            return placeholder;
        }
        CustomValue value = valueOptional.get();
        switch (value.getType()) {
            case STRING:
                return value.getValue();
            case GROOVY:
                try {
                    return new GroovyShell(new Binding(innerVariableMap)).evaluate(value.getValue()).toString();
                } catch (Exception e) {
                    LOGGER.error(String.format("groovy %s execute error, please check the syntax and return values:%s",
                        placeholder, value.getValue()), e);
                    return value.getValue();
                }
            default:
                return "";
        }
    }

}
