package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.star.easydoc.common.Consts;
import com.star.easydoc.config.EasyDocConfigComponent;
import com.star.easydoc.service.WriterService;
import com.star.easydoc.service.gpt.GptService;
import com.star.easydoc.service.translator.TranslatorService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class SourceDescriptionService {
    private SourceDescriptionService() {}

    public record Target(String label, String displayed, String paramName,
                         SmartPsiElementPointer<PsiJavaDocumentedElement> source) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record Draft(Target target, long stamp, String original) {}

    public static List<Target> targets(DocView doc) {
        if (doc == null) {
            return List.of();
        }
        List<Target> result = new ArrayList<>();
        add(result, "接口说明", doc.getDesc(), doc.getPsiMethod());
        add(result, "类型说明", doc.getDocTitle(), doc.getPsiClass());
        if (doc.getReqParamList() != null) {
            doc.getReqParamList().forEach(param -> add(result, "请求参数 · " + param.getName(), param.getDesc(), param.getPsiElement()));
        }
        if (doc.getHeaderList() != null) {
            doc.getHeaderList().forEach(header -> add(result, "请求头 · " + header.getName(), header.getDesc(), header.getPsiElement()));
        }
        if (doc.getType() == FrameworkEnum.DUBBO && doc.getPsiMethod() != null && doc.getReqBody() != null
                && doc.getReqBody().getChildList() != null) {
            for (Body argument : doc.getReqBody().getChildList()) {
                for (PsiParameter parameter : doc.getPsiMethod().getParameterList().getParameters()) {
                    if (parameter.getName().equals(argument.getName())) {
                        add(result, "请求参数 · " + parameter.getName(), argument.getDesc(), parameter);
                        break;
                    }
                }
            }
        }
        addBody(result, "请求体", doc.getReqBody(), Collections.newSetFromMap(new IdentityHashMap<>()), true);
        addBody(result, "响应体", doc.getRespBody(), Collections.newSetFromMap(new IdentityHashMap<>()), true);
        return List.copyOf(result);
    }

    private static void addBody(List<Target> result, String label, Body body, Set<Body> visited, boolean root) {
        if (body == null || !visited.add(body)) {
            return;
        }
        String path = root || body.getName() == null ? label : label + " · " + body.getName();
        var source = body.getPsiElement();
        if (!body.isSyntheticContainerNode()
                && (source instanceof PsiField || source instanceof PsiRecordComponent || source instanceof PsiParameter)) {
            add(result, path, body.getDesc(), source);
        }
        if (body.getChildList() != null) {
            body.getChildList().forEach(child -> addBody(result, path, child, visited, false));
        }
    }

    private static void add(List<Target> targets, String label, String displayed, PsiElement element) {
        if (element == null || !element.isValid()) {
            return;
        }
        PsiJavaDocumentedElement owner;
        String tag = null;
        if (element instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
            owner = method;
            tag = parameter.getName();
        } else if (element instanceof PsiRecordComponent component) {
            owner = component.getContainingClass();
            tag = component.getName();
        } else if (element instanceof PsiField field) {
            // A shared Java declaration has only one Javadoc. Expose its first field.
            owner = field;
        } else if (element instanceof PsiClass || element instanceof PsiMethod) {
            owner = (PsiJavaDocumentedElement) element;
        } else {
            return;
        }
        if (!writable(owner)) {
            return;
        }
        String paramName = tag;
        if (targets.stream().noneMatch(target -> Objects.equals(paramName, target.paramName())
                && owner.equals(target.source().getElement()))) {
            targets.add(new Target(label, displayed, tag, SmartPointerManager.createPointer(owner)));
        }
    }

    private static boolean writable(PsiJavaDocumentedElement owner) {
        if (!WriterService.canWriteJavadoc(owner)) {
            return false;
        }
        var file = owner.getContainingFile().getVirtualFile();
        if (file == null || !ProjectFileIndex.getInstance(owner.getProject()).isInContent(file)) {
            return false;
        }
        var existing = owner.getDocComment();
        return existing == null || WriterService.isBlockComment(existing.getText());
    }

    public static Draft capture(Target target) {
        var owner = target.source().getElement();
        if (!writable(owner) || !hasParameter(owner, target.paramName())) {
            return null;
        }
        var comment = owner.getDocComment();
        String original = target.paramName() == null
                ? descriptionBody(comment) : paramText(comment, target.paramName());
        return new Draft(target, WriterService.modificationStamp(owner.getContainingFile()), original);
    }

    public static Supplier<String> prepareGeneration(Draft draft, String instructions) {
        var owner = currentOwner(draft);
        if (owner == null) {
            return null;
        }
        var app = ApplicationManager.getApplication();
        String provider = app.getService(EasyDocConfigComponent.class).getState().getTranslator();
        String name = draft.target().paramName() != null ? draft.target().paramName()
                : owner instanceof PsiNamedElement named ? named.getName() : "";
        if (!Consts.AI_TRANSLATOR.contains(provider)) {
            return app.getService(TranslatorService.class).prepareTranslation(name == null ? "" : name, owner);
        }

        var model = app.getService(GptService.class);
        String source = owner.getText();
        var existing = owner.getDocComment();
        if (existing != null && source.startsWith(existing.getText())) {
            source = source.substring(existing.getTextLength()).stripLeading();
        }
        if (owner instanceof PsiClass && source.contains("{")) {
            source = source.substring(0, source.indexOf('{')).stripTrailing();
        }
        String prompt = model.withProjectTerms(owner, source, """
                根据下面的 Java 声明，为选中的条目写一段准确、简短的中文说明。
                只返回说明文本。避免推断源码中没有体现的行为。
                条目：%s（%s）
                当前说明：%s
                补充要求：%s
                声明：
                %s
                """.formatted(draft.target().label(), name, draft.original(), instructions, source));
        return () -> descriptionText(model.chat(provider, prompt));
    }

    private static String descriptionText(String response) {
        String text = response == null ? "" : response.strip();
        if (text.startsWith("```") && text.endsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine >= 0 && firstLine < text.length() - 3) {
                text = text.substring(firstLine + 1, text.length() - 3).strip();
            }
        }
        if (WriterService.isBlockComment(text)) {
            return text.substring(3, text.length() - 2).lines().map(String::strip)
                    .map(line -> line.startsWith("*") ? line.substring(1).strip() : line)
                    .filter(line -> !line.startsWith("@")).collect(Collectors.joining(" ")).strip();
        }
        return text;
    }

    public static boolean usesModel() {
        var settings = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
        return Consts.AI_TRANSLATOR.contains(settings.getTranslator());
    }

    public static boolean apply(Draft draft, String description) {
        var owner = currentOwner(draft);
        if (owner == null || description == null || description.isBlank()) {
            return false;
        }
        String prose = WriterService.escapeJavaUnicodeSequences(description.strip().replaceAll("\\s+", " ").replace("*/", "*&#47;"));
        if (prose.startsWith("@")) {
            prose = "&#64;" + prose.substring(1);
        }
        PsiDocComment previous = owner.getDocComment();
        String text = rewrite(previous, draft.target().paramName(), prose);
        var app = ApplicationManager.getApplication();
        app.getService(WriterService.class).applyJavadocs(owner.getProject(), List.of(
                new WriterService.JavaChange(draft.target().source(), draft.stamp(), text, 0, previous != null)));
        return true;
    }

    private static PsiJavaDocumentedElement currentOwner(Draft draft) {
        var owner = draft.target().source().getElement();
        return writable(owner) && hasParameter(owner, draft.target().paramName())
                && WriterService.modificationStamp(owner.getContainingFile()) == draft.stamp() ? owner : null;
    }

    private static boolean hasParameter(PsiJavaDocumentedElement owner, String name) {
        if (name == null) {
            return true;
        }
        if (owner instanceof PsiMethod method) {
            return Arrays.stream(method.getParameterList().getParameters()).anyMatch(param -> name.equals(param.getName()));
        }
        return owner instanceof PsiClass type
                && Arrays.stream(type.getRecordComponents()).anyMatch(component -> name.equals(component.getName()));
    }

    private static String descriptionBody(PsiDocComment comment) {
        if (comment == null) {
            return "";
        }
        String text = comment.getText();
        var tags = comment.getTags();
        int end = tags.length == 0 ? text.length() - 2
                : tags[0].getTextRange().getStartOffset() - comment.getTextRange().getStartOffset();
        return text.substring(3, end).lines().map(String::strip)
                .map(line -> line.startsWith("*") ? line.substring(1).strip() : line)
                .collect(Collectors.joining("\n")).strip();
    }

    private static String paramText(PsiDocComment comment, String name) {
        if (comment != null) {
            for (PsiDocTag tag : comment.findTagsByName("param")) {
                if (matches(tag, name)) {
                    return Arrays.stream(tag.getDataElements()).skip(1).map(PsiElement::getText)
                            .collect(Collectors.joining(" ")).strip();
                }
            }
        }
        return "";
    }

    private static boolean matches(PsiDocTag tag, String name) {
        return "param".equals(tag.getName()) && tag.getValueElement() != null
                && name.equals(tag.getValueElement().getText());
    }

    private static String rewrite(PsiDocComment previous, String param, String prose) {
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
