package com.liuzhihang.doc.view.action;

import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.utils.PostmanCollectionBuilder;

public final class CopyPostmanAction extends CopyRequestAction {
    @Override
    protected String render(DocumentTarget target, DocView document) {
        return PostmanCollectionBuilder.build(document);
    }

    @Override
    protected String successMessage(DocumentTarget target) {
        return "已复制 Postman Collection，请在 Postman Import 中粘贴";
    }
}
