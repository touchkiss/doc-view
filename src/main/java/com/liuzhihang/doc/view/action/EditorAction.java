package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.ui.ParamDocEditorForm;
import com.liuzhihang.doc.view.utils.CustomPsiUtils;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑实体类字段注释
 *
 * @author liuzhihang
 * @date 2020/2/26 21:57
 */
public class EditorAction extends AbstractAction {

    /**
     * @see AnAction#actionPerformed(AnActionEvent)
     */
    @Override
    public void actionPerformed(AnActionEvent e) {

        // 获取当前project对象
        Project project = e.getData(PlatformDataKeys.PROJECT);
        // 获取当前编辑的文件, 可以进而获取 PsiClass, PsiField 对象
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (editor == null || project == null || psiFile == null || DumbService.isDumb(project)) {
            return;
        }

        // .proto 文件不支持 Doc Editor: 编辑结果需要通过 WriterService 以 JavaDoc 形式写回源码,
        // 而 proto 文档背后是合成出来的非物理类, 写回没有意义
        if (CustomPsiUtils.isProtoFile(psiFile)) {
            return;
        }

        // 获取Java类或者接口
        PsiClass targetClass = CustomPsiUtils.getTargetClass(editor, psiFile);

        if (targetClass == null || targetClass.isAnnotationType() || targetClass.isEnum()) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.error.class"));
            return;
        }

        ParamDocEditorForm.getInstance(project, psiFile, editor, targetClass).popup();
    }

    /**
     * 设置右键菜单是否隐藏 Doc View
     *
     * @param e
     */
    @Override
    public void update(@NotNull AnActionEvent e) {

        Project project = e.getData(PlatformDataKeys.PROJECT);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        Presentation presentation = e.getPresentation();

        if (editor == null || project == null || psiFile == null || DumbService.isDumb(project)) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        // .proto 文件隐藏 Doc Editor。
        // 必须在 getTargetClass 之前判断: 否则每次弹出右键菜单都会在 BGT 上合成一次 Java 类
        if (CustomPsiUtils.isProtoFile(psiFile)) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        PsiClass targetClass = CustomPsiUtils.getTargetClass(editor, psiFile);

        if (targetClass == null || targetClass.isAnnotationType() || targetClass.isInterface() || targetClass.isEnum()) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        // 判断是否是 Doc View 类，是的话 隐藏
        if (DocViewUtils.isDocViewClass(targetClass)) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        presentation.setEnabledAndVisible(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}
