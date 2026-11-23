package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.javadoc.service.generator.impl.FieldDocGenerator;
import com.liuzhihang.doc.view.service.gpt.GptService;
import com.liuzhihang.doc.view.service.gpt.ModelRequestException;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.javadoc.service.JavaDocGeneratorServiceImpl;
import com.liuzhihang.doc.view.service.WriterService;

import java.util.function.Supplier;

final class SourceCommentActionSupport {
    private SourceCommentActionSupport() {}

    static <T> T service(Class<T> type) {
        return ApplicationManager.getApplication().getService(type);
    }

    static EasyDocConfig config() {
        return service(EasyDocConfigComponent.class).getState();
    }

    static PsiElement context(AnActionEvent event) {
        var editor = event.getData(CommonDataKeys.EDITOR);
        var file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor != null && file != null && file.getTextLength() > 0) {
            int offset = Math.min(editor.getCaretModel().getOffset(), file.getTextLength() - 1);
            PsiElement atCaret = file.findElementAt(offset);
            if (atCaret != null) {
                return atCaret;
            }
        }
        return event.getData(CommonDataKeys.PSI_ELEMENT);
    }

    static PsiJavaDocumentedElement javaTarget(PsiElement context) {
        for (PsiElement element = context; element != null; element = element.getParent()) {
            if (element instanceof PsiRecordComponent component) {
                return component.getContainingClass();
            }
            if (element instanceof PsiField field) {
                return WriterService.declarationOwner(field);
            }
            if (element instanceof PsiClass || element instanceof PsiMethod) {
                return (PsiJavaDocumentedElement) element;
            }
        }
        return null;
    }

    static boolean projectFile(PsiElement element) {
        if (element == null || !element.isValid() || !element.isWritable()) {
            return false;
        }
        var file = element.getContainingFile();
        return file != null && file.getVirtualFile() != null
                && ProjectFileIndex.getInstance(element.getProject()).isInContent(file.getVirtualFile());
    }

    static WriterService.JavaChange prepare(PsiJavaDocumentedElement element) {
        var settings = config();
        String mode = settings.getCoverMode();
        var writer = service(WriterService.class);
        String provider = settings.getTranslator();
        var pending = ReadAction.compute(() -> {
            if (!eligible(element, settings)) {
                return null;
            }
            Supplier<String> generation;
            if (Consts.AI_TRANSLATOR.contains(provider)) {
                var model = service(GptService.class);
                String kind = element instanceof PsiClass ? "class" : element instanceof PsiMethod ? "method" : "field";
                var input = model.captureCommentInput(element, kind, provider);
                generation = () -> model.requestComment(input);
            } else {
                generation = service(JavaDocGeneratorServiceImpl.class).prepare(element);
            }
            return new PendingChange(SmartPointerManager.createPointer(element),
                    WriterService.modificationStamp(element.getContainingFile()), generation);
        });
        if (pending == null) {
            return null;
        }
        ProgressManager.checkCanceled();
        String generated = pending.generation().get();
        ProgressManager.checkCanceled();
        return ReadAction.compute(() -> {
            var target = pending.target().getElement();
            return projectFile(target) && WriterService.modificationStamp(target.getContainingFile()) == pending.stamp()
                    ? writer.prepareJavadoc(target, generated, mode) : null;
        });
    }

    private record PendingChange(SmartPsiElementPointer<PsiJavaDocumentedElement> target, long stamp,
                                 Supplier<String> generation) {}

    private static boolean eligible(PsiJavaDocumentedElement target, EasyDocConfig settings) {
        return WriterService.canWriteJavadoc(target) && projectFile(target)
                && (!EasyDocConfig.COVER_MODE_IGNORE.equals(settings.getCoverMode()) || target.getDocComment() == null)
                && (!(target instanceof PsiField field) || !FieldDocGenerator.isExcludedField(settings, field.getName()));
    }

    static void showGenerationError(Project project, String title, Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof ModelRequestException) {
                Messages.showErrorDialog(project, cause.getMessage(), title);
                return;
            }
            var next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
        }
        Messages.showErrorDialog(project, "生成失败，请检查注释模板、翻译或模型配置。", title);
    }

    static <T> T compute(Project project, String title, Supplier<T> computation) {
        try {
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    computation::get, title, true, project);
        } catch (ProcessCanceledException e) {
            return null;
        } catch (RuntimeException e) {
            showGenerationError(project, title, e);
            return null;
        }
    }
}
