package com.liuzhihang.doc.view.action.upload;

import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.action.AbstractAction;
import com.liuzhihang.doc.view.action.DocumentTarget;
import com.liuzhihang.doc.view.enums.UploadPlatform;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;

public abstract class AbstractUploadAction extends AbstractAction {
    private final UploadPlatform platform;

    protected AbstractUploadAction(UploadPlatform platform) {
        this.platform = platform;
    }

    @Override
    protected void perform(DocumentTarget target) {
        var project = target.project();
        DocViewBackgroundTasks.runReadTask(project, DocViewBundle.message("task.docview.upload"), true,
                indicator -> DocViewService.getInstance(project, target.type()).buildDoc(target.type(), target.method()),
                documents -> DocViewUploadService.getInstance(platform).upload(project, documents),
                throwable -> DocViewNotification.notifyError(project, throwable.getMessage()));
    }
}
