package com.liuzhihang.doc.view.service;

import com.intellij.psi.PsiElement;

public interface KdocSupport {
    /**
     * 判断上下文是否支持 KDoc 生成。
     * @param context 当前元素
     * @return 支持时返回 true
     */
    boolean supports(PsiElement context);

    /**
     * 准备 KDoc 生成任务。
     * @param context 当前元素
     * @param mode 生成模式
     * @return 待执行的生成任务
     */
    Runnable prepare(PsiElement context, String mode);
}
