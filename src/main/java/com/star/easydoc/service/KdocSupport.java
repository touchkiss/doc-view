package com.star.easydoc.service;

import com.intellij.psi.PsiElement;

public interface KdocSupport {
    boolean supports(PsiElement context);

    Runnable prepare(PsiElement context, String mode);
}
