package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.*;
import org.jetbrains.annotations.NotNull;
import java.awt.datatransfer.StringSelection;
import java.util.regex.*;

public abstract class ProtoGrpcCopyRestAction extends ProtoGrpcCopyCurlAction {
    protected abstract boolean descriptions();
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getData(CommonDataKeys.PROJECT);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || file == null || editor == null) return;
        String[] signature = ProtoGrpcUtils.extractMethodSignature(editor, file);
        if (signature == null) { DocViewNotification.notifyError(project, "无法识别 gRPC 方法"); return; }
        Matcher pkg = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(file.getText());
        String service = (pkg.find() ? pkg.group(1) + "." : "") + signature[0];
        String request = GrpcRestRequestBuilder.build(CurlHostUtils.grpcHost(project), service, signature[1],
                ProtoGrpcUtils.parseMessageFields(file, signature[2]), descriptions());
        CopyPasteManager.getInstance().setContents(new StringSelection(request));
        DocViewNotification.notifyInfo(project, "已复制 gRPC HTTP Client 请求：" + signature[1]);
    }
    public static final class Json extends ProtoGrpcCopyRestAction { @Override protected boolean descriptions() { return false; } }
    public static final class Json5 extends ProtoGrpcCopyRestAction { @Override protected boolean descriptions() { return true; } }
}
