package com.liuzhihang.doc.view.action;

import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.utils.CurlHostUtils;
import com.liuzhihang.doc.view.utils.CurlUtils;

public final class CopyCurlAction extends CopyRequestAction {
    @Override
    protected String render(DocumentTarget target, DocView document) {
        String curl = CurlUtils.build(document);
        return curl == null || curl.isBlank() ? "" : CurlHostUtils.applyCopyHost(target.project(), curl);
    }

    @Override
    protected String successMessage(DocumentTarget target) {
        return DocViewBundle.message("notify.copy.curl.success", target.method().getName());
    }
}
