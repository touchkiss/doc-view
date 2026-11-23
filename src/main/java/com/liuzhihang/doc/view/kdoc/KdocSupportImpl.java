package com.liuzhihang.doc.view.kdoc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.kdoc.service.KdocGeneratorServiceImpl;
import com.liuzhihang.doc.view.service.KdocSupport;
import com.liuzhihang.doc.view.service.WriterService;
import org.jetbrains.kotlin.kdoc.psi.api.KDoc;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtObjectDeclaration;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtPsiFactoryKt;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class KdocSupportImpl implements KdocSupport {
    private static final Pattern TAG_LINE = Pattern.compile("\\s*\\*\\s*(@(\\w+)(?:\\s+(\\S+))?.*)");
    private static final Set<String> NAMED_TAGS = Set.of("param", "property", "throws", "exception", "see", "sample");

    @Override
    public boolean supports(PsiElement context) {
        return declaration(context) != null;
    }

    private record Pending(SmartPsiElementPointer<KtDeclaration> target, long stamp, String previous,
                           Supplier<String> generation) {}

    @Override
    public Runnable prepare(PsiElement context, String mode) {
        var pending = ReadAction.compute(() -> {
            var target = declaration(context);
            if (target == null || !target.isValid() || !target.isWritable()) {
                return null;
            }
            KDoc old = target.getDocComment();
            if (old != null && EasyDocConfig.COVER_MODE_IGNORE.equals(mode)) {
                return null;
            }
            var generator = ApplicationManager.getApplication().getService(KdocGeneratorServiceImpl.class);
            return new Pending(SmartPointerManager.createPointer(target),
                    WriterService.modificationStamp(target.getContainingFile()),
                    old == null ? null : old.getText(), generator.prepare(target));
        });
        if (pending == null) {
            return null;
        }
        String generated = pending.generation().get().strip();
        if (!WriterService.isBlockComment(generated)) {
            return null;
        }
        boolean merge = pending.previous() != null && EasyDocConfig.COVER_MODE_MERGE.equals(mode);
        String text = merge ? merge(pending.previous(), generated) : generated;
        if (text.equals(pending.previous())) {
            return null;
        }
        var project = pending.target().getProject();
        return () -> WriteCommandAction.writeCommandAction(project).withName("生成 KDoc").run(() -> {
            var current = pending.target().getElement();
            if (current == null || !current.isWritable()
                    || pending.stamp() != WriterService.modificationStamp(current.getContainingFile())) {
                return;
            }
            var comment = KtPsiFactoryKt.KtPsiFactory(project).createComment(text);
            if (!(comment instanceof KDoc)) {
                return;
            }
            var existing = current.getDocComment();
            var inserted = existing == null ? current.addBefore(comment, current.getFirstChild()) : existing.replace(comment);
            if (!merge) {
                if (!(inserted.getNextSibling() instanceof PsiWhiteSpace)) {
                    current.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"), inserted);
                }
                var range = inserted.getTextRange();
                CodeStyleManager.getInstance(project).reformatText(
                        current.getContainingFile(), range.getStartOffset(), range.getEndOffset() + 1);
            }
        });
    }

    private static KtDeclaration declaration(PsiElement context) {
        for (PsiElement element = context; element != null; element = element.getParent()) {
            if (element instanceof KtClass || element instanceof KtObjectDeclaration
                    || element instanceof KtNamedFunction || element instanceof KtProperty) {
                return (KtDeclaration) element;
            }
        }
        return null;
    }

    private static String merge(String old, String generated) {
        Set<String> keys = new HashSet<>();
        old.lines().forEach(line -> {
            var tag = TAG_LINE.matcher(line);
            if (tag.matches()) {
                keys.add(key(tag.group(2), tag.group(3)));
            }
        });
        StringBuilder appended = new StringBuilder();
        generated.lines().forEach(line -> {
            var tag = TAG_LINE.matcher(line);
            if (tag.matches() && keys.add(key(tag.group(2), tag.group(3)))) {
                appended.append("\n * ").append(tag.group(1));
            }
        });
        return appended.isEmpty() ? old : old.substring(0, old.length() - 2).stripTrailing() + appended + "\n */";
    }

    private static String key(String name, String value) {
        return NAMED_TAGS.contains(name) && value != null ? name + " " + value.replace("[", "").replace("]", "") : name;
    }
}
