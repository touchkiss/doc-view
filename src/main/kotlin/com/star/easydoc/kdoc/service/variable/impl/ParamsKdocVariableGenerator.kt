package com.star.easydoc.kdoc.service.variable.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.star.easydoc.config.EasyDocConfig
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters
import java.util.function.Supplier

/**
 * @author [wangchao](mailto:wangchao.star@gmail.com)
 * @version 1.0.0
 * @since 2019-12-07 23:18:00
 */
class ParamsKdocVariableGenerator : AbstractKdocVariableGenerator() {
    private val translatorService = ApplicationManager.getApplication().getService(com.star.easydoc.service.translator.TranslatorService::class.java)
    override fun generate(element: PsiElement): String = prepare(element).get()

    override fun prepare(element: PsiElement): Supplier<String> {
        if (element !is KtNamedDeclaration) {
            return Supplier { "" }
        }
        val linkedParams = config.kdocParamType == EasyDocConfig.LINK_PARAM_TYPE
        val params = element.getValueParameters().mapNotNull { parameter ->
            val name = parameter.name ?: return@mapNotNull null
            val label = if (linkedParams) "[$name]" else name
            val translation = translatorService.prepareTranslation(name, element)
            Supplier { "@param $label ${translation.get()}" }
        }
        return Supplier { params.joinToString("\n* ") { it.get() } }
    }
}
