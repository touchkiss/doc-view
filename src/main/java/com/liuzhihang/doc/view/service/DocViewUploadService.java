package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.UploadPlatform;
import com.liuzhihang.doc.view.service.impl.ShowDocServiceImpl;
import com.liuzhihang.doc.view.service.impl.YApiServiceImpl;
import com.liuzhihang.doc.view.service.impl.YuQueServiceImpl;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Dispatches document uploads on a cancellable background task. */
public interface DocViewUploadService {

    List<UploadPlatform> UPLOAD_OPTIONS = List.of(UploadPlatform.values());

    @NotNull
    static DocViewUploadService getInstance(@NotNull UploadPlatform platform) {
        return switch (platform) {
            case YAPI -> ApplicationManager.getApplication().getService(YApiServiceImpl.class);
            case SHOW_DOC -> ApplicationManager.getApplication().getService(ShowDocServiceImpl.class);
            case YUQUE -> ApplicationManager.getApplication().getService(YuQueServiceImpl.class);
        };
    }

    default void upload(@NotNull Project project, @NotNull List<DocView> docViewList) {
        DocViewBackgroundTasks.runTask(project, DocViewBundle.message("task.docview.upload"), true, indicator -> {
            if (project.isDisposed()) throw new ProcessCanceledException();
            if (!checkSettings(project)) {
                return;
            }
            for (DocView docView : docViewList) {
                indicator.checkCanceled();
                if (project.isDisposed()) throw new ProcessCanceledException();
                doUpload(project, docView);
            }
        }, throwable -> DocViewNotification.notifyError(project, DocViewBundle.message("notify.integration.upload.error")));
    }

    default void upload(@NotNull Project project, @NotNull DocView docView) {
        upload(project, List.of(docView));
    }

    boolean checkSettings(@NotNull Project project);

    void doUpload(@NotNull Project project, @NotNull DocView docView);
}
