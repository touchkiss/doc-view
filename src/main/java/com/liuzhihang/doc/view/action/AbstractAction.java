package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.CustomPsiUtils;
import org.jetbrains.annotations.NotNull;

/** Stateless adapter shared by commands which consume one source declaration. */
public abstract class AbstractAction extends AnAction {
    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public final void update(@NotNull AnActionEvent event) {
        boolean available = false;
        Project project = event.getProject();
        if (ready(project)) {
            var selection = ActionSelection.from(event);
            if (CustomPsiUtils.isProtoFile(selection.file())) {
                available = supportsProto() && CustomPsiUtils.hasProtoMessage(selection.editor(), selection.file());
            } else {
                var target = javaTarget(project, selection);
                available = target != null && supports(target);
            }
        }
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (!ready(project)) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        var selection = ActionSelection.from(event);
        DocumentTarget target;
        if (CustomPsiUtils.isProtoFile(selection.file())) {
            if (!supportsProto()) {
                return;
            }
            PsiClass type = CustomPsiUtils.getProtoTargetClass(selection.editor(), selection.file());
            if (type == null) {
                DocViewNotification.notifyError(project, DocViewBundle.message("notify.error.proto.no.message"));
                return;
            }
            target = new DocumentTarget(project, selection.file(), selection.editor(), type, null);
        } else {
            target = javaTarget(project, selection);
        }
        if (target != null && supports(target)) {
            perform(target);
        } else {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.error.class"));
        }
    }

    private static boolean ready(Project project) {
        return project != null && !project.isDisposed() && !DumbService.isDumb(project);
    }

    private static DocumentTarget javaTarget(Project project, ActionSelection selection) {
        var element = selection.declaration();
        if (element == null || !element.isValid() || selection.file() == null) {
            return null;
        }
        var type = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
        if (type == null || type instanceof SyntheticElement || type.isAnnotationType() || type.isEnum()) {
            return null;
        }
        var method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        if (method instanceof SyntheticElement || method != null && method.getContainingClass() != type) {
            method = null;
        }
        return new DocumentTarget(project, selection.file(), selection.editor(), type, method);
    }

    protected boolean supportsProto() {
        return true;
    }

    protected boolean supports(DocumentTarget target) {
        return true;
    }

    /**
     * Executes the command for a validated source target.
     * @param target resolved source target
     */
    protected abstract void perform(DocumentTarget target);
}
