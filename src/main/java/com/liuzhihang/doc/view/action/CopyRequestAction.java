package com.liuzhihang.doc.view.action;

import com.intellij.openapi.ide.CopyPasteManager;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;

import java.awt.datatransfer.StringSelection;

abstract class CopyRequestAction extends AbstractAction {
    @Override
    protected final boolean supportsProto() {
        return false;
    }

    @Override
    protected final boolean supports(DocumentTarget target) {
        return target.method() != null && SpringPsiUtils.isSpringMethod(target.method());
    }

    @Override
    protected final void perform(DocumentTarget target) {
        var project = target.project();
        String message = successMessage(target);
        DocViewBackgroundTasks.runReadTask(project, "生成请求示例", true, indicator -> {
            if (!target.type().isValid() || target.method() == null || !target.method().isValid()) {
                return "";
            }
            var documents = DocViewService.getInstance(project, target.type()).buildDoc(target.type(), target.method());
            indicator.checkCanceled();
            return documents.isEmpty() ? "" : render(target, documents.getFirst());
        }, text -> {
            if (text == null || text.isBlank()) {
                DocViewNotification.notifyError(project, DocViewBundle.message("notify.copy.curl.empty"));
                return;
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
            DocViewNotification.notifyInfo(project, message);
        }, error -> DocViewNotification.notifyError(project, "生成请求失败：" + error.getMessage()));
    }

    protected abstract String render(DocumentTarget target, DocView document);

    protected abstract String successMessage(DocumentTarget target);
}
