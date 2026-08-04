package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.Settings;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 将 {@link CurlUtils} 生成的 curl 中的 {{host}} 占位符替换为项目配置的域名。
 * <p>
 * Copy cURL 与生成文档是两条独立的配置:
 * <ul>
 *     <li>Copy cURL 复制到剪贴板时用 {@link Settings#getCurlCopyHost()}, 默认 http://localhost:8080</li>
 *     <li>生成文档（预览 / 导出 / 上传）中的 curl 示例用 {@link Settings#getCurlDocHost()},
 *         默认就是 {{host}} 本身, 即不做替换, 保证未配置的项目输出与之前完全一致</li>
 * </ul>
 * 替换发生在 curl 构建之后, {@link CurlUtils#build} 因此保持为 DocView 的纯函数,
 * 且 .http 导出（{@link CustomFileUtils}）可以继续保留字面量 {{host}}。
 * <p>
 * 配置值原样使用: 不做 URL 校验, 不增删 scheme, 允许填 {{order-web}} 这类网关标识。
 *
 * @author liuzhihang
 */
public final class CurlHostUtils {

    /**
     * curl 中的域名占位符
     */
    public static final String HOST_PLACEHOLDER = "{{host}}";

    private CurlHostUtils() {
    }

    /**
     * 用 Copy cURL 域名替换占位符
     *
     * @param project 当前工程
     * @param curl    curl 命令
     * @return 替换后的 curl 命令
     */
    @NotNull
    public static String applyCopyHost(@NotNull Project project, @Nullable String curl) {
        return replaceHost(curl, copyHost(project));
    }

    /**
     * 用文档 curl 域名替换占位符。配置值为 {{host}} 时不做任何替换。
     *
     * @param project 当前工程
     * @param curl    curl 命令
     * @return 替换后的 curl 命令
     */
    @NotNull
    public static String applyDocHost(@NotNull Project project, @Nullable String curl) {
        String host = docHost(project);
        if (HOST_PLACEHOLDER.equals(host)) {
            // 默认值即占位符本身, 替换是恒等操作, 直接跳过
            return StringUtils.defaultString(curl);
        }
        return replaceHost(curl, host);
    }

    /**
     * Copy cURL 域名, 未配置时取默认值
     *
     * @param project 当前工程
     * @return 域名
     */
    @NotNull
    public static String copyHost(@NotNull Project project) {
        return orDefault(Settings.getInstance(project).getCurlCopyHost(), Settings.DEFAULT_CURL_COPY_HOST);
    }

    /**
     * 文档 curl 域名, 未配置时取默认值
     *
     * @param project 当前工程
     * @return 域名
     */
    @NotNull
    public static String docHost(@NotNull Project project) {
        return orDefault(Settings.getInstance(project).getCurlDocHost(), Settings.DEFAULT_CURL_DOC_HOST);
    }

    /**
     * Copy gRPC cURL 域名, 未配置时取默认值。
     * 这里不做去尾斜杠处理: {@link GrpcCurlUtils} 拼接 URL 时已经处理。
     *
     * @param project 当前工程
     * @return 域名
     */
    @NotNull
    public static String grpcHost(@NotNull Project project) {
        return orDefault(Settings.getInstance(project).getGrpcCurlHost(), Settings.DEFAULT_GRPC_CURL_HOST);
    }

    /**
     * 空白配置回退到默认值, 避免生成 curl -X GET '/api/users' 这种没有域名的命令
     */
    @NotNull
    private static String orDefault(@Nullable String value, @NotNull String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
    }

    /**
     * 替换占位符, 并去掉域名末尾的 '/', 避免出现 http://order-web//api/users
     */
    @NotNull
    private static String replaceHost(@Nullable String curl, @NotNull String host) {
        if (StringUtils.isBlank(curl)) {
            return StringUtils.defaultString(curl);
        }
        return curl.replace(HOST_PLACEHOLDER, StringUtils.stripEnd(host, "/"));
    }
}
