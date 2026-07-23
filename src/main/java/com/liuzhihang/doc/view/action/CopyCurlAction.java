package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.CustomPsiUtils;
import com.liuzhihang.doc.view.utils.CurlUtils;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * 右键菜单复制 curl 命令
 *
 * @author liuzhihang
 */
public class CopyCurlAction extends AbstractAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        super.actionPerformed(e);

        if (project == null || targetClass == null || targetMethod == null) {
            return;
        }

        List<DocView> docViewList = DocViewService.getInstance(project, targetClass)
                .buildDoc(targetClass, targetMethod);

        if (docViewList.isEmpty()) {
            DocViewNotification.notifyError(project,
                    DocViewBundle.message("notify.copy.curl.empty"));
            return;
        }

        DocView docView = docViewList.getFirst();
        String curl = CurlUtils.build(docView);

        if (curl == null || curl.isBlank()) {
            DocViewNotification.notifyError(project,
                    DocViewBundle.message("notify.copy.curl.empty"));
            return;
        }

//      替换为本地服务地址，否则{{host}}无法被IDEA识别，粘贴转换为http失败
        curl = curl.replace("{{host}}", "http://localhost:8080");
        StringSelection selection = new StringSelection(curl);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);

        DocViewNotification.notifyInfo(project,
                DocViewBundle.message("notify.copy.curl.success", targetMethod.getName()));
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

        PsiClass targetClass = CustomPsiUtils.getTargetClass(editor, psiFile);
        if (targetClass == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        PsiMethod targetMethod = CustomPsiUtils.getTargetMethod(editor, psiFile);
        if (targetMethod == null || !SpringPsiUtils.isSpringMethod(targetMethod)) {
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
