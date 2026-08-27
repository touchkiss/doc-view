package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
final class YApiInterfaceUrlResolver {

    private YApiInterfaceUrlResolver() {
    }

    static String resolve(YApiFacadeService facadeService, YapiSave save) {
        String baseUrl = save.getYapiUrl() + "/project/" + save.getProjectId() + "/interface/api/";
        String categoryUrl = baseUrl + "cat_" + save.getCatId();
        try {
            Optional<Long> interfaceId = facadeService.findInterfaceId(
                    save.getYapiUrl(), save.getProjectId(), save.getToken(),
                    save.getCatId(), save.getMethod(), save.getPath());
            return interfaceId.map(id -> baseUrl + id).orElse(categoryUrl);
        } catch (Exception e) {
            log.warn("查询已上传的 YApi 接口 ID 失败，回退到分类地址: method={}, path={}",
                    save.getMethod(), save.getPath(), e);
            return categoryUrl;
        }
    }
}
