package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.UrlRewriteRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * REST 接口 URL 重写工具.
 * <p>
 * 依据用户在设置中配置的多条正则规则, 将 controller 中的内部路径重写为对外(网关)路径.
 * 规则按列表顺序依次应用, 后一条作用在前一条的结果之上.
 *
 * @author liuzhihang
 * @date 2026/7/7
 */
@Slf4j
public class UrlRewriteUtils {

    private UrlRewriteUtils() {
    }

    /**
     * 读取项目设置中的重写规则并应用到路径上.
     *
     * @param project 当前项目
     * @param path    原始路径
     * @return 重写后的路径
     */
    public static String rewrite(@NotNull Project project, String path) {
        return rewrite(Settings.getInstance(project).getUrlRewriteRules(), path);
    }

    /**
     * 纯函数核心: 将规则依次应用到路径上, 便于单独测试.
     * <p>
     * 单条规则的正则非法时跳过该条(记录警告), 不影响其余规则和文档生成.
     *
     * @param rules 重写规则列表(可为 null)
     * @param path  原始路径
     * @return 重写后的路径
     */
    public static String rewrite(List<UrlRewriteRule> rules, String path) {
        if (rules == null || rules.isEmpty() || StringUtils.isBlank(path)) {
            return path;
        }

        String result = path;
        for (UrlRewriteRule rule : rules) {
            if (rule == null || !rule.enabled || StringUtils.isBlank(rule.regex)) {
                continue;
            }
            try {
                result = result.replaceAll(rule.regex, rule.replacement == null ? "" : rule.replacement);
            } catch (RuntimeException e) {
                // 正则非法(PatternSyntaxException)等: 跳过该规则, 保证文档生成不中断
                log.warn("跳过非法的 URL 重写规则, regex={}, replacement={}", rule.regex, rule.replacement, e);
            }
        }
        return result;
    }
}
