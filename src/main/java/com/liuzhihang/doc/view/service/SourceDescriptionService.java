package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocDescription;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.service.gpt.GptService;
import com.liuzhihang.doc.view.service.translator.TranslatorService;
import com.liuzhihang.doc.view.utils.CustomPsiCommentUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class SourceDescriptionService {
    private SourceDescriptionService() {}

    public record Target(String label, DocDescription description, String paramName,
                         SmartPsiElementPointer<PsiJavaDocumentedElement> source) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record Draft(Target target, long stamp, String original) {}

    public record Edit(Draft draft, String description) {}

    public static List<Target> targets(DocView doc) {
        if (doc == null) {
            return List.of();
        }
        List<Target> result = new ArrayList<>();
        add(result, "接口说明", doc.getDescription(), doc.getPsiMethod());
        add(result, "类型说明", DocDescription.EMPTY, doc.getPsiClass());
        if (doc.getReqParamList() != null) {
            doc.getReqParamList().forEach(param -> add(result, "请求参数 · " + param.getName(), param.getDescription(), param.getPsiElement()));
        }
        if (doc.getHeaderList() != null) {
            doc.getHeaderList().forEach(header -> add(result, "请求头 · " + header.getName(), header.getDescription(), header.getPsiElement()));
        }
        if (doc.getType() == FrameworkEnum.DUBBO && doc.getPsiMethod() != null && doc.getReqBody() != null
                && doc.getReqBody().getChildList() != null) {
            for (Body argument : doc.getReqBody().getChildList()) {
                for (PsiParameter parameter : doc.getPsiMethod().getParameterList().getParameters()) {
                    if (parameter.getName().equals(argument.getName())) {
                        add(result, "请求参数 · " + parameter.getName(), argument.getDescription(), parameter);
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
            add(result, path, body.getDescription(), source);
        }
        if (body.getChildList() != null) {
            body.getChildList().forEach(child -> addBody(result, path, child, visited, false));
        }
    }

    private static void add(List<Target> targets, String label, DocDescription description, PsiElement element) {
        Target target = target(label, description, element);
        if (target != null && targets.stream().noneMatch(existing ->
                Objects.equals(target.paramName(), existing.paramName()) && Objects.equals(target.source().getElement(), existing.source().getElement()))) {
            targets.add(target);
        }
    }

    public static Target classTarget(PsiClass type) {
        return type.isValid() ? target("类说明 · " + type.getName(), DocDescription.EMPTY, type) : null;
    }

    public static Target fieldTarget(String label, String displayed, PsiElement element) {
        return fieldDescriptionTarget(label, DocDescription.legacy(displayed), element);
    }

    public static Target fieldDescriptionTarget(String label, DocDescription description, PsiElement element) {
        return element instanceof PsiField || element instanceof PsiRecordComponent ? target(label, description, element) : null;
    }

    private static Target target(String label, DocDescription description, PsiElement element) {
        if (element == null || !element.isValid() || description.supplements().stream()
                .anyMatch(part -> part.source() == DocDescription.Source.PROTO_GETTER)) {
            return null;
        }
        PsiJavaDocumentedElement owner;
        String tag = null;
        if (element instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
            owner = method;
            tag = parameter.getName();
        } else if (element instanceof PsiRecordComponent component) {
            owner = component.getContainingClass();
            tag = component.getName();
        } else if (element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField) {
            owner = (PsiJavaDocumentedElement) element;
        } else {
            return null;
        }
        return writable(owner) && supportsComment(owner, tag) ? new Target(label, description, tag, SmartPointerManager.createPointer(owner)) : null;
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
        if (!writable(owner) || !hasParameter(owner, target.paramName()) || !supportsComment(owner, target.paramName())) {
            return null;
        }
        var comment = owner.getDocComment();
        var ordinary = ordinaryComment(owner, target.paramName());
        String original = ordinary != null ? CustomPsiCommentUtils.ordinaryCommentText(ordinary)
                : target.paramName() == null ? CustomPsiCommentUtils.descriptionBody(comment)
                : CustomPsiCommentUtils.paramText(comment, target.paramName());
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
                只返回当前条目的注释正文。避免推断源码中没有体现的行为。
                校验约束、引用展开内容和 OpenAPI 描述由文档工具处理，请勿把它们复制进正文。
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
        return applyBatch(List.of(new Edit(draft, description)));
    }

    public static boolean applyBatch(List<Edit> edits) {
        // Swing cell commits can run on the EDT without the IDE's implicit write-intent lock.
        return WriteIntentReadAction.compute(() -> applyUnderWriteIntent(edits));
    }

    private static boolean applyUnderWriteIntent(List<Edit> edits) {
        Map<PsiJavaDocumentedElement, List<Edit>> changesByOwner = new LinkedHashMap<>();
        for (Edit edit : edits) {
            var owner = currentOwner(edit.draft());
            if (owner == null || edit.description() == null) {
                return false;
            }
            if (!edit.description().strip().equals(edit.draft().original().strip())) {
                changesByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(edit);
            }
        }
        if (changesByOwner.isEmpty()) {
            return true;
        }
        var project = changesByOwner.keySet().iterator().next().getProject();
        var factory = PsiElementFactory.getInstance(project);
        List<WriterService.JavaChange> changes = new ArrayList<>();
        List<OrdinaryChange> ordinaryChanges = new ArrayList<>();
        for (var entry : changesByOwner.entrySet()) {
            var previous = entry.getKey().getDocComment();
            var comment = previous;
            String result = null;
            List<Edit> group = entry.getValue();
            for (Edit edit : group) {
                String name = edit.draft().target().paramName();
                var ordinary = ordinaryComment(entry.getKey(), name);
                if (ordinary != null) {
                    ordinaryChanges.add(new OrdinaryChange(SmartPointerManager.createPointer(ordinary),
                            safeCommentText(edit.description())));
                } else {
                    String prose = safeProse(edit.description());
                    result = rewrite(comment, name, preserveInlineReferences(comment, name, prose));
                    comment = factory.createDocCommentFromText(result);
                }
            }
            if (result != null) {
                Draft first = group.getFirst().draft();
                changes.add(new WriterService.JavaChange(first.target().source(), first.stamp(), result, 0, previous != null));
            }
        }
        var writer = ApplicationManager.getApplication().getService(WriterService.class);
        if (ordinaryChanges.isEmpty()) {
            writer.applyJavadocs(project, changes);
        } else {
            WriteCommandAction.writeCommandAction(project).withName("更新源码注释").run(() -> {
                writer.applyJavadocs(project, changes);
                for (var change : ordinaryChanges) {
                    var original = change.comment().getElement();
                    if (original != null) {
                        String text = original.getText().startsWith("//") ? "// " + change.prose()
                                : "/* " + change.prose() + " */";
                        original.replace(PsiParserFacade.getInstance(project)
                                .createLineOrBlockCommentFromText(original.getLanguage(), text));
                    }
                }
            });
        }
        return true;
    }

    private record OrdinaryChange(SmartPsiElementPointer<PsiComment> comment, String prose) {}

    private static String preserveInlineReferences(PsiDocComment comment, String param, String prose) {
        if (comment == null) {
            return prose;
        }
        var references = new ArrayList<PsiInlineDocTag>();
        if (param == null) {
            for (var element : comment.getDescriptionElements()) {
                if (element instanceof PsiInlineDocTag tag) {
                    references.add(tag);
                } else {
                    references.addAll(PsiTreeUtil.findChildrenOfType(element, PsiInlineDocTag.class));
                }
            }
        } else {
            for (var tag : comment.findTagsByName("param")) {
                if (matches(tag, param)) {
                    references.addAll(PsiTreeUtil.findChildrenOfType(tag, PsiInlineDocTag.class));
                }
            }
        }
        for (var reference : references) {
            String name = reference.getName();
            String text = safeProse(reference.getText());
            if (Set.of("link", "linkplain", "inheritDoc").contains(name) && !prose.contains(text)) {
                prose = prose.isEmpty() ? text : prose + " " + text;
            }
        }
        return prose;
    }

    private static String safeProse(String description) {
        String prose = safeCommentText(description);
        return prose.startsWith("@") ? "&#64;" + prose.substring(1) : prose;
    }

    private static String safeCommentText(String description) {
        return WriterService.escapeJavaUnicodeSequences(description.strip().replaceAll("\\s+", " ").replace("*/", "*&#47;"));
    }

    private static PsiJavaDocumentedElement currentOwner(Draft draft) {
        var owner = draft.target().source().getElement();
        return writable(owner) && hasParameter(owner, draft.target().paramName())
                && supportsComment(owner, draft.target().paramName()) && WriterService.modificationStamp(owner.getContainingFile()) == draft.stamp() ? owner : null;
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

    private static PsiElement ordinaryCommentDeclaration(PsiJavaDocumentedElement owner, String param) {
        if (owner instanceof PsiField field && field.getDocComment() == null) {
            return field;
        }
        if (owner instanceof PsiClass type && param != null) {
            for (var component : type.getRecordComponents()) {
                if (param.equals(component.getName())) {
                    return component;
                }
            }
        }
        return null;
    }

    private static PsiComment ordinaryComment(PsiJavaDocumentedElement owner, String param) {
        var declaration = ordinaryCommentDeclaration(owner, param);
        return declaration == null ? null : CustomPsiCommentUtils.ordinaryDeclarationComment(declaration);
    }

    private static boolean supportsComment(PsiJavaDocumentedElement owner, String param) {
        var declaration = ordinaryCommentDeclaration(owner, param);
        if (declaration == null || CustomPsiCommentUtils.ordinaryDeclarationComment(declaration) != null) {
            return true;
        }
        for (var child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiComment) {
                return false;
            }
        }
        return true;
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
