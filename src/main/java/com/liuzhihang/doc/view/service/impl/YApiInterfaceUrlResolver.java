package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

@Slf4j
public final class YApiInterfaceUrlResolver {

    private YApiInterfaceUrlResolver() {
    }

    public static String resolve(YApiFacadeService facadeService, YapiSave save) {
        try {
            return resolveStrict(facadeService, save);
        } catch (Exception e) {
            String categoryUrl = baseUrl(save) + "cat_" + save.getCatId();
            log.warn("查询已上传的 YApi 接口 ID 失败，回退到分类地址: method={}, path={}",
                    save.getMethod(), save.getPath(), e);
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
}
