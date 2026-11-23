package com.liuzhihang.doc.view.kdoc.service.variable.impl

import com.intellij.psi.PsiElement
import com.liuzhihang.doc.view.config.EasyDocConfig

/**
 * @author [wangchao](mailto:wangchao.star@gmail.com)
 * @version 1.0.0
 * @since 2019-12-07 23:16:00
 */
class AuthorKdocVariableGenerator : AbstractKdocVariableGenerator() {
    override fun generate(element: PsiElement): String {
        return config.kdocAuthor
    }

}
