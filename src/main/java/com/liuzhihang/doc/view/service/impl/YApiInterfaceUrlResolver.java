package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.Optional;

public final class YApiInterfaceUrlResolver {

    private YApiInterfaceUrlResolver() { }

    /** Compatibility entry point; callers presenting upload success should inspect the outcome. */
    public static String resolve(YApiFacadeService facadeService, YapiSave save) {
        return resolveWithStatus(facadeService, save).url();
    }

    /** Distinguishes an actual interface detail link from an explicitly reported category fallback. */
    public static LinkResolution resolveWithStatus(YApiFacadeService facadeService, YapiSave save) {
        try {
            String url = resolveStrict(facadeService, save);
            if (url.equals(categoryUrl(save))) {
                return new LinkResolution(url, false, "接口已保存，但未找到接口详情，当前链接打开分类。");
            }
            return new LinkResolution(url, true, null);
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception failure) {
            // Neither transport exceptions nor remote response text belong in a notification.
            return new LinkResolution(categoryUrl(save), false, "接口已保存，但接口详情查询失败，当前链接打开分类。");
        }
    }

    public static String resolveStrict(YApiFacadeService facadeService, YapiSave save) throws Exception {
        Long existingId = positiveId(save.getId());
        if (existingId != null) {
            return baseUrl(save) + existingId;
        }
        Optional<Long> interfaceId = facadeService.findInterfaceId(
                save.getYapiUrl(), save.getProjectId(), save.getToken(),
                save.getCatId(), save.getMethod(), save.getPath());
        if (interfaceId.isPresent() && interfaceId.get() > 0) {
            save.setId(String.valueOf(interfaceId.get()));
            return baseUrl(save) + interfaceId.get();
        }
        return categoryUrl(save);
    }

    private static Long positiveId(String value) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String categoryUrl(YapiSave save) {
        return baseUrl(save) + "cat_" + save.getCatId();
    }

    private static String baseUrl(YapiSave save) {
        return save.getYapiUrl().replaceAll("/+$", "") + "/project/" + save.getProjectId() + "/interface/api/";
    }

    public record LinkResolution(String url, boolean interfaceDetail, String warning) { }
}
