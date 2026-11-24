package com.liuzhihang.doc.view.action;

import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.utils.RestRequestBuilder;

public abstract class CopyRestRequestAction extends CopyRequestAction {
    private final boolean json5;

    protected CopyRestRequestAction(boolean json5) {
        this.json5 = json5;
    }

    @Override
    protected String render(DocumentTarget target, DocView document) {
        return RestRequestBuilder.build(document, json5);
    }

    @Override
    protected String successMessage(DocumentTarget target) {
        return "已复制 REST 请求（" + (json5 ? "JSON5" : "JSON") + "）：" + target.method().getName();
    }

    public static final class Json extends CopyRestRequestAction {
        public Json() { super(false); }
    }

    public static final class Json5 extends CopyRestRequestAction {
        public Json5() { super(true); }
    }
}
