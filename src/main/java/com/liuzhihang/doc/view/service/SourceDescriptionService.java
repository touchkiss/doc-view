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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocDescription;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
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
        String original;
        if (ordinary != null) {
            original = CustomPsiCommentUtils.ordinaryCommentText(ordinary);
        } else if (target.paramName() == null) {
            original = CustomPsiCommentUtils.descriptionBody(comment);
        } else {
            original = CustomPsiCommentUtils.paramText(comment, target.paramName());
        }
        return new Draft(target, WriterService.modificationStamp(owner.getContainingFile()), original);
    }

    public static Supplier<String> prepareGeneration(Draft draft, String instructions) {
        var owner = currentOwner(draft);
        return owner == null ? null : SourceDescriptionGenerator.prepare(draft, owner, instructions);
    }

    public static boolean usesModel() {
        return SourceDescriptionGenerator.usesModel();
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
            prepareOwnerChanges(entry.getKey(), entry.getValue(), factory, changes, ordinaryChanges);
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

    private static void prepareOwnerChanges(PsiJavaDocumentedElement owner, List<Edit> group,
                                            PsiElementFactory factory, List<WriterService.JavaChange> changes,
                                            List<OrdinaryChange> ordinaryChanges) {
        var previous = owner.getDocComment();
        var comment = previous;
        String result = null;
        for (Edit edit : group) {
            String name = edit.draft().target().paramName();
            var ordinary = ordinaryComment(owner, name);
            if (ordinary != null) {
                ordinaryChanges.add(new OrdinaryChange(SmartPointerManager.createPointer(ordinary),
                        JavaDescriptionText.safeCommentText(edit.description())));
            } else {
                result = JavaDescriptionText.rewrite(comment, name, edit.description());
                comment = factory.createDocCommentFromText(result);
            }
        }
        if (result != null) {
            Draft first = group.getFirst().draft();
            changes.add(new WriterService.JavaChange(first.target().source(), first.stamp(), result, 0, previous != null));
        }
    }

    private record OrdinaryChange(SmartPsiElementPointer<PsiComment> comment, String prose) {}

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
}
