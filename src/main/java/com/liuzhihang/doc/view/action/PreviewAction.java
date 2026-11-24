package com.liuzhihang.doc.view.action;

import com.liuzhihang.doc.view.ui.PreviewForm;

public class PreviewAction extends AbstractAction {
    @Override
    protected void perform(DocumentTarget target) {
        PreviewForm.getInstance(target.type(), target.method()).popup();
    }
}
