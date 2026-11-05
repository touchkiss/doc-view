package com.liuzhihang.doc.view.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
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
import com.liuzhihang.doc.view.service.IntegrationCredentialService;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** Shares the fork's upsert workflow with MCP; owns only IDE-facing notifications. */
@Service
public final class YApiServiceImpl implements DocViewUploadService {
    private final YapiSaveFactory saveFactory = new YapiSaveFactory();

    @Override
    public boolean checkSettings(@NotNull Project project) {
        YApiSettings settings = YApiSettings.getInstance(project);
        if (StringUtils.isBlank(settings.getUrl()) || settings.getProjectId() == null
                || !IntegrationCredentialService.getInstance(project)
                .readStatus(IntegrationCredentialService.Provider.YAPI).apiTokenConfigured()) {
            DocViewBackgroundTasks.invokeLater(project, () -> {
                DocViewNotification.notifyError(project, DocViewBundle.message("notify.yapi.info.settings"));
                ShowSettingsUtil.getInstance().showSettingsDialog(project, YApiSettingsConfigurable.class);
            });
            return false;
        }
        return true;
    }

    @Override
    public void doUpload(@NotNull Project project, @NotNull DocView docView) {
        try {
            ProgressManager.checkCanceled();
            if (project.isDisposed()) throw new ProcessCanceledException();
            YApiFacadeService facade = ApplicationManager.getApplication().getService(YApiFacadeServiceImpl.class);
            var result = new YApiUploadOrchestrator(facade, saveFactory).upload(project, List.of(docView)).get(0);
            ProgressManager.checkCanceled();
            if (project.isDisposed()) throw new ProcessCanceledException();
            if ("failed".equals(result.getStatus())) {
                DocViewNotification.notifyError(project, DocViewBundle.message("notify.integration.upload.error"));
            } else {
                DocViewNotification.uploadSuccess(project, "YApi", result.getYapiUrl());
            }
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception failure) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.integration.upload.error"));
        }
    }
}
