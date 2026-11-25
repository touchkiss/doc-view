package com.liuzhihang.doc.view.service.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.ui.settings.YuQueSettingsConfigurable;
import com.liuzhihang.doc.view.config.YuQueSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.render.DocumentMarkdownRenderer;
import com.liuzhihang.doc.view.integration.YuQueFacadeService;
import com.liuzhihang.doc.view.integration.dto.YuQueCreate;
import com.liuzhihang.doc.view.integration.dto.YuQueResponse;
import com.liuzhihang.doc.view.integration.dto.YuQueUpdate;
import com.liuzhihang.doc.view.integration.impl.YuQueFacadeServiceImpl;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 上传文档到语雀
 *
 * @author liuzhihang
 * @date 2021/7/27 12:00
 */
@Service
public final class YuQueServiceImpl implements DocViewUploadService {

    @Override
    public boolean checkSettings(@NotNull Project project) {

        YuQueSettings apiSettings = YuQueSettings.getInstance(project);
        if (StringUtils.isBlank(apiSettings.getUrl())
                || StringUtils.isBlank(apiSettings.getApiUrl())
                || StringUtils.isBlank(apiSettings.getRepos())
                || StringUtils.isBlank(apiSettings.getLogin())
                || StringUtils.isBlank(apiSettings.getToken())) {
            UploadInteraction.showSettings(project, YuQueSettingsConfigurable.class, "notify.yuque.info.settings");
            return false;
        }
        return true;
    }

    @Override
    public void doUpload(@NotNull Project project, @NotNull DocView docView) {

        try {
            UploadInteraction.checkCanceled(project);
            YuQueSettings settings = YuQueSettings.getInstance(project);
            String token = settings.getToken();
            YuQueFacadeService facadeService = ApplicationManager.getApplication().getService(YuQueFacadeServiceImpl.class);
            String slug;
            if (docView.getPath().startsWith("/")) {
                slug = docView.getPath().substring(1).replace("/", "_");
            } else {
                slug = docView.getPath().replace("/", "_");
            }


            // 获取文章是否存在
            YuQueResponse doc = facadeService.getDoc(settings.getApiUrl(), token, settings.getNamespace(), slug);

            YuQueResponse yuQueResponse;
            if (doc == null) {
                // 文档不存在
                YuQueCreate yuQueCreate = new YuQueCreate();
                yuQueCreate.setTitle(docView.getName());
                yuQueCreate.setSlug(slug);
                yuQueCreate.setBody(DocumentMarkdownRenderer.render(project, docView));
                yuQueResponse = facadeService.create(settings.getApiUrl(), token, settings.getNamespace(), yuQueCreate);

            } else {
                // 文档存在
                YuQueUpdate yuQueCreate = new YuQueUpdate();
                yuQueCreate.setTitle(docView.getName());
                yuQueCreate.setSlug(slug);
                yuQueCreate.setBody(DocumentMarkdownRenderer.render(project, docView));
                yuQueResponse = facadeService.update(settings.getApiUrl(), token, settings.getNamespace(), doc.getData().getId(), yuQueCreate);
            }

            String yuQueDocUrl = settings.getUrl() + "/" + settings.getNamespace() + "/" + yuQueResponse.getData().getSlug();
            UploadInteraction.checkCanceled(project);
            DocViewNotification.uploadSuccess(project, DocViewBundle.message("platform.name.yuque"), yuQueDocUrl);
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception _) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.yuque.upload.error"));
        }
    }
}
