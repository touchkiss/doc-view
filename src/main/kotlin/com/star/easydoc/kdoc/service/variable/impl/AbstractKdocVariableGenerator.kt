package com.star.easydoc.kdoc.service.variable.impl

import com.intellij.openapi.application.ApplicationManager
import com.star.easydoc.config.EasyDocConfig
import com.star.easydoc.config.EasyDocConfigComponent
import com.star.easydoc.javadoc.service.variable.VariableGenerator

/**
 * 变量生成器
 *
 * @author wangchao
 * @date 2022/10/01
 */
abstract class AbstractKdocVariableGenerator : VariableGenerator {
    override fun getConfig(): EasyDocConfig {
        return ApplicationManager.getApplication().getService(EasyDocConfigComponent::class.java).state!!
    }
}
