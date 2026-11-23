package com.liuzhihang.doc.view.javadoc.service.generator;

import java.util.function.Supplier;

import com.intellij.psi.PsiElement;

public interface JavaDocGenerator extends DocGenerator {

    @Override
    default String generate(PsiElement element) {
        return prepare(element).get();
    }

    /** 在读锁内获取 PSI 数据，返回可在读锁外执行的注释生成任务。 */
    Supplier<String> prepare(PsiElement element);
}
