package com.liuzhihang.doc.view.service;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.config.EasyDocConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PackageInfoService {
    public static final String INFO_FILE_NAME = "package-info.java";
    public static final String PACKAGE_INFO_DESCRIBE = "PACKAGE_INFO_DESCRIBE";

    public PsiDirectory packageDirectory(PsiElement context) {
        if (context instanceof PsiDirectory directory) {
            return directory;
        }
        var file = context == null ? null : context.getContainingFile();
        return file == null || !INFO_FILE_NAME.equals(file.getName()) ? null : file.getContainingDirectory();
    }

    public PsiPackage sourcePackage(PsiDirectory directory) {
        if (!directory.isValid() || !directory.isWritable()
                || !ProjectFileIndex.getInstance(directory.getProject()).isInSourceContent(directory.getVirtualFile())) {
            return null;
        }
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        return pkg == null || pkg.getQualifiedName().isBlank() ? null : pkg;
    }

    public PsiDirectory resolveDirectory(PsiPackage pkg, PsiDirectory selected) {
        List<PsiDirectory> directories = Arrays.stream(pkg.getDirectories())
                .filter(dir -> sourcePackage(dir) != null).toList();
        if (directories.contains(selected)) {
            return selected;
        }
        var index = ProjectFileIndex.getInstance(selected.getProject());
        var root = index.getSourceRootForFile(selected.getVirtualFile());
        var siblings = directories.stream()
                .filter(dir -> root != null && root.equals(index.getSourceRootForFile(dir.getVirtualFile()))).toList();
        if (siblings.size() == 1) {
            return siblings.getFirst();
        }
        return directories.size() == 1 ? directories.getFirst() : null;
    }

    public void write(Project project, Map<PsiDirectory, String> descriptions, String mode) {
        WriteCommandAction.writeCommandAction(project).withName("生成包注释").run(() ->
                descriptions.forEach((directory, description) -> writeOne(directory, description, mode)));
    }

    private void writeOne(PsiDirectory directory, String description, String mode) {
        PsiPackage pkg = sourcePackage(directory);
        if (pkg == null || description == null || description.isBlank()) {
            return;
        }
        String brief = WriterService.escapeJavaUnicodeSequences(description.replace("*/", "*&#47;").replaceAll("\\R+", " ")).strip();
        String doc = "/**\n * " + brief + "\n */\n";
        var file = directory.findFile(INFO_FILE_NAME);
        if (file == null) {
            directory.add(PsiFileFactory.getInstance(directory.getProject()).createFileFromText(
                    INFO_FILE_NAME, JavaFileType.INSTANCE, doc + "package " + pkg.getQualifiedName() + ";\n"));
            return;
        }
        if (!(file instanceof PsiJavaFile javaFile) || !file.isWritable() || javaFile.getPackageStatement() == null) {
            return;
        }
        var manager = PsiDocumentManager.getInstance(directory.getProject());
        var document = manager.getDocument(file);
        if (document == null || !document.isWritable()) {
            return;
        }
        var statement = javaFile.getPackageStatement();
        PsiDocComment old = PsiTreeUtil.findChildOfType(statement, PsiDocComment.class);
        if (old == null && PsiTreeUtil.skipWhitespacesBackward(statement) instanceof PsiDocComment previous
                && document.getLineNumber(statement.getTextRange().getStartOffset())
                - document.getLineNumber(previous.getTextRange().getEndOffset()) <= 1) {
            old = previous;
        }
        if (old == null) {
            document.insertString(statement.getTextRange().getStartOffset(), doc);
        } else if (EasyDocConfig.COVER_MODE_FORCE.equals(mode)) {
            var range = old.getTextRange();
            document.replaceString(range.getStartOffset(), range.getEndOffset(), doc.stripTrailing());
        } else if (EasyDocConfig.COVER_MODE_MERGE.equals(mode) && WriterService.isBlockComment(old.getText())
                && !old.getText().contains(brief)) {
            var range = old.getTextRange();
            int end = range.getEndOffset() - 2;
            document.insertString(end, "\n * " + brief + "\n ");
        }
        manager.commitDocument(document);
    }
}
