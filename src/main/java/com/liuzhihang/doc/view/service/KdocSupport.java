package com.liuzhihang.doc.view.service;

import com.intellij.psi.PsiElement;

public interface KdocSupport {
    boolean supports(PsiElement context);

    Runnable prepare(PsiElement context, String mode);
}
