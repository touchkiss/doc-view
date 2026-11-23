package com.star.easydoc.javadoc.service.variable.impl;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.star.easydoc.service.translator.TranslatorService;

/**
 * 异常变量生成器
 *
 * @author <a href="mailto:wangchao.star@gmail.com">wangchao</a>
 * @date 2025/07/28
 */
public class ThrowsVariableGenerator extends AbstractVariableGenerator {
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
        List<Supplier<String>> exceptions = Arrays.stream(((PsiMethod)element).getThrowsList().getReferencedTypes())
            .map(type -> prepareException(type, element))
            .toList();
        return () -> exceptions.stream().map(Supplier::get).collect(Collectors.joining("\n"));
    }

    private Supplier<String> prepareException(PsiClassType type, PsiElement element) {
        String name = type.getName();
        Supplier<String> description = translatorService.prepareTranslationWithClass(name,
            type.getCanonicalText(), element.getProject(), element);
        return () -> "@throws " + name + " " + description.get();
    }
}
