package com.liuzhihang.doc.view.utils;

import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 根据 {@link DocView} 生成 curl 命令字符串.
 *
 * @author liuzhihang
 */
public final class CurlUtils {

    private static final int CONTINUATION_LENGTH_THRESHOLD = 120;

    private CurlUtils() {
    }

    /**
     * 构建 curl 命令 (不含 Markdown 代码块围栏).
     */
    public static String build(DocView docView) {
        if (docView == null) {
            return "";
        }
        String method = docView.getMethod();
        String path = docView.getPath();
        if (StringUtils.isBlank(method) || StringUtils.isBlank(path)) {
            return "";
        }

        UrlBuildResult urlResult = buildUrl(docView, path);
        List<String> parts = new ArrayList<>();
        parts.add("curl -X " + method.toUpperCase(Locale.ROOT));
        parts.add("'" + urlResult.url + "'");

        appendHeaders(docView, parts);
        appendBody(docView, method, urlResult.formBodyParams, parts);

        return formatCommand(parts);
    }

    private static void appendHeaders(DocView docView, List<String> parts) {
        if (CollectionUtils.isEmpty(docView.getHeaderList())) {
            return;
        }
        for (Header header : docView.getHeaderList()) {
            if (header == null || StringUtils.isBlank(header.getName())) {
                continue;
            }
            String value = header.getValue() != null ? header.getValue() : "";
            parts.add("-H '" + header.getName() + ": " + escapeSingleQuoted(value) + "'");
        }
    }

    private static void appendBody(DocView docView, String method, List<Param> formBodyParams, List<String> parts) {
        if (!isBodyMethod(method)) {
            return;
        }

        ContentTypeEnum contentType = docView.getContentType();
        if (contentType == ContentTypeEnum.JSON && StringUtils.isNotBlank(docView.getReqBodyExample())) {
            if (!hasContentTypeHeader(parts)) {
                parts.add("-H 'Content-Type: application/json'");
            }
            parts.add("-d '" + escapeSingleQuoted(docView.getReqBodyExample().trim()) + "'");
            return;
        }

        if (contentType == ContentTypeEnum.FORM || StringUtils.isNotBlank(docView.getReqFormExample())) {
            if (!formBodyParams.isEmpty()) {
                for (Param param : formBodyParams) {
                    String name = param.getName();
                    if (StringUtils.isBlank(name)) {
                        continue;
                    }
                    String example = param.getExample() != null ? param.getExample() : "";
                    parts.add("-d '" + escapeSingleQuoted(name + "=" + example) + "'");
                }
                return;
            }
            if (StringUtils.isNotBlank(docView.getReqFormExample())) {
                for (String pair : docView.getReqFormExample().split("&")) {
                    if (StringUtils.isNotBlank(pair)) {
                        parts.add("-d '" + escapeSingleQuoted(pair) + "'");
                    }
                }
            }
        }
    }

    private static boolean hasContentTypeHeader(List<String> parts) {
        for (String part : parts) {
            if (part.startsWith("-H 'Content-Type:") || part.startsWith("-H \"Content-Type:")) {
                return true;
            }
        }
        return false;
    }

    private static UrlBuildResult buildUrl(DocView docView, String path) {
        List<Param> params = docView.getReqParamList() != null ? docView.getReqParamList() : List.of();
        List<Param> nonPathParams = new ArrayList<>();

        for (Param param : params) {
            if (param == null || StringUtils.isBlank(param.getName())) {
                continue;
            }
            String name = param.getName();
            String example = param.getExample() != null ? param.getExample() : "";
            String placeholder = "{" + name + "}";
            if (path.contains(placeholder)) {
                path = path.replace(placeholder, example);
            } else {
                nonPathParams.add(param);
            }
        }

        boolean queryOnUrl = !isBodyMethod(docView.getMethod()) || docView.getContentType() == ContentTypeEnum.JSON;

        StringBuilder url = new StringBuilder("{{host}}").append(path);
        List<Param> formBodyParams = new ArrayList<>();

        if (queryOnUrl && !nonPathParams.isEmpty()) {
            url.append("?");
            for (int i = 0; i < nonPathParams.size(); i++) {
                Param param = nonPathParams.get(i);
                if (i > 0) {
                    url.append("&");
                }
                String example = param.getExample() != null ? param.getExample() : "";
                url.append(param.getName()).append("=").append(example);
            }
        } else {
            formBodyParams.addAll(nonPathParams);
        }

        return new UrlBuildResult(url.toString(), formBodyParams);
    }

    private static boolean isBodyMethod(String method) {
        if (StringUtils.isBlank(method)) {
            return false;
        }
        String upper = method.toUpperCase(Locale.ROOT);
        return "POST".equals(upper) || "PUT".equals(upper) || "PATCH".equals(upper);
    }

    private static String formatCommand(List<String> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        int totalLength = parts.stream().mapToInt(String::length).sum();
        if (parts.size() <= 3 && totalLength <= CONTINUATION_LENGTH_THRESHOLD) {
            return String.join(" ", parts);
        }
        StringBuilder builder = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            builder.append(" \\\n  ").append(parts.get(i));
        }
        return builder.toString();
    }

    static String escapeSingleQuoted(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "'\\''");
    }

    private static final class UrlBuildResult {
        private final String url;
        private final List<Param> formBodyParams;

        private UrlBuildResult(String url, List<Param> formBodyParams) {
            this.url = url;
            this.formBodyParams = formBodyParams;
        }
    }
}
