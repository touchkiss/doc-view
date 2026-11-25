package com.liuzhihang.doc.view.service;

import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Set;

final class JavaDescriptionText {
    private JavaDescriptionText() {}

    private static String preserveInlineReferences(PsiDocComment comment, String param, String prose) {
        if (comment == null) {
            return prose;
        }
        var references = inlineReferences(comment, param);
        for (var reference : references) {
            String name = reference.getName();
            String text = safeProse(reference.getText());
            if (Set.of("link", "linkplain", "inheritDoc").contains(name) && !prose.contains(text)) {
                prose = prose.isEmpty() ? text : prose + " " + text;
            }
        }
        return prose;
    }

    private static ArrayList<PsiInlineDocTag> inlineReferences(PsiDocComment comment, String param) {
        var references = new ArrayList<PsiInlineDocTag>();
        if (param != null) {
            for (var tag : comment.findTagsByName("param")) {
                if (matches(tag, param)) {
                    references.addAll(PsiTreeUtil.findChildrenOfType(tag, PsiInlineDocTag.class));
                }
            }
            return references;
        }
        for (var element : comment.getDescriptionElements()) {
            if (element instanceof PsiInlineDocTag tag) {
                references.add(tag);
            } else {
                references.addAll(PsiTreeUtil.findChildrenOfType(element, PsiInlineDocTag.class));
            }
        }
        return references;
    }

    private static String safeProse(String description) {
        String prose = safeCommentText(description);
        return prose.startsWith("@") ? "&#64;" + prose.substring(1) : prose;
    }

    static String safeCommentText(String description) {
        return WriterService.escapeJavaUnicodeSequences(description.strip().replaceAll("\\s+", " ").replace("*/", "*&#47;"));
    }

    private static boolean matches(PsiDocTag tag, String name) {
        return "param".equals(tag.getName()) && tag.getValueElement() != null
                && name.equals(tag.getValueElement().getText());
    }

    static String rewrite(PsiDocComment previous, String param, String description) {
        String prose = preserveInlineReferences(previous, param, safeProse(description));
        String text = previous == null ? "/** */" : previous.getText();
        var tags = previous == null ? new PsiDocTag[0] : previous.getTags();
        int offset = previous == null ? 0 : previous.getTextRange().getStartOffset();
        int closing = text.length() - 2;
        if (param == null) {
            int start = tags.length == 0 ? closing : tags[0].getTextRange().getStartOffset() - offset;
            String suffix = suffix(text, start, tags.length == 0 ? " " : " * ");
            return "/**\n * " + prose + "\n" + suffix;
        }

        String replacement = "@param " + param + " " + prose;
        for (int i = 0; i < tags.length; i++) {
            if (matches(tags[i], param)) {
                int start = tags[i].getTextRange().getStartOffset() - offset;
                int end = i + 1 == tags.length ? closing : tags[i + 1].getTextRange().getStartOffset() - offset;
                return text.substring(0, start) + replacement + "\n"
                        + suffix(text, end, end == closing ? " " : " * ");
            }
        }
        int line = standaloneLineStart(text, closing);
        return text.substring(0, line).stripTrailing() + "\n * " + replacement + "\n" + suffix(text, closing, " ");
    }

    private static String suffix(String text, int offset, String prefix) {
        int line = standaloneLineStart(text, offset);
        return line == offset ? prefix + text.substring(offset) : text.substring(line);
    }

    private static int standaloneLineStart(String text, int offset) {
        int start = text.lastIndexOf('\n', offset - 1) + 1;
        String prefix = text.substring(start, offset).strip();
        return start > 0 && (prefix.isEmpty() || "*".equals(prefix)) ? start : offset;
    }
}
