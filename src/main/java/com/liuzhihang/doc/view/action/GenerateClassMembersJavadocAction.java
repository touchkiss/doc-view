package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.service.ProtoCommentSupport;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.liuzhihang.doc.view.action.SourceCommentActionSupport.*;

public final class GenerateClassMembersJavadocAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var context = context(event);
        var proto = service(ProtoCommentSupport.class);
        event.getPresentation().setEnabledAndVisible(project != null && !DumbService.isDumb(project) && projectFile(context)
                && (context.getContainingFile() instanceof PsiJavaFile || proto != null && proto.supports(context)));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        var context = context(event);
        if (!projectFile(context)) {
            return;
        }
        var file = context.getContainingFile();
        var proto = service(ProtoCommentSupport.class);
        if (proto != null && proto.supports(context)) {
            proto.generate(context, List.of(), true);
        } else if (file instanceof PsiJavaFile javaFile) {
            var type = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
            List<PsiJavaDocumentedElement> targets = new ArrayList<>();
            var classes = type == null ? javaFile.getClasses() : new PsiClass[]{type};
            for (var candidate : classes) {
                GenerateAllJavadocAction.collect(candidate, targets, false, true, true, true);
            }
            if (!targets.isEmpty()) {
                BatchJavadocGeneration.start(project, file, targets);
            }
        }
    }
}
