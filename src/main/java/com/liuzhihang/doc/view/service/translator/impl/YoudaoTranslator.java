package com.liuzhihang.doc.view.service.translator.impl;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;

/**
 * 有道翻译
 *
 * @author wangchao
 * @date 2019/09/01
 */
public class YoudaoTranslator extends AbstractTranslator {
    private static final Logger LOGGER = Logger.getInstance(YoudaoTranslator.class);

    @Override
    public String translateEn2Ch(String text, PsiElement psiElement) {
        LOGGER.error("youdao free translate has been disabled by official, please apply for a private account");
        return "";
    }

    @Override
    public String translateCh2En(String text, PsiElement psiElement) {
        LOGGER.error("youdao free translate has been disabled by official, please apply for a private account");
        return "";
    }

}
