package com.liuzhihang.doc.view.kdoc.service.variable.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.util.function.Supplier

/**
 * @author [wangchao](mailto:wangchao.star@gmail.com)
 * @version 1.0.0
 * @since 2019-12-07 23:16:00
 */
class DocKdocVariableGenerator : AbstractKdocVariableGenerator() {
    private val translatorService = ApplicationManager.getApplication().getService(com.liuzhihang.doc.view.service.translator.TranslatorService::class.java)
    override fun generate(element: PsiElement): String = prepare(element).get()

    override fun prepare(element: PsiElement): Supplier<String> {
        val name = (element as? KtNamedDeclaration)?.name ?: return Supplier { "" }
        val translation = translatorService.prepareTranslation(name, element)
        return Supplier { translation.get().trim() }
    }
}
