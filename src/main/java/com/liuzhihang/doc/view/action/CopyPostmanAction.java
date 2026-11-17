package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.PostmanCollectionBuilder;
import org.jetbrains.annotations.NotNull;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public class CopyPostmanAction extends CopyCurlAction {
    @Override public void actionPerformed(@NotNull AnActionEvent e) {
        validateContext(e);
        if (project == null || targetClass == null || targetMethod == null) return;
        List<DocView> docs = DocViewService.getInstance(project, targetClass).buildDoc(targetClass, targetMethod);
        try {
            String text = docs.isEmpty() ? "" : PostmanCollectionBuilder.build(docs.getFirst());
            if (text.isBlank()) throw new IllegalArgumentException("请求数据为空");
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
            DocViewNotification.notifyInfo(project, "已复制 Postman Collection，请在 Postman Import 中粘贴");
        } catch (RuntimeException ex) {
            DocViewNotification.notifyError(project, "生成 Postman 请求失败：" + ex.getMessage());
        }
    }
}
