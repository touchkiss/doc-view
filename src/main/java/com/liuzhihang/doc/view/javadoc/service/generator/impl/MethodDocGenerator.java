package com.liuzhihang.doc.view.javadoc.service.generator.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.liuzhihang.doc.view.common.util.VcsUtil;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.javadoc.service.variable.JavadocVariableGeneratorService;

/**
 * 方法文档生成器
 *
 * @author wangchao
 * @date 2019/11/12
 */
public class MethodDocGenerator extends AbstractDocGenerator {

    /** 配置 */
    private EasyDocConfig config = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
    private JavadocVariableGeneratorService javadocVariableGeneratorService = ApplicationManager.getApplication().getService(
        JavadocVariableGeneratorService.class);

    @Override
    public Supplier<String> prepare(PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            return () -> "";
        }
        PsiMethod psiMethod = (PsiMethod)psiElement;
        PsiDocComment docComment = psiMethod.getDocComment();
        if (EasyDocConfig.COVER_MODE_IGNORE.equals(config.getCoverMode()) && docComment != null) {
            return () -> null;
        }

        String template = getDefaultTemplate(psiMethod);
        if (config.getMethodTemplateConfig() != null
            && Boolean.FALSE.equals(config.getMethodTemplateConfig().getIsDefault())) {
            template = config.getMethodTemplateConfig().getTemplate();
        }

        Supplier<String> javadoc = javadocVariableGeneratorService.prepare(psiMethod, template,
            config.getMethodTemplateConfig().getCustomMap(), getMethodInnerVariable(psiMethod));
        return javadoc;
    }

    /**
     * 获取默认模板
     *
     * @param psiMethod PSI 方法
     * @return {@link String }
     */
    private String getDefaultTemplate(PsiMethod psiMethod) {
        String paramsKey = " * $PARAMS$\n";
        if (psiMethod.getParameterList().getParameters().length == 0) {
            paramsKey = "";
        }
        String returnKey = " * $RETURN$\n";
        if (psiMethod.getReturnType() == null || psiMethod.getReturnType().getCanonicalText().equals("void")) {
            returnKey = "";
        }
        String throwsKey = " * $THROWS$\n";
        if (psiMethod.getThrowsList().getReferencedTypes().length == 0) {
            throwsKey = "";
        }
        return "/**\n"
            + " * $DOC$\n"
            + " *\n"
            + paramsKey
            + returnKey
            + throwsKey
            + " */";
    }

    /**
     * 获取方法内部的变量
     *
     * @param psiMethod psi方法
     * @return {@link java.util.Map<java.lang.String,java.lang.Object>}
     */
    private Map<String, Object> getMethodInnerVariable(PsiMethod psiMethod) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("author", config.getAuthor());
        map.put("methodName", psiMethod.getName());
        map.put("methodReturnType",
            psiMethod.getReturnType() == null ? "" : psiMethod.getReturnType().getCanonicalText());
        map.put("methodParamTypes",
            Arrays.stream(psiMethod.getParameterList().getParameters()).map(param -> param.getType().getCanonicalText())
                .toArray(String[]::new));
        map.put("methodParamNames",
            Arrays.stream(psiMethod.getParameterList().getParameters()).map(PsiParameter::getName)
                .toArray(String[]::new));
        map.put("branch", VcsUtil.getCurrentBranch(psiMethod.getProject()));
        map.put("projectName", psiMethod.getProject().getName());
        return map;
    }

    @Override
    protected EasyDocConfig getConfig() {
        return config;
    }
}
