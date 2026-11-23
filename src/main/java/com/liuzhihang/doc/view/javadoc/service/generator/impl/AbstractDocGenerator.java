package com.liuzhihang.doc.view.javadoc.service.generator.impl;

import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.javadoc.service.generator.JavaDocGenerator;

/**
 * 抽象文档生成器
 *
 * @author wangchao
 * @date 2024/03/11
 */
public abstract class AbstractDocGenerator implements JavaDocGenerator {

    /**
     * 获取配置
     *
     * @return {@code EasyDocConfig }
     */
    protected abstract EasyDocConfig getConfig();
}
