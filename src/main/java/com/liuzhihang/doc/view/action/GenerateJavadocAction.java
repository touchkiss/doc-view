package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiDocumentManager;
import com.liuzhihang.doc.view.common.util.LanguageUtil;
import com.liuzhihang.doc.view.common.util.StringUtil;
import com.liuzhihang.doc.view.service.KdocSupport;
import com.liuzhihang.doc.view.service.PackageInfoService;
import com.liuzhihang.doc.view.service.ProtoCommentSupport;
import com.liuzhihang.doc.view.service.WriterService;
import com.liuzhihang.doc.view.service.translator.TranslatorService;
import com.liuzhihang.doc.view.view.inner.TranslateResultView;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.liuzhihang.doc.view.action.SourceCommentActionSupport.*;

public class GenerateJavadocAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null || DumbService.isDumb(project)) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }
        var selection = ActionSelection.from(event);
        var editor = selection.editor();
        var context = selection.declaration();
        var kotlin = service(KdocSupport.class);
        var proto = service(ProtoCommentSupport.class);
        String selectedText = editor == null ? null : editor.getSelectionModel().getSelectedText();
        boolean translate = selectedText != null && !selectedText.isBlank();
        var packages = service(PackageInfoService.class);
        var directory = packages.packageDirectory(context);
        event.getPresentation().setText(translate ? "翻译选区" : directory == null ? "生成注释" : "生成包注释");
        event.getPresentation().setEnabledAndVisible(translate
                || directory != null && packages.sourcePackage(directory) != null
                || projectFile(context) && (context.getContainingFile() instanceof PsiJavaFile && (javaTarget(context) != null
                            || editor != null && editor.getCaretModel().getCaretCount() > 1
                                    && editor.getCaretModel().getAllCarets().stream().anyMatch(caret ->
                                            javaTarget(context.getContainingFile().findElementAt(caret.getOffset())) != null))
                        || kotlin != null && kotlin.supports(context)
                        || proto != null && proto.supports(context)));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        var selection = ActionSelection.from(event);
        var editor = selection.editor();
        var context = selection.declaration();
        String selectedText = editor == null ? null : editor.getSelectionModel().getSelectedText();
        var writer = service(WriterService.class);
        var translator = service(TranslatorService.class);
        if (selectedText != null && !selectedText.isBlank()) {
            int start = editor.getSelectionModel().getSelectionStart();
            boolean naming = LanguageUtil.isAllChinese(selectedText);
            String result = compute(project, "翻译选区", () -> naming
                    ? translator.translateCh2En(selectedText, context)
                    : translator.autoTranslate(selectedText.contains(" ") ? selectedText
                            : String.join(" ", StringUtil.split(selectedText)), context));
            if (result != null) {
                if (naming) {
                    writer.write(project, editor, selectedText, start, result);
                } else {
                    new TranslateResultView(result).show();
                }
            }
            return;
        }

        var packages = service(PackageInfoService.class);
        PsiDirectory directory = packages.packageDirectory(context);
        if (directory != null) {
            var pkg = packages.sourcePackage(directory);
            if (pkg != null) {
                String name = pkg.getName();
                String description = compute(project, "准备包注释", () -> translator.translate(name, pkg));
                if (description != null) {
                    packages.write(project, Map.of(directory, description.isBlank() ? pkg.getName() : description),
                            config().getCoverMode());
                }
            }
            return;
        }
        if (!projectFile(context)) {
            return;
        }

        var file = context.getContainingFile();
        var proto = service(ProtoCommentSupport.class);
        if (proto != null && proto.supports(context)) {
            var offsets = editor == null ? List.<Integer>of() :
                    editor.getCaretModel().getAllCarets().stream().map(caret -> caret.getOffset()).toList();
            proto.generate(context, offsets, false);
            return;
        }

        if (editor != null && editor.getCaretModel().getCaretCount() > 1 && file instanceof PsiJavaFile) {
            var targets = new LinkedHashSet<PsiJavaDocumentedElement>();
            for (var caret : editor.getCaretModel().getAllCarets()) {
                var target = javaTarget(file.findElementAt(caret.getOffset()));
                if (target != null) {
                    targets.add(target);
                }
            }
            if (!targets.isEmpty()) {
                BatchJavadocGeneration.start(project, file, targets);
            }
            return;
        }

        var target = javaTarget(context);
        if (target != null) {
            var change = compute(project, "准备 Javadoc", () -> prepare(target));
            if (change != null) {
                writer.applyJavadocs(project, List.of(change));
            }
            return;
        }
        var kotlin = service(KdocSupport.class);
        if (kotlin != null && kotlin.supports(context)) {
            Runnable change = compute(project, "准备 KDoc",
                    () -> kotlin.prepare(context, config().getCoverMode()));
            if (change != null) {
                change.run();
            }
        }
    }
}
