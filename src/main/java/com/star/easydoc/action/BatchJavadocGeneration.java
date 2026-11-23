package com.star.easydoc.action;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.star.easydoc.service.WriterService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.star.easydoc.action.SourceCommentActionSupport.prepare;
import static com.star.easydoc.action.SourceCommentActionSupport.service;
import static com.star.easydoc.action.SourceCommentActionSupport.showGenerationError;

final class BatchJavadocGeneration {
    private BatchJavadocGeneration() {}

    static void start(Project project, PsiFile file, Collection<PsiJavaDocumentedElement> elements) {
        if (elements.isEmpty()) {
            return;
        }
        var targets = List.copyOf(elements);
        long stamp = WriterService.modificationStamp(file);
        new Task.Backgroundable(project, "批量生成 Javadoc", true) {
            private final List<WriterService.JavaChange> changes = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                for (int i = 0; i < targets.size(); i++) {
                    indicator.checkCanceled();
                    if (ReadAction.compute(this::fileChanged)) {
                        return;
                    }
                    indicator.setFraction((double) i / targets.size());
                    var change = prepare(targets.get(i));
                    if (change != null) {
                        changes.add(change);
                    }
                }
                indicator.setFraction(1);
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (fileChanged()) {
                    StatusBar.Info.set("文件在生成期间已修改，本次注释未写入。", project);
                    return;
                }
                service(WriterService.class).applyJavadocs(project, changes);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                if (!project.isDisposed()) {
                    showGenerationError(project, "批量生成 Javadoc", error);
                }
            }

            private boolean fileChanged() {
                return !file.isValid() || WriterService.modificationStamp(file) != stamp;
            }
        }.queue();
    }
}
