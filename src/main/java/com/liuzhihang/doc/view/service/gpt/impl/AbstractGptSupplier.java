package com.liuzhihang.doc.view.service.gpt.impl;

import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.service.gpt.GptSupplier;

/**
 * @author wangchao
 * @date 2024/03/05
 */
public abstract class AbstractGptSupplier implements GptSupplier {

    /** 配置 */
    private EasyDocConfig config;

    @Override
    public GptSupplier init(EasyDocConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public EasyDocConfig getConfig() {
        return this.config;
    }
}
