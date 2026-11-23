package com.liuzhihang.doc.view.proto;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.protobuf.lang.psi.PbDefinition;
import com.intellij.protobuf.lang.psi.PbEnumValue;
import com.intellij.protobuf.lang.psi.PbFile;
import com.intellij.protobuf.lang.psi.PbMapField;
import com.intellij.protobuf.lang.psi.PbNamedElement;
import com.intellij.protobuf.lang.psi.PbServiceMethod;
import com.intellij.protobuf.lang.psi.PbSimpleField;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.service.ProtoCommentSupport;
import com.liuzhihang.doc.view.service.translator.TranslatorService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ProtoCommentSupportImpl implements ProtoCommentSupport {
    private record Pending(int offset, String indentation, boolean inline, Supplier<String> translate) {}
    private record Insertion(int offset, String comment) {}

    @Override
    public boolean supports(PsiElement context) {
        return context != null && context.getContainingFile() instanceof PbFile;
    }

    @Override
    public void generate(PsiElement context, List<Integer> offsets, boolean allMembers) {
        if (!supports(context)) {
            return;
        }
        var project = context.getProject();
        var file = context.getContainingFile();
        var documents = PsiDocumentManager.getInstance(project);
        Document document = documents.getDocument(file);
        if (document == null || !document.isWritable()) {
            return;
        }
        long stamp = document.getModificationStamp();
        Set<PbNamedElement> targets = new LinkedHashSet<>();
        if (allMembers) {
            var definition = PsiTreeUtil.getParentOfType(context, PbDefinition.class, false);
            PsiElement container = definition == null ? file : definition;
            targets.addAll(PsiTreeUtil.collectElementsOfType(container, PbSimpleField.class));
            targets.addAll(PsiTreeUtil.collectElementsOfType(container, PbMapField.class));
            targets.addAll(PsiTreeUtil.collectElementsOfType(container, PbServiceMethod.class));
            targets.addAll(PsiTreeUtil.collectElementsOfType(container, PbEnumValue.class));
        } else if (offsets.isEmpty()) {
            addTarget(targets, context);
        } else {
            for (int offset : offsets) {
                addTarget(targets, file.findElementAt(offset));
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        var translator = ApplicationManager.getApplication().getService(TranslatorService.class);
        new Task.Backgroundable(project, "生成 Proto 注释", true) {
            private final List<Insertion> insertions = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                var pending = ReadAction.compute(() -> {
                    if (document.getModificationStamp() != stamp || !file.isValid()) {
                        return List.<Pending>of();
                    }
                    List<Pending> result = new ArrayList<>();
                    for (var target : targets) {
                        indicator.checkCanceled();
                        if (!target.isValid() || target.getName() == null || hasLeadingComment(target, document)) {
                            continue;
                        }
                        int start = target.getTextRange().getStartOffset();
                        int lineStart = document.getLineStartOffset(document.getLineNumber(start));
                        String prefix = document.getText(TextRange.create(lineStart, start));
                        boolean inline = !prefix.isBlank();
                        result.add(new Pending(inline ? start : lineStart, inline ? "" : prefix, inline,
                                translator.prepareTranslation(target.getName(), target)));
                    }
                    return result;
                });
                for (var entry : pending) {
                    indicator.checkCanceled();
                    String description = entry.translate().get().replace("*/", "* /").replaceAll("\\R+", " ").strip();
                    if (!description.isBlank()) {
                        String indent = entry.indentation();
                        String comment = entry.inline() ? "/* " + description + " */ "
                                : indent + "/*\n" + indent + " * " + description + "\n" + indent + " */\n";
                        insertions.add(new Insertion(entry.offset(), comment));
                    }
                }
                indicator.checkCanceled();
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (document.getModificationStamp() != stamp) {
                    StatusBar.Info.set("文件在生成期间已修改，本次注释未写入。", project);
                    return;
                }
                WriteCommandAction.writeCommandAction(project, file).withName("生成 Proto 注释").run(() -> {
                    if (!file.isValid() || !document.isWritable() || document.getModificationStamp() != stamp) {
                        return;
                    }
                    insertions.sort(Comparator.comparingInt(Insertion::offset).reversed());
                    for (var insertion : insertions) {
                        document.insertString(insertion.offset(), insertion.comment());
                    }
                    documents.commitDocument(document);
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                if (!project.isDisposed()) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(project, "请检查翻译配置。", "生成 Proto 注释失败");
                }
            }
        }.queue();
    }

    private static void addTarget(Set<PbNamedElement> targets, PsiElement context) {
        var target = PsiTreeUtil.getParentOfType(context, PbNamedElement.class, false);
        if (target != null) {
            targets.add(target);
        }
    }

    private static boolean hasLeadingComment(PbNamedElement target, Document document) {
        if (!(PsiTreeUtil.skipWhitespacesBackward(target) instanceof PsiComment comment)) {
            return false;
        }
        int commentLine = document.getLineNumber(comment.getTextRange().getEndOffset());
        int targetLine = document.getLineNumber(target.getTextRange().getStartOffset());
        return targetLine - commentLine <= 1;
    }
}
