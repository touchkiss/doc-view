package com.liuzhihang.doc.view.javadoc.service.variable.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.javadoc.service.variable.VariableGenerator;

/**
 * 变量生成器
 *
 * @author wangchao
 * @date 2022/10/01
 */
public abstract class AbstractVariableGenerator implements VariableGenerator {

    @Override
    public EasyDocConfig getConfig() {
        return ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
    }
}
