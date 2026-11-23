package com.star.easydoc.action;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.PsiTreeUtil;
import com.star.easydoc.service.PackageInfoService;
import com.star.easydoc.service.WriterService;
import com.star.easydoc.service.translator.TranslatorService;
import com.star.easydoc.view.inner.GenerateAllView;
import com.star.easydoc.view.inner.PackageDescribeView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.star.easydoc.action.SourceCommentActionSupport.*;

public class GenerateAllJavadocAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        boolean enabled = false;
        if (project != null && !DumbService.isDumb(project)) {
            var context = context(event);
            enabled = service(PackageInfoService.class).packageDirectory(context) != null
                    || projectFile(context) && PsiTreeUtil.getParentOfType(context, PsiClass.class, false) != null;
        }
        event.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        PsiElement context = context(event);
        var packages = service(PackageInfoService.class);
        PsiDirectory directory = packages.packageDirectory(context);
        if (directory != null) {
            generatePackages(project, packages, directory);
            return;
        }
        PsiClass target = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (!projectFile(target)) {
            return;
        }

        var settings = config();
        var options = new GenerateAllView();
        options.getClassCheckBox().setSelected(Boolean.TRUE.equals(settings.getGenAllClass()));
        options.getMethodCheckBox().setSelected(Boolean.TRUE.equals(settings.getGenAllMethod()));
        options.getFieldCheckBox().setSelected(Boolean.TRUE.equals(settings.getGenAllField()));
        options.getInnerClassCheckBox().setSelected(Boolean.TRUE.equals(settings.getGenAllInnerClass()));
        if (!options.showAndGet()) {
            return;
        }

        boolean type = options.getClassCheckBox().isSelected();
        boolean method = options.getMethodCheckBox().isSelected();
        boolean field = options.getFieldCheckBox().isSelected();
        boolean nested = options.getInnerClassCheckBox().isSelected();
        settings.setGenAllClass(type);
        settings.setGenAllMethod(method);
        settings.setGenAllField(field);
        settings.setGenAllInnerClass(nested);

        List<PsiJavaDocumentedElement> targets = new ArrayList<>();
        collect(target, targets, type, method, field, nested);
        BatchJavadocGeneration.start(project, target.getContainingFile(), targets);
    }

    static void collect(PsiClass target, List<PsiJavaDocumentedElement> elements,
                                boolean type, boolean method, boolean field, boolean nested) {
        if (type && WriterService.canWriteJavadoc(target)) {
            elements.add(target);
        }
        if (method) {
            Arrays.stream(target.getMethods()).filter(WriterService::canWriteJavadoc).forEach(elements::add);
        }
        if (field) {
            Arrays.stream(target.getFields()).filter(WriterService::canWriteJavadoc).forEach(elements::add);
        }
        if (nested) {
            for (PsiClass inner : target.getInnerClasses()) {
                collect(inner, elements, type, method, field, true);
            }
        }
    }

    private static void generatePackages(Project project, PackageInfoService packages, PsiDirectory directory) {
        var chooser = new PackageChooserDialog("选择要生成包注释的 Java 包", project);
        var current = packages.sourcePackage(directory);
        if (current != null) {
            chooser.selectPackage(current.getQualifiedName());
        }
        if (!chooser.showAndGet() || chooser.getSelectedPackages().isEmpty()) {
            return;
        }
        Map<PsiPackage, PsiDirectory> directories = new LinkedHashMap<>();
        for (var pkg : chooser.getSelectedPackages()) {
            PsiDirectory resolved = packages.resolveDirectory(pkg, directory);
            if (resolved == null) {
                com.intellij.openapi.ui.Messages.showWarningDialog(project,
                        "无法唯一确定包 " + pkg.getQualifiedName() + " 的源码目录。请从对应源码目录生成包注释。",
                        "批量生成包注释");
                return;
            }
            directories.put(pkg, resolved);
        }
        var translator = service(TranslatorService.class);
        var descriptions = compute(project, "准备包注释", () -> {
            Map<PsiPackage, String> translated = new LinkedHashMap<>();
            for (var pkg : directories.keySet()) {
                ProgressManager.checkCanceled();
                String name = ReadAction.compute(pkg::getName);
                String description = translator.translate(name, pkg);
                translated.put(pkg, description.isBlank() ? name : description);
            }
            return translated;
        });
        if (descriptions == null) {
            return;
        }
        var dialog = new PackageDescribeView(descriptions);
        if (dialog.showAndGet()) {
            Map<PsiDirectory, String> result = new LinkedHashMap<>();
            dialog.getFinalMap().forEach((pkg, description) -> result.put(directories.get(pkg), description));
            packages.write(project, result, config().getCoverMode());
        }
    }
}
