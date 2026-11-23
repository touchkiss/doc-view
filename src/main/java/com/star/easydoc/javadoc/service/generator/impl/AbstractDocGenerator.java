package com.star.easydoc.javadoc.service.generator.impl;

import com.star.easydoc.config.EasyDocConfig;
import com.star.easydoc.javadoc.service.generator.JavaDocGenerator;

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
