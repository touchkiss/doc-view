package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public final class YApiInterfaceUrlResolver {

    private YApiInterfaceUrlResolver() {
    }

    public static String resolve(YApiFacadeService facadeService, YapiSave save) {
        try {
            return resolveStrict(facadeService, save);
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception e) {
            String categoryUrl = baseUrl(save) + "cat_" + save.getCatId();
            return categoryUrl;
        }
    }

    public static String resolveStrict(YApiFacadeService facadeService, YapiSave save) throws Exception {
        String baseUrl = baseUrl(save);
        String categoryUrl = baseUrl + "cat_" + save.getCatId();
        if (StringUtils.isNotBlank(save.getId())) {
            return baseUrl + save.getId();
        }
        Optional<Long> interfaceId = facadeService.findInterfaceId(
                save.getYapiUrl(), save.getProjectId(), save.getToken(),
                save.getCatId(), save.getMethod(), save.getPath());
        return interfaceId.map(id -> baseUrl + id).orElse(categoryUrl);
    }

    private static String baseUrl(YapiSave save) {
        return save.getYapiUrl() + "/project/" + save.getProjectId() + "/interface/api/";
    }

    private static String redact(String value, String token) {
        String redacted = StringUtils.defaultString(value);
        if (StringUtils.isNotBlank(token)) {
            redacted = redacted.replace(token, "***");
        }
        return redacted.replaceAll("(?i)(token=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(\\\"token\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
    }
}
