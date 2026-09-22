package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolver;
import com.liuzhihang.doc.view.service.impl.YApiServiceImpl;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Performs the YApi upload workflow without coupling it to IDE notifications. */
public final class YApiUploadOrchestrator {

    private final YApiFacadeService facadeService;
    private final Function<Project, YApiSettings> settingsProvider;
    private final YapiSaveFactory saveFactory;

    public YApiUploadOrchestrator(YApiFacadeService facadeService) {
        this(facadeService, YApiSettings::getInstance);
    }

    YApiUploadOrchestrator(YApiFacadeService facadeService,
                           Function<Project, YApiSettings> settingsProvider) {
        this(facadeService, settingsProvider, YApiServiceImpl::toYapiSave);
    }

    YApiUploadOrchestrator(YApiFacadeService facadeService,
                           Function<Project, YApiSettings> settingsProvider,
                           YapiSaveFactory saveFactory) {
        this.facadeService = facadeService;
        this.settingsProvider = settingsProvider;
        this.saveFactory = saveFactory;
    }

    public List<UploadItemResult> upload(Project project, List<DocView> docViews) {
        YApiSettings settings = requiredSettings(project);
        List<UploadItemResult> results = new ArrayList<>();
        for (DocView docView : docViews) {
            results.add(uploadOne(settings, docView));
        }
        return results;
    }

    private UploadItemResult uploadOne(YApiSettings settings, DocView docView) {
        String reference = reference(docView);
        try {
            YApiCat category = getOrAddCategory(settings, docView.getDocTitle());
            YapiSave save = saveFactory.create(settings, category, docView);
            Optional<Long> existingId = facadeService.findInterfaceId(
                    save.getYapiUrl(), save.getProjectId(), save.getToken(),
                    save.getCatId(), save.getMethod(), save.getPath());
            if (existingId.isPresent()) {
                save.setId(String.valueOf(existingId.get()));
            }
            facadeService.save(save);
            String status = existingId.isPresent() ? "updated" : "created";
            return UploadItemResult.success(reference, status, YApiInterfaceUrlResolver.resolve(facadeService, save));
        } catch (Exception exception) {
            return UploadItemResult.failure(reference, McpException.Code.YAPI_REQUEST_FAILED,
                    "YApi 请求失败");
        }
    }

    private YApiSettings requiredSettings(Project project) {
        YApiSettings settings = settingsProvider.apply(project);
        if (settings == null || StringUtils.isBlank(settings.getUrl())
                || settings.getProjectId() == null || StringUtils.isBlank(settings.getToken())) {
            throw new McpException(McpException.Code.YAPI_NOT_CONFIGURED, "YApi 尚未配置");
        }
        return settings;
    }

    private YApiCat getOrAddCategory(YApiSettings settings, String name) throws Exception {
        Optional<YApiCat> existing = facadeService.getCatMenu(
                        settings.getUrl(), settings.getProjectId(), settings.getToken())
                .stream()
                .filter(category -> category.getName().equals(name))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        YApiCat category = new YApiCat();
        category.setYapiUrl(settings.getUrl());
        category.setProjectId(settings.getProjectId());
        category.setName(name);
        category.setToken(settings.getToken());
        return facadeService.addCat(category);
    }

    private static String reference(DocView docView) {
        if (docView == null) {
            return "unknown";
        }
        if ("Dubbo".equals(docView.getMethod())) {
            return docView.getPsiMethod() == null
                    ? "POST /Dubbo/unknown"
                    : "POST /Dubbo/" + docView.getPsiMethod().getName();
        }
        return docView.getMethod() + " " + docView.getPath();
    }

    @FunctionalInterface
    interface YapiSaveFactory {
        YapiSave create(YApiSettings settings, YApiCat category, DocView docView);
    }

    public static final class UploadItemResult {
        private final String reference;
        private final String status;
        private final String yapiUrl;
        private final String errorCode;
        private final String message;

        private UploadItemResult(String reference, String status, String yapiUrl,
                                 String errorCode, String message) {
            this.reference = reference;
            this.status = status;
            this.yapiUrl = yapiUrl;
            this.errorCode = errorCode;
            this.message = message;
        }

        private static UploadItemResult success(String reference, String status, String yapiUrl) {
            return new UploadItemResult(reference, status, yapiUrl, null, null);
        }

        private static UploadItemResult failure(String reference, McpException.Code errorCode, String message) {
            return new UploadItemResult(reference, "failed", null, errorCode.name(), message);
        }

        public String getReference() {
            return reference;
        }

        public String getStatus() {
            return status;
        }

        public String getYapiUrl() {
            return yapiUrl;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }
    }
}
