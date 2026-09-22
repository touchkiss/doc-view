package com.liuzhihang.doc.view.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.config.YApiSettingsConfigurable;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImpl;
import com.liuzhihang.doc.view.mcp.YApiUploadOrchestrator;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Uploads documents to YApi and owns only IDE-facing notifications and settings UI. */
@Slf4j
@Service
public final class YApiServiceImpl implements DocViewUploadService {

    private final YapiSaveFactory saveFactory = new YapiSaveFactory();

    @Override
    public boolean checkSettings(@NotNull Project project) {
        YApiSettings apiSettings = YApiSettings.getInstance(project);
        if (StringUtils.isBlank(apiSettings.getUrl())
                || apiSettings.getProjectId() == null
                || StringUtils.isBlank(apiSettings.getToken())) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.yapi.info.settings"));
            ShowSettingsUtil.getInstance().showSettingsDialog(project, YApiSettingsConfigurable.class);
            return false;
        }
        return true;
    }

    @Override
    public void doUpload(@NotNull Project project, @NotNull DocView docView) {
        try {
            YApiFacadeService facadeService = ApplicationManager.getApplication().getService(YApiFacadeServiceImpl.class);
            YApiUploadOrchestrator.UploadItemResult result = new YApiUploadOrchestrator(facadeService, saveFactory)
                    .upload(project, List.of(docView)).get(0);
            if ("failed".equals(result.getStatus())) {
                throw new IllegalStateException(result.getMessage());
            }
            DocViewNotification.uploadSuccess(project, "YApi", result.getYapiUrl());
        } catch (Exception e) {
            String summary = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.yapi.upload.error", summary));
            log.error("上传单个文档失败: {}, 原因: {}", docView, summary, e);
        }
    }
}
