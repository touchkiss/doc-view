package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.GrpcCurlUtils;
import com.liuzhihang.doc.view.utils.ProtoGrpcUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * Right-click action to copy gRPC curl command from proto service method definition.
 * Parses the proto file to extract service name, method name, and request message fields,
 * then generates a curl command in the format: curl -X GRPC "{host}/{Service}/{Method}" -d '{json}'
 *
 * @author liuzhihang
 */
public class ProtoGrpcCopyCurlAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || psiFile == null || editor == null) {
            return;
        }

        // Extract method signature
        String[] signature = ProtoGrpcUtils.extractMethodSignature(editor, psiFile);
        if (signature == null || signature.length != 4) {
            DocViewNotification.notifyError(project, "Failed to extract gRPC method signature");
            return;
        }

        String serviceName = signature[0];
        String methodName = signature[1];
        String requestType = signature[2];

        // Parse request message fields
        List<String[]> fields = ProtoGrpcUtils.parseMessageFields(psiFile, requestType);

        // Generate JSON body
        String jsonBody = ProtoGrpcUtils.generateJsonBody(fields);

        // Build curl command
        String curl = GrpcCurlUtils.build(serviceName, methodName, jsonBody);
        if (curl == null || curl.isBlank()) {
            DocViewNotification.notifyError(project, "Failed to generate gRPC curl command");
            return;
        }

        // Copy to clipboard
        StringSelection selection = new StringSelection(curl);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);

        DocViewNotification.notifyInfo(project, "Copied gRPC cURL for " + methodName);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (editor == null || project == null || psiFile == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // Only show for proto files with cursor on rpc method definition
        if (!ProtoGrpcUtils.isProtoFile(psiFile) || !ProtoGrpcUtils.isGrpcMethod(editor, psiFile)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
