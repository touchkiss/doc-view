package com.liuzhihang.doc.view.service;

import com.intellij.psi.PsiElement;

import java.util.List;

public interface ProtoCommentSupport {
    /**
     * 判断上下文是否支持 Proto 注释生成。
     * @param context 当前元素
     * @return 支持时返回 true
     */
    boolean supports(PsiElement context);

    /**
     * 为指定位置或全部成员生成注释。
     * @param context 当前元素
     * @param offsets 指定位置的偏移量
     * @param allMembers 是否处理全部成员
     */
    void generate(PsiElement context, List<Integer> offsets, boolean allMembers);
}
