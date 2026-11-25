package com.liuzhihang.doc.view.action.toolbar.window;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.action.toolbar.AbstractToolbarUploadAction;
import com.liuzhihang.doc.view.data.DocViewDataKeys;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.ui.UploadPlatformPopup;
import com.liuzhihang.doc.view.ui.window.RootNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author liuzhihang
 * @date 2021/10/23 19:55
 */
public class WindowUploadAction extends AbstractToolbarUploadAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean available = project != null
                && !project.isDisposed()
                && !DumbService.isDumb(project)
                && e.getData(DocViewDataKeys.WINDOW_ROOT_NODE) != null
                && e.getData(DocViewDataKeys.WINDOW_TOOLBAR) != null;
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        // 获取当前project对象
        Project project = e.getProject();
        JComponent toolbar = e.getData(DocViewDataKeys.WINDOW_TOOLBAR);
        RootNode rootNode = e.getData(DocViewDataKeys.WINDOW_ROOT_NODE);

        if (project == null || toolbar == null || rootNode == null || rootNode.getChildCount() == 0) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.window.upload.empty"));
            return;
        }

        UploadPlatformPopup.show(project, toolbar, service -> service.upload(project, rootNode.docViewList()));
    }
}
