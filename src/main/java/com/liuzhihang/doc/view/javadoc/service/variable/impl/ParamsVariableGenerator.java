package com.liuzhihang.doc.view.javadoc.service.variable.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.service.translator.TranslatorService;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:wangchao.star@gmail.com">wangchao</a>
 * @version 1.0.0
 * @since 2019-12-07 23:18:00
 */
public class ParamsVariableGenerator extends AbstractVariableGenerator {
    private final TranslatorService translatorService = ApplicationManager.getApplication().getService(TranslatorService.class);

    @Override
    public String generate(PsiElement element) {
        return prepare(element).get();
    }

    @Override
    public Supplier<String> prepare(PsiElement element) {
        if (!(element instanceof PsiMethod)) {
            return () -> "";
        }

        List<String> paramNameList = Arrays.stream(((PsiMethod)element).getParameterList().getParameters())
            .map(PsiParameter::getName).collect(Collectors.toList());
        if (paramNameList.isEmpty()) {
            return () -> "";
        }

        List<Supplier<String>> parameters = new ArrayList<>();
        PsiDocComment docComment = ((PsiMethod)element).getDocComment();
        // {"paramName":PsiDocTag}
        Map<String, PsiDocTag> psiDocTagMap = new HashMap<>();
        if (docComment != null) {
            PsiDocTag[] paramsDocArray = docComment.findTagsByName("param");
            for (PsiDocTag psiDocTag : paramsDocArray) {
                if (psiDocTag.getDataElements().length < 1) {
                    continue;
                }
                psiDocTagMap.put(psiDocTag.getDataElements()[0].getText(), psiDocTag);
            }
        }

        for (String paramName : paramNameList) {
            PsiDocTag paramTag = psiDocTagMap.get(paramName);
            String desc = paramTag == null || paramTag.getDataElements().length < 2
                ? "" : paramTag.getDataElements()[1].getText();
            Supplier<String> description = StringUtils.isEmpty(desc)
                || EasyDocConfig.COVER_MODE_FORCE.equals(getConfig().getCoverMode())
                ? translatorService.prepareTranslation(paramName, element)
                : () -> desc;
            parameters.add(() -> "@param " + paramName + " " + description.get());
        }
        return () -> parameters.stream().map(Supplier::get).collect(Collectors.joining("\n* "));
    }
}
