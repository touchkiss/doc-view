package com.liuzhihang.doc.view.javadoc.service.variable;

import java.util.function.Supplier;

import com.intellij.psi.PsiElement;
import com.liuzhihang.doc.view.config.EasyDocConfig;

/**
 * 变量生成器
 *
 * @author wangchao
 * @date 2019/12/07
 */
public interface VariableGenerator {
    /**
     * 生成
     *
     * @param element 元素
     * @return {@link String}
     */
    String generate(PsiElement element);

    default Supplier<String> prepare(PsiElement element) {
        String value = generate(element);
        return () -> value;
    }

    /**
     * 获取配置
     *
     * @return {@link EasyDocConfig}
     */
    EasyDocConfig getConfig();
}
