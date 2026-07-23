package com.liuzhihang.doc.view.config;

/**
 * URL 重写规则.
 * <p>
 * 使用公共字段 + 无参构造, 便于 IntelliJ {@code XmlSerializerUtil} 直接序列化到 DocViewSettings.xml.
 *
 * @author liuzhihang
 * @date 2026/7/7
 */
public class UrlRewriteRule {

    /**
     * 是否启用该规则
     */
    public boolean enabled = true;

    /**
     * 匹配的正则表达式, 例如 ^/order/i/admin/seller/(.*)$
     */
    public String regex = "";

    /**
     * 替换内容, 支持 $1 等分组引用, 例如 /seller/c/order/$1
     */
    public String replacement = "";

    public UrlRewriteRule() {
    }

    public UrlRewriteRule(boolean enabled, String regex, String replacement) {
        this.enabled = enabled;
        this.regex = regex;
        this.replacement = replacement;
    }

    /**
     * 深拷贝, 用于设置页面在 apply 前隔离编辑.
     */
    public UrlRewriteRule copy() {
        return new UrlRewriteRule(enabled, regex, replacement);
    }
}
