package com.liuzhihang.doc.view.action;

import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.ExportUtils;

public final class ExportDocumentAction extends AbstractAction {
    @Override
    protected void perform(DocumentTarget target) {
        String name = target.method() == null ? target.type().getName() : target.method().getName();
        ExportUtils.batchExportMarkdown(target.project(), name == null ? "DocView" : name,
                () -> DocViewService.getInstance(target.project(), target.type()).buildDoc(target.type(), target.method()));
    }
}
