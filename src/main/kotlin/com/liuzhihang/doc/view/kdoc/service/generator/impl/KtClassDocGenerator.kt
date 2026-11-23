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
import org.jetbrains.kotlin.psi.KtClass

/**
 * @constructor
 */
class KtClassDocGenerator : JavaDocGenerator {
    private val config: EasyDocConfig = ApplicationManager.getApplication().getService(EasyDocConfigComponent::class.java).state!!
    private val kdocVariableGeneratorService = ApplicationManager.getApplication().getService(KdocVariableGeneratorService::class.java)

    override fun prepare(psiElement: PsiElement): Supplier<String> {
        if (psiElement !is KtClass) {
            return Supplier { StringUtils.EMPTY }
        }
        return if (config.kdocClassTemplateConfig != null && config.kdocClassTemplateConfig.isDefault
        ) {
            defaultGenerate(psiElement)
        } else {
            customGenerate(psiElement)
        }
    }

    /**
     * 默认的生成
     *
     * @param psi 当前类
     * @return [java.lang.String]
     */
    private fun defaultGenerate(psi: KtClass): Supplier<String> {
        return kdocVariableGeneratorService.prepare(
            psi, "/**\n" +
                    " * \$DOC\$\n" +
                    " *\n" +
                    " * @author \$AUTHOR\$\n" +
                    " * @date \$DATE\$\n" +
                    " * @constructor \$CONSTRUCTOR\$\n" +
                    " * \$PARAMS\$\n" +
                    " */".trimIndent(),
            config.kdocClassTemplateConfig.customMap, getClassInnerVariable(psi)
        )
    }

    /**
     * 自定义生成
     *
     * @param psi 当前类
     * @return [java.lang.String]
     */
    private fun customGenerate(psi: KtClass): Supplier<String> {
        return kdocVariableGeneratorService.prepare(
            psi, config.kdocClassTemplateConfig.template,
            config.kdocClassTemplateConfig.customMap, getClassInnerVariable(psi)
        )
    }

    /**
     * 获取类内部变量
     *
     * @param psiClass psi类
     * @return [,][<]
     */
    private fun getClassInnerVariable(psiClass: KtClass): Map<String?, Any?> {
        val map: MutableMap<String?, Any?> = mutableMapOf()
        map["author"] = config.kdocAuthor
        map["className"] = psiClass.fqName
        map["simpleClassName"] = psiClass.name
        map["branch"] = VcsUtil.getCurrentBranch(psiClass.project)
        map["projectName"] = psiClass.project.name
        return map
    }

}
