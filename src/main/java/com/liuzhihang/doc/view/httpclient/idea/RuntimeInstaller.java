package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.httpClient.execution.common.RequestExecutionSupport;
import com.intellij.httpClient.execution.impl.HttpRequestRunSupport;
import com.intellij.openapi.extensions.ExtensionPoint;
import java.util.ArrayList;
import java.util.List;

/** IDEA asserts a unique execution support: replace the default instead of adding a competing one. */
public final class RuntimeInstaller implements Disposable {
    private final List<RequestExecutionSupport<?>> originals = new ArrayList<>();
    private final Json5ExecutionSupport replacement = new Json5ExecutionSupport();
    private final ExtensionPoint<RequestExecutionSupport<?>> point;
    public RuntimeInstaller() {
        point = RequestExecutionSupport.All.getEP_NAME().getPoint();
        for (var support : point.getExtensionList()) {
            if (support.getClass() == HttpRequestRunSupport.class) originals.add(support);
        }
        if (originals.size() != 1) throw new IllegalStateException("Doc View JSON5 support requires exactly one native HTTP run support");
        originals.forEach(point::unregisterExtension);
        point.registerExtension(replacement);
    }
    public static void install() { ApplicationManager.getApplication().getService(RuntimeInstaller.class); }
    @Override public void dispose() {
        point.unregisterExtension(replacement);
        originals.forEach(point::registerExtension);
    }
}
