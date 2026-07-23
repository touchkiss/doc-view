package com.liuzhihang.doc.view;

import com.liuzhihang.doc.view.config.UrlRewriteRule;
import com.liuzhihang.doc.view.utils.UrlRewriteUtils;

import java.util.List;

/**
 * URL 重写规则自检.
 * <p>
 * 运行 main (建议带 -ea 开启断言) 验证 {@link UrlRewriteUtils} 纯函数核心逻辑.
 *
 * @author liuzhihang
 */
public class UrlRewriteTest {

    public static void main(String[] args) {

        // 1. 单条规则重写: 内部路径 -> 网关路径
        UrlRewriteRule rule = new UrlRewriteRule(true, "^/order/i/admin/seller/(.*)$", "/seller/c/order/$1");
        String out = UrlRewriteUtils.rewrite(List.of(rule), "/order/i/admin/seller/list");
        check("单条规则", "/seller/c/order/list", out);

        // 2. 不匹配的路径保持不变
        out = UrlRewriteUtils.rewrite(List.of(rule), "/user/profile");
        check("不匹配保持不变", "/user/profile", out);

        // 3. 多条规则按顺序依次应用 (rule2 作用在 rule1 结果上)
        UrlRewriteRule r1 = new UrlRewriteRule(true, "^/a/(.*)$", "/b/$1");
        UrlRewriteRule r2 = new UrlRewriteRule(true, "^/b/(.*)$", "/c/$1");
        out = UrlRewriteUtils.rewrite(List.of(r1, r2), "/a/x");
        check("顺序应用", "/c/x", out);

        // 4. 禁用的规则被跳过
        UrlRewriteRule disabled = new UrlRewriteRule(false, "^/order/i/admin/seller/(.*)$", "/seller/c/order/$1");
        out = UrlRewriteUtils.rewrite(List.of(disabled), "/order/i/admin/seller/list");
        check("禁用跳过", "/order/i/admin/seller/list", out);

        // 5. 非法正则被跳过, 不抛异常, 后续合法规则仍生效
        UrlRewriteRule bad = new UrlRewriteRule(true, "[unclosed", "/x");
        UrlRewriteRule good = new UrlRewriteRule(true, "^/order/(.*)$", "/gw/$1");
        out = UrlRewriteUtils.rewrite(List.of(bad, good), "/order/list");
        check("非法正则跳过", "/gw/list", out);

        // 6. 空规则列表 / null 路径边界
        check("空规则", "/order/list", UrlRewriteUtils.rewrite(List.of(), "/order/list"));
        check("null 路径", null, UrlRewriteUtils.rewrite(List.of(rule), null));

        System.out.println("所有 URL 重写自检通过 ✓");
    }

    private static void check(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + " 失败: 期望[" + expected + "] 实际[" + actual + "]");
        }
        System.out.println("[PASS] " + name + " -> " + actual);
    }
}
