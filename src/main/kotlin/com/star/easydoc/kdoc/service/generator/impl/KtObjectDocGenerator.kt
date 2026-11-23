package com.star.easydoc.kdoc.service.generator.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.star.easydoc.common.util.VcsUtil
import com.star.easydoc.config.EasyDocConfig
import com.star.easydoc.config.EasyDocConfigComponent
import com.star.easydoc.javadoc.service.generator.JavaDocGenerator
import java.util.function.Supplier
import com.star.easydoc.kdoc.service.variable.KdocVariableGeneratorService
import org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.psi.KtObjectDeclaration

/**
 * Kotlin Object Doc generator
 * @author: kuolemax
 * @date: 2022-12-3
 */
class KtObjectDocGenerator : JavaDocGenerator {
    private val config: EasyDocConfig = ApplicationManager.getApplication().getService(EasyDocConfigComponent::class.java).state!!
    private val kdocVariableGeneratorService = ApplicationManager.getApplication().getService(KdocVariableGeneratorService::class.java)

    private val defaultTemplate = """
        /**
         * ${'$'}DOC${'$'}
         *
         * @author ${'$'}AUTHOR${'$'}
         * @date ${'$'}DATE${'$'}
         */
    """.trimIndent()

    override fun prepare(psiElement: PsiElement): Supplier<String> {
        if (psiElement !is KtObjectDeclaration) {
            return Supplier { StringUtils.EMPTY }
        }

        return kdocVariableGeneratorService.prepare(
            psiElement,
            if (config.kdocFieldTemplateConfig != null && config.kdocFieldTemplateConfig.isDefault) {
                defaultTemplate
            } else {
                config.kdocFieldTemplateConfig.template
            },
            config.kdocFieldTemplateConfig.customMap,
            getClassInnerVariable(psiElement)
        )
    }


    /**
     * 获取类内部变量
     *
     * @param psiObject kt object
     * @return
     */
    private fun getClassInnerVariable(psiObject: KtObjectDeclaration): Map<String?, Any?> {
        return mapOf(
            "author" to config.kdocAuthor,
            "className" to psiObject.fqName,
            "simpleClassName" to psiObject.name,
            "projectName" to psiObject.project.name,
            "branch" to VcsUtil.getCurrentBranch(psiObject.project)
        )
    }

}
