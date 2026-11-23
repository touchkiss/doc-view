package com.liuzhihang.doc.view.javadoc.service.variable.impl;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.service.translator.TranslatorService;
import org.apache.commons.lang3.StringUtils;

/**
 * doc 变量生成器
 *
 * @author <a href="mailto:wangchao.star@gmail.com">wangchao</a>
 * @date 2025/07/28
 */
public class DocVariableGenerator extends AbstractVariableGenerator {
    private final TranslatorService translatorService = ApplicationManager.getApplication().getService(TranslatorService.class);

    @Override
    public String generate(PsiElement element) {
        return prepare(element).get();
    }

    @Override
    public Supplier<String> prepare(PsiElement element) {
        if (!(element instanceof PsiNamedElement namedElement)) {
            return () -> "";
        }
        PsiDocComment docComment = element instanceof PsiJavaDocumentedElement documentedElement
            ? documentedElement.getDocComment() : null;

        // force模式
        if (docComment == null || EasyDocConfig.COVER_MODE_FORCE.equals(getConfig().getCoverMode())) {
            return translatorService.prepareTranslation(namedElement.getName(), element);
        }

        PsiElement[] descriptionElements = docComment.getDescriptionElements();
        List<String> descTextList = Arrays.stream(descriptionElements).map(PsiElement::getText)
            .map(StringUtils::strip)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
        String result = Joiner.on("\n* ").skipNulls().join(descTextList);
        return StringUtils.isNotBlank(result)
            ? () -> result
            : translatorService.prepareTranslation(namedElement.getName(), element);
    }
}
