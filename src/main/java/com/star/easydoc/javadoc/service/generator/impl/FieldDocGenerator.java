package com.star.easydoc.javadoc.service.generator.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.javadoc.PsiDocComment;
import com.star.easydoc.common.util.VcsUtil;
import com.star.easydoc.config.EasyDocConfig;
import com.star.easydoc.config.EasyDocConfigComponent;
import com.star.easydoc.javadoc.service.variable.JavadocVariableGeneratorService;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 属性文档生成器
 *
 * @author wangchao
 * @date 2019/11/12
 */
public class FieldDocGenerator extends AbstractDocGenerator {

    private EasyDocConfig config = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
    private JavadocVariableGeneratorService javadocVariableGeneratorService = ApplicationManager.getApplication().getService(
        JavadocVariableGeneratorService.class);

    /** 简单模板 */
    private static final String SIMPLE_TEMPLATE = "/** $DOC$ */";
    /** 文档模板 */
    private static final String DOC_TEMPLATE = "/**\n"
        + " * $DOC$\n"
        + " */";

    @Override
    public Supplier<String> prepare(PsiElement psiElement) {
        if (!(psiElement instanceof PsiField)) {
            return () -> "";
        }
        PsiField psiField = (PsiField)psiElement;

        // 检查字段是否在排除列表中
        if (isExcludedField(config, psiField.getName())) {
            return () -> "";
        }

        PsiDocComment docComment = psiField.getDocComment();
        if (EasyDocConfig.COVER_MODE_IGNORE.equals(config.getCoverMode()) && docComment != null) {
            return () -> null;
        }

        String template;
        if (BooleanUtils.isTrue(config.getSimpleFieldDoc())) {
            template = SIMPLE_TEMPLATE;
        } else {
            template = DOC_TEMPLATE;
        }
        if (config.getFieldTemplateConfig() != null
            && Boolean.FALSE.equals(config.getFieldTemplateConfig().getIsDefault())) {
            template = config.getFieldTemplateConfig().getTemplate();
        }
        Supplier<String> javadoc = javadocVariableGeneratorService.prepare(psiField, template,
            config.getFieldTemplateConfig().getCustomMap(), getFieldInnerVariable(psiField));
        return javadoc;
    }

    /**
     * 获取字段内部的变量
     *
     * @param psiField psi属性
     * @return {@link java.util.Map<java.lang.String,java.lang.Object>}
     */
    private Map<String, Object> getFieldInnerVariable(PsiField psiField) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("author", config.getAuthor());
        map.put("fieldName", psiField.getName());
        map.put("fieldType", psiField.getType().getCanonicalText());
        map.put("branch", VcsUtil.getCurrentBranch(psiField.getProject()));
        map.put("projectName", psiField.getProject().getName());
        return map;
    }

    @Override
    protected EasyDocConfig getConfig() {
        return config;
    }

    /**
     * 检查字段是否在排除列表中
     *
     * @param fieldName 字段名
     * @return 是否排除
     */
    public static boolean isExcludedField(EasyDocConfig config, String fieldName) {
        String excludeFieldNames = config.getExcludeFieldNames();
        if (StringUtils.isBlank(excludeFieldNames)) {
            return false;
        }
        Set<String> excludedFields = Arrays.stream(excludeFieldNames.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());
        return excludedFields.contains(fieldName);
    }
}
