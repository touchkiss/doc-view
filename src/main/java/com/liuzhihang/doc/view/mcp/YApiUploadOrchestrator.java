package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.YApiRemoteException;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolver;
import com.liuzhihang.doc.view.service.impl.YapiSaveFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Performs the YApi upload workflow without coupling it to IDE notifications. */
public final class YApiUploadOrchestrator {

    private final YApiFacadeService facadeService;
    private final Function<Project, YApiSettings> settingsProvider;
    private final SaveMapper saveMapper;

    public YApiUploadOrchestrator(YApiFacadeService facadeService) {
        this(facadeService, new YapiSaveFactory());
    }

    public YApiUploadOrchestrator(YApiFacadeService facadeService, YapiSaveFactory saveFactory) {
        this(facadeService, YApiSettings::getInstance, saveFactory::create);
    }

    YApiUploadOrchestrator(YApiFacadeService facadeService,
                           Function<Project, YApiSettings> settingsProvider) {
        this(facadeService, settingsProvider, new YapiSaveFactory()::create);
    }

    YApiUploadOrchestrator(YApiFacadeService facadeService,
                           Function<Project, YApiSettings> settingsProvider,
                           SaveMapper saveMapper) {
        this.facadeService = facadeService;
        this.settingsProvider = settingsProvider;
        this.saveMapper = saveMapper;
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
            return uploadPreparedDocument(settings, category, docView, reference);
        } catch (Exception exception) {
            return remoteFailure(reference, settings, exception);
        }
    }

    private UploadItemResult uploadPreparedDocument(YApiSettings settings, YApiCat category, DocView docView,
                                                     String reference) {
        final YapiSave save;
        try {
            save = saveMapper.create(settings, category, docView);
        } catch (Exception exception) {
            return UploadItemResult.failure(reference, McpException.Code.DOC_GENERATION_FAILED,
                    safeSummary(exception, settings.getToken()));
        }
        try {
            Optional<Long> existingId = facadeService.findInterfaceId(
                    save.getYapiUrl(), save.getProjectId(), save.getToken(),
                    save.getCatId(), save.getMethod(), save.getPath());
            if (existingId.isPresent()) {
                save.setId(String.valueOf(existingId.get()));
            }
            facadeService.save(save);
            String status = existingId.isPresent() ? "updated" : "created";
            return UploadItemResult.success(reference, status,
                    YApiInterfaceUrlResolver.resolveStrict(facadeService, save));
        } catch (Exception exception) {
            return remoteFailure(reference, settings, exception);
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

    private UploadItemResult remoteFailure(String reference, YApiSettings settings, Exception exception) {
        McpException.Code code = McpException.Code.YAPI_REQUEST_FAILED;
        if (exception instanceof McpException) {
            McpException.Code mcpCode = ((McpException) exception).getCode();
            if (mcpCode == McpException.Code.YAPI_RESPONSE_INVALID
                    || mcpCode == McpException.Code.YAPI_REQUEST_FAILED) {
                code = mcpCode;
            }
        } else if (exception instanceof YApiRemoteException
                && ((YApiRemoteException) exception).getKind() == YApiRemoteException.Kind.RESPONSE_INVALID) {
            code = McpException.Code.YAPI_RESPONSE_INVALID;
        }
        return UploadItemResult.failure(reference, code, safeSummary(exception, settings.getToken()));
    }

    private static String safeSummary(Exception exception, String token) {
        String summary = StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName());
        if (StringUtils.isNotBlank(token)) {
            summary = summary.replace(token, "***");
        }
        summary = summary.replaceAll("(?i)(token=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(\\\"token\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
        return summary.length() > 240 ? summary.substring(0, 240) : summary;
    }

    @FunctionalInterface
    interface SaveMapper {
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
