package com.star.easydoc.service;

import com.intellij.psi.PsiElement;

import java.util.List;

public interface ProtoCommentSupport {
    boolean supports(PsiElement context);

    void generate(PsiElement context, List<Integer> offsets, boolean allMembers);
}
