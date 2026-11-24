package com.liuzhihang.doc.view.action.toolbar.preview;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.liuzhihang.doc.view.action.toolbar.AbstractToolbarUploadAction;
import com.liuzhihang.doc.view.data.DocViewDataKeys;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.ui.PreviewForm;
import com.liuzhihang.doc.view.ui.UploadPlatformPopup;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;

/**
 * Preview 右侧单个上传
 *
 * @author liuzhihang
 * @date 2021/10/23 22:31
 */
public class PreviewRightUploadAction extends AbstractToolbarUploadAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        // 获取当前project对象
        Project project = e.getData(PlatformDataKeys.PROJECT);
        JBPopup popup = e.getData(DocViewDataKeys.PREVIEW_POPUP);
        DocView currentDocView = e.getData(DocViewDataKeys.PREVIEW_CURRENT_DOC_VIEW);
        JPanel previewToolbarPanel = e.getData(DocViewDataKeys.PREVIEW_TOOLBAR_PANEL);

        if (popup == null || project == null || currentDocView == null || previewToolbarPanel == null) {
            return;
        }

        PreviewForm.myIsPinned.set(true);
        UploadPlatformPopup.show(project, previewToolbarPanel, service -> {
            if (!popup.isDisposed()) {
                service.upload(project, currentDocView);
            }
        });
    }
}
