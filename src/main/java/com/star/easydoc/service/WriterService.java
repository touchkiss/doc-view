package com.star.easydoc.service;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.star.easydoc.config.EasyDocConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class WriterService {
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\(?=u+[0-9a-fA-F]{4})");

    public static long modificationStamp(PsiFile file) {
        var document = PsiDocumentManager.getInstance(file.getProject()).getCachedDocument(file);
        return document == null ? file.getModificationStamp() : document.getModificationStamp();
    }

    public static String escapeJavaUnicodeSequences(String text) {
        return UNICODE_ESCAPE.matcher(text).replaceAll("&#92;");
    }

    public static PsiField declarationOwner(PsiField field) {
        var type = field.getTypeElement();
        return type != null && type.getParent() instanceof PsiField owner ? owner : field;
    }

    public static boolean canWriteJavadoc(PsiJavaDocumentedElement target) {
        // Implicit record members can report writable even though they have no source declaration.
        return target != null && target.isValid() && target.isPhysical() && target.isWritable()
                && target.getNode() != null && target.getContainingFile() instanceof PsiJavaFile
                && (!(target instanceof PsiField field) || declarationOwner(field) == field);
    }

    public record JavaChange(SmartPsiElementPointer<PsiJavaDocumentedElement> target, long stamp, String text,
                             int trailingLines, boolean merge) {}

    public JavaChange prepareJavadoc(PsiJavaDocumentedElement target, String generated, String mode) {
        if (generated == null || generated.isBlank() || !canWriteJavadoc(target)) {
            return null;
        }
        String text = escapeJavaUnicodeSequences(generated.strip());
        if (!isBlockComment(text)) {
            throw new IllegalArgumentException("生成结果必须是完整的 Javadoc 注释。");
        }
        PsiElementFactory factory = PsiElementFactory.getInstance(target.getProject());
        PsiDocComment old = target.getDocComment();
        boolean merging = old != null && EasyDocConfig.COVER_MODE_MERGE.equals(mode);
        if (old != null && !EasyDocConfig.COVER_MODE_FORCE.equals(mode)) {
            if (!EasyDocConfig.COVER_MODE_MERGE.equals(mode) || !isBlockComment(old.getText())) {
                return null;
            }
            text = merge(old, factory.createDocCommentFromText(text));
            if (text.equals(old.getText())) {
                return null;
            }
        }
        return new JavaChange(SmartPointerManager.createPointer(target), modificationStamp(target.getContainingFile()),
                text, Math.min(10, com.star.easydoc.common.util.StringUtil.endCount(generated, '\n')), merging);
    }

    public void applyJavadocs(Project project, List<JavaChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        WriteCommandAction.writeCommandAction(project).withName("生成 Javadoc").run(() -> {
            var targets = changes.stream().filter(change -> {
                var target = change.target().getElement();
                return canWriteJavadoc(target) && modificationStamp(target.getContainingFile()) == change.stamp();
            }).toList();
            PsiElementFactory factory = PsiElementFactory.getInstance(project);
            CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
            for (JavaChange change : targets) {
                var target = change.target().getElement();
                if (!canWriteJavadoc(target)) {
                    continue;
                }
                PsiDocComment comment = factory.createDocCommentFromText(change.text());
                PsiDocComment old = target.getDocComment();
                if (old != null && change.merge()) {
                    // Preserve formatting and tag order of existing documentation when appending missing tags.
                    old.replace(comment);
                    continue;
                }
                PsiDocComment inserted = (PsiDocComment) (old == null ? target.addBefore(comment, target.getFirstChild()) : old.replace(comment));
                var parser = PsiParserFacade.getInstance(project);
                if (!(inserted.getNextSibling() instanceof PsiWhiteSpace)) {
                    target.addAfter(parser.createWhiteSpaceFromText("\n"), inserted);
                }
                var range = inserted.getTextRange();
                codeStyle.reformatText(target.getContainingFile(), range.getStartOffset(), range.getEndOffset() + 1);
                if (change.trailingLines() > 0 && target.getDocComment() != null
                        && target.getDocComment().getNextSibling() instanceof PsiWhiteSpace whitespace) {
                    String gap = whitespace.getText();
                    String indent = gap.substring(gap.lastIndexOf('\n') + 1);
                    whitespace.replace(parser.createWhiteSpaceFromText("\n".repeat(change.trailingLines() + 1) + indent));
                }
            }
        });
    }

    public static boolean isBlockComment(String text) {
        return text.startsWith("/**") && text.indexOf("*/", 2) == text.length() - 2 && text.length() >= 5;
    }

    private static String merge(PsiDocComment old, PsiDocComment generated) {
        Set<String> tags = new HashSet<>();
        for (PsiDocTag tag : old.getTags()) {
            tags.add(tagKey(tag));
        }
        StringBuilder appended = new StringBuilder();
        for (PsiDocTag tag : generated.getTags()) {
            if (tags.add(tagKey(tag))) {
                appended.append("\n * ").append(tag.getText().strip());
            }
        }
        String original = old.getText();
        if (appended.isEmpty()) {
            return original;
        }
        int close = original.length() - 2;
        return original.substring(0, close).stripTrailing() + appended + "\n */";
    }

    private static String tagKey(PsiDocTag tag) {
        String name = tag.getName();
        if (Set.of("param", "throws", "exception", "see").contains(name)) {
            var value = tag.getValueElement();
            if ("exception".equals(name)) {
                name = "throws";
            }
            return name + " " + (value == null ? "" : value.getText());
        }
        return name;
    }

    public void write(Project project, Editor editor, String selection, int start, String text) {
        if (text == null || text.isBlank() || editor.isDisposed() || editor.isViewer()) {
            return;
        }
        var model = editor.getSelectionModel();
        if (model.getSelectionStart() != start || !selection.equals(model.getSelectedText())
                || !EditorModificationUtil.checkModificationAllowed(editor)) {
            return;
        }
        WriteCommandAction.writeCommandAction(project).withName("翻译选区并替换").run(() -> {
            EditorModificationUtil.insertStringAtCaret(editor, text);
            model.setSelection(start, start + text.length());
        });
    }
}
