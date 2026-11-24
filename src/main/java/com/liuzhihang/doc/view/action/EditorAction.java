package com.liuzhihang.doc.view.action;

import com.liuzhihang.doc.view.ui.ParamDocEditorForm;
import com.liuzhihang.doc.view.utils.DocViewUtils;

public class EditorAction extends AbstractAction {
    @Override
    protected boolean supportsProto() {
        return false;
    }

    @Override
    protected boolean supports(DocumentTarget target) {
        return !target.type().isInterface() && !DocViewUtils.isDocViewClass(target.type());
    }

    @Override
    protected void perform(DocumentTarget target) {
        ParamDocEditorForm.getInstance(target.project(), target.file(), target.editor(), target.type()).popup();
    }
}
