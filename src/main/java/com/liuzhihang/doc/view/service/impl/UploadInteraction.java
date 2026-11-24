package com.liuzhihang.doc.view.service.impl;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;
import org.jetbrains.annotations.PropertyKey;

final class UploadInteraction {
    private UploadInteraction() {}

    static void checkCanceled(Project project) {
        ProgressManager.checkCanceled();
        if (project.isDisposed()) {
            throw new ProcessCanceledException();
        }
    }

    static void showSettings(Project project, Class<? extends Configurable> settings,
                             @PropertyKey(resourceBundle = "messages.DocViewBundle") String messageKey) {
        DocViewBackgroundTasks.invokeLater(project, () -> {
            DocViewNotification.notifyError(project, DocViewBundle.message(messageKey));
            ShowSettingsUtil.getInstance().showSettingsDialog(project, settings);
        });
    }
}
