package com.liuzhihang.doc.view.service.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.ShowDocSettings;
import com.liuzhihang.doc.view.ui.settings.ShowDocSettingsConfigurable;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.render.DocumentMarkdownRenderer;
import com.liuzhihang.doc.view.integration.ShowDocFacadeService;
import com.liuzhihang.doc.view.integration.dto.ShowDocUpdateRequest;
import com.liuzhihang.doc.view.integration.dto.ShowDocUpdateResponse;
import com.liuzhihang.doc.view.integration.impl.ShowDocFacadeServiceImpl;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author liuzhihang
 * @date 2021/7/27 12:00
 */
@Service
public final class ShowDocServiceImpl implements DocViewUploadService {


    @Override
    public boolean checkSettings(@NotNull Project project) {
        ShowDocSettings apiSettings = ShowDocSettings.getInstance(project);
        if (StringUtils.isBlank(apiSettings.getUrl())
                || StringUtils.isBlank(apiSettings.getApiKey()) || StringUtils.isBlank(apiSettings.getApiToken())) {
            // 说明没有配置 ShowDoc 上传地址, 跳转到配置页面
            UploadInteraction.showSettings(project, ShowDocSettingsConfigurable.class, "notify.showdoc.info.settings");
            return false;
        }
        return true;
    }

    @Override
    public void doUpload(@NotNull Project project, @NotNull DocView docView) {

        try {
            UploadInteraction.checkCanceled(project);
            ShowDocSettings settings = ShowDocSettings.getInstance(project);
            ShowDocUpdateRequest request = new ShowDocUpdateRequest();
            request.setShowDocUrl(settings.getUrl());
            request.setApiKey(settings.getApiKey());
            request.setApiToken(settings.getApiToken());
            request.setCatName(docView.getDocTitle());
            request.setPageTitle(docView.getName());
            request.setPageContent(DocumentMarkdownRenderer.render(project, docView));

            ShowDocFacadeService facadeService = ApplicationManager.getApplication().getService(ShowDocFacadeServiceImpl.class);
            ShowDocUpdateResponse response = facadeService.updateByApi(request);

            ShowDocUpdateResponse.DataInner data = response.getData();

            String showDocInterfaceUrl = settings.getUrl() + "/" + data.getItemId() + "/" + data.getPageId();

            UploadInteraction.checkCanceled(project);
            DocViewNotification.uploadSuccess(project, DocViewBundle.message("platform.name.showdoc"), showDocInterfaceUrl);
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception _) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.showdoc.upload.error"));
        }
    }
}
