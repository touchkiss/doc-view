package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiNamedElement;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.service.gpt.GptService;
import com.liuzhihang.doc.view.service.translator.TranslatorService;

import java.util.function.Supplier;
import java.util.stream.Collectors;

final class SourceDescriptionGenerator {
    private static final char CLASS_BODY_START = '{';
    private static final String CODE_FENCE = "```";

    private SourceDescriptionGenerator() {}

    static Supplier<String> prepare(SourceDescriptionService.Draft draft, PsiJavaDocumentedElement owner, String instructions) {
        var app = ApplicationManager.getApplication();
        String provider = app.getService(EasyDocConfigComponent.class).getState().getTranslator();
        String name = draft.target().paramName();
        if (name == null) {
            name = owner instanceof PsiNamedElement named ? named.getName() : "";
        }
        if (!Consts.AI_TRANSLATOR.contains(provider)) {
            return app.getService(TranslatorService.class).prepareTranslation(name == null ? "" : name, owner);
        }

        var model = app.getService(GptService.class);
        String source = owner.getText();
        var existing = owner.getDocComment();
        if (existing != null && source.startsWith(existing.getText())) {
            source = source.substring(existing.getTextLength()).stripLeading();
        }
        if (owner instanceof PsiClass && source.indexOf(CLASS_BODY_START) >= 0) {
            source = source.substring(0, source.indexOf(CLASS_BODY_START)).stripTrailing();
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
        if (text.startsWith(CODE_FENCE) && text.endsWith(CODE_FENCE)) {
            int firstLine = text.indexOf('\n');
            if (firstLine >= 0 && firstLine < text.length() - CODE_FENCE.length()) {
                text = text.substring(firstLine + 1, text.length() - CODE_FENCE.length()).strip();
            }
        }
        if (WriterService.isBlockComment(text)) {
            return text.substring(3, text.length() - 2).lines().map(String::strip)
                    .map(line -> line.startsWith("*") ? line.substring(1).strip() : line)
                    .filter(line -> !line.startsWith("@")).collect(Collectors.joining(" ")).strip();
        }
        return text;
    }

    static boolean usesModel() {
        var settings = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
        return Consts.AI_TRANSLATOR.contains(settings.getTranslator());
    }
}
