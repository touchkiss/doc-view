package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.RestRequestBuilder;
import org.jetbrains.annotations.NotNull;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public abstract class CopyRestRequestAction extends CopyCurlAction {
    protected abstract boolean json5();
    @Override public void actionPerformed(@NotNull AnActionEvent e) {
        // Run AbstractAction's context validation without invoking CopyCurlAction.
        validateContext(e);
        if (project == null || targetClass == null || targetMethod == null) return;
        List<DocView> docs = DocViewService.getInstance(project, targetClass).buildDoc(targetClass, targetMethod);
        String request = docs.isEmpty() ? "" : RestRequestBuilder.build(docs.getFirst(), json5());
        if (request.isBlank()) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.copy.curl.empty"));
            return;
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(request));
        DocViewNotification.notifyInfo(project, "已复制 REST 请求（" + (json5() ? "JSON5" : "JSON") + "）：" + targetMethod.getName());
    }
    public static final class Json extends CopyRestRequestAction { @Override protected boolean json5() { return false; } }
    public static final class Json5 extends CopyRestRequestAction { @Override protected boolean json5() { return true; } }
}
