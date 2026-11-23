package com.liuzhihang.doc.view.javadoc.service.generator.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.liuzhihang.doc.view.common.util.VcsUtil;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.javadoc.service.variable.JavadocVariableGeneratorService;
import com.liuzhihang.doc.view.service.translator.TranslatorService;

/**
 * 类文档生成器
 *
 * @author wangchao
 * @date 2019/11/12
 */
public class ClassDocGenerator extends AbstractDocGenerator {

    private EasyDocConfig config = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
    private JavadocVariableGeneratorService javadocVariableGeneratorService = ApplicationManager.getApplication().getService(
        JavadocVariableGeneratorService.class);
    private TranslatorService translatorService = ApplicationManager.getApplication().getService(TranslatorService.class);

    /** 默认模板 */
    private static final String DEFAULT_TEMPLATE = "/**\n"
        + "* $DOC$\n"
        + "*\n"
        + "* @author $AUTHOR$\n"
        + "* @date $DATE$\n"
        + "*/";

    @Override
    public Supplier<String> prepare(PsiElement psiElement) {
        if (!(psiElement instanceof PsiClass)) {
            return () -> "";
        }
        PsiClass psiClass = (PsiClass)psiElement;
        PsiDocComment docComment = psiClass.getDocComment();
        if (EasyDocConfig.COVER_MODE_IGNORE.equals(config.getCoverMode()) && docComment != null) {
            return () -> null;
        }

        String template = DEFAULT_TEMPLATE;
        if (config.getClassTemplateConfig() != null
            && Boolean.FALSE.equals(config.getClassTemplateConfig().getIsDefault())) {
            template = config.getClassTemplateConfig().getTemplate();
        }
        Supplier<String> javadoc = javadocVariableGeneratorService.prepare(psiClass, template,
            config.getClassTemplateConfig().getCustomMap(), getClassInnerVariable(psiClass));
        if (psiClass.isRecord()) {
            javadoc = prepareRecordParams(psiClass, javadoc);
        }
        return javadoc;
    }

    /**
     * 为Record类注入@param标签
     */
    private Supplier<String> prepareRecordParams(PsiClass psiClass, Supplier<String> javadoc) {
        List<Supplier<String>> params = Arrays.stream(psiClass.getRecordComponents())
            .<Supplier<String>>map(component -> {
                String name = component.getName();
                Supplier<String> description = translatorService.prepareTranslation(name, psiClass);
                return () -> "\n * @param " + name + " " + description.get();
            })
            .toList();
        if (params.isEmpty()) {
            return javadoc;
        }
        return () -> {
            String text = javadoc.get();
            StringBuilder tags = new StringBuilder();
            params.forEach(param -> tags.append(param.get()));
            int end = text.lastIndexOf("*/");
            return end < 0 ? text : text.substring(0, end) + tags + "\n " + text.substring(end);
        };
    }

    /**
     * 获取类内部变量
     *
     * @param psiClass psi类
     * @return {@link java.util.Map<java.lang.String,java.lang.Object>}
     */
    private Map<String, Object> getClassInnerVariable(PsiClass psiClass) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("author", config.getAuthor());
        map.put("className", psiClass.getQualifiedName());
        map.put("simpleClassName", psiClass.getName());
        map.put("branch", VcsUtil.getCurrentBranch(psiClass.getProject()));
        map.put("projectName", psiClass.getProject().getName());
        return map;
    }

    @Override
    protected EasyDocConfig getConfig() {
        return config;
    }
}
