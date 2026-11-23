package com.liuzhihang.doc.view.kdoc.service.generator.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.liuzhihang.doc.view.common.util.VcsUtil
import com.liuzhihang.doc.view.config.EasyDocConfig
import com.liuzhihang.doc.view.config.EasyDocConfigComponent
import com.liuzhihang.doc.view.javadoc.service.generator.JavaDocGenerator
import java.util.function.Supplier
import com.liuzhihang.doc.view.kdoc.service.variable.KdocVariableGeneratorService
import org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 方法文档生成器
 *
 * @author wangchao
 * @date 2019/11/12
 */
class KtNamedFunctionDocGenerator : JavaDocGenerator {
    private val config: EasyDocConfig = ApplicationManager.getApplication().getService(EasyDocConfigComponent::class.java).state!!
    private val kdocVariableGeneratorService = ApplicationManager.getApplication().getService(
        KdocVariableGeneratorService::class.java
    )

    override fun prepare(psiElement: PsiElement): Supplier<String> {
        if (psiElement !is KtNamedFunction) {
            return Supplier { StringUtils.EMPTY }
        }
        return if (config.kdocMethodTemplateConfig != null && config.kdocMethodTemplateConfig.isDefault
        ) {
            defaultGenerate(psiElement)
        } else {
            customGenerate(psiElement)
        }
    }

    /**
     * 默认的生成
     *
     * @param psi 当前方法
     * @return [java.lang.String]
     */
    private fun defaultGenerate(psi: KtNamedFunction): Supplier<String> {
        val template = """
            /**
             * ${'$'}DOC$
             *
             * ${'$'}PARAMS$
             * ${'$'}RETURN$
             */
          """.trimIndent()
        return kdocVariableGeneratorService.prepare(
            psi, template, config.kdocMethodTemplateConfig.customMap, getMethodInnerVariable(psi)
        )
    }

    /**
     * 自定义生成
     *
     * @param psi 当前方法
     * @return [java.lang.String]
     */
    private fun customGenerate(psi: KtNamedFunction): Supplier<String> {
        return kdocVariableGeneratorService.prepare(
            psi, config.kdocMethodTemplateConfig.template,
            config.kdocMethodTemplateConfig.customMap, getMethodInnerVariable(psi)
        )
    }

    /**
     * 获取方法内部的变量
     *
     * @param psiMethod psi方法
     * @return [,][<]
     */
    private fun getMethodInnerVariable(psiMethod: KtNamedFunction): Map<String?, Any?> {
        val map: MutableMap<String?, Any?> = mutableMapOf()
        map["author"] = config.kdocAuthor
        map["methodName"] = psiMethod.name
        map["methodReturnType"] = psiMethod.typeReference?.text
        map["methodParamTypes"] = psiMethod.valueParameters.map { StringUtils.strip(it.typeReference?.text, "?") }
            .toTypedArray()
        map["methodParamNames"] = psiMethod.valueParameters.map { it.name }.toTypedArray()
        map["branch"] = VcsUtil.getCurrentBranch(psiMethod.project)
        map["projectName"] = psiMethod.project.name
        return map
    }
}
