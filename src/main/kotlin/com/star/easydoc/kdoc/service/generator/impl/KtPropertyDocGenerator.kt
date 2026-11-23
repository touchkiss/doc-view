package com.star.easydoc.kdoc.service.generator.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.star.easydoc.common.util.VcsUtil
import com.star.easydoc.config.EasyDocConfig
import com.star.easydoc.config.EasyDocConfigComponent
import com.star.easydoc.javadoc.service.generator.JavaDocGenerator
import java.util.function.Supplier
import com.star.easydoc.kdoc.service.variable.KdocVariableGeneratorService
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 属性文档生成器
 *
 * @author wangchao
 * @date 2019/11/12
 */
class KtPropertyDocGenerator : JavaDocGenerator {
    private val config: EasyDocConfig = ApplicationManager.getApplication().getService(EasyDocConfigComponent::class.java).state!!
    private val kdocVariableGeneratorService = ApplicationManager.getApplication().getService(KdocVariableGeneratorService::class.java)
    override fun prepare(psiElement: PsiElement): Supplier<String> {
        if (psiElement !is KtProperty) {
            return Supplier { StringUtils.EMPTY }
        }
        // 检查字段是否在排除列表中
        if (isExcludedField(psiElement.name)) {
            return Supplier { StringUtils.EMPTY }
        }
        return if (config.kdocFieldTemplateConfig != null && config.kdocFieldTemplateConfig.isDefault
        ) {
            defaultGenerate(psiElement)
        } else {
            customGenerate(psiElement)
        }
    }

    /**
     * 默认的生成
     *
     * @param psi 当前属性
     * @return [java.lang.String]
     */
    private fun defaultGenerate(psi: KtProperty): Supplier<String> {
        return if (BooleanUtils.isTrue(config.kdocSimpleFieldDoc)) {
            kdocVariableGeneratorService.prepare(
                psi, "/** \$DOC\$ */",
                config.kdocFieldTemplateConfig.customMap, getFieldInnerVariable(psi)
            )
        } else {
            kdocVariableGeneratorService.prepare(
                psi, "/**\n * \$DOC\$\n */",
                config.kdocFieldTemplateConfig.customMap, getFieldInnerVariable(psi)
            )
        }
    }

    /**
     * 自定义生成
     *
     * @param psi 当前属性
     * @return [String]
     */
    private fun customGenerate(psi: KtProperty): Supplier<String> {
        return kdocVariableGeneratorService.prepare(
            psi, config.kdocFieldTemplateConfig.template,
            config.kdocFieldTemplateConfig.customMap, getFieldInnerVariable(psi)
        )
    }

    /**
     * 获取字段内部的变量
     *
     * @param psiField psi属性
     * @return [,][<]
     */
    private fun getFieldInnerVariable(psiField: KtProperty): Map<String?, Any?> {
        val map: MutableMap<String?, Any?> = mutableMapOf()
        map["author"] = config.kdocAuthor
        map["fieldName"] = psiField.name
        map["fieldType"] = StringUtils.strip(psiField.typeReference?.typeElement?.text, "?")
        map["branch"] = VcsUtil.getCurrentBranch(psiField.project)
        map["projectName"] = psiField.project.name
        return map
    }

    /**
     * 检查字段是否在排除列表中
     *
     * @param fieldName 字段名
     * @return 是否排除
     */
    private fun isExcludedField(fieldName: String?): Boolean {
        if (fieldName == null) {
            return false
        }
        val excludeFieldNames = config.excludeFieldNames
        if (StringUtils.isBlank(excludeFieldNames)) {
            return false
        }
        val excludedFields = excludeFieldNames.split(",")
            .map { it.trim() }
            .filter { StringUtils.isNotBlank(it) }
            .toSet()
        return excludedFields.contains(fieldName)
    }
}
