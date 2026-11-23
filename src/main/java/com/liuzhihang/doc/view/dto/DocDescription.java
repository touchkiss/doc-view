package com.liuzhihang.doc.view.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Local source text and the ordered contributions to the published description. */
public record DocDescription(String sourceComment, String renderedComment, List<Supplement> supplements, String draftText) {
    public static final DocDescription EMPTY = local("", "");

    public enum Source {
        VALIDATION("校验约束"),
        SPRING_DEFAULT("Spring 默认值"),
        ENUM_REFERENCE("枚举引用"),
        OPEN_API("OpenAPI"),
        DEPENDENCY("依赖源码"),
        GENERATED("生成说明"),
        PROTO_GETTER("Proto getter");

        private final String label;

        Source(String label) {
            this.label = label;
        }
    }

    public record Supplement(Source source, String text, boolean rendered) {}

    public DocDescription {
        supplements = List.copyOf(supplements);
    }

    public static DocDescription local(String comment, String renderedComment) {
        return new DocDescription(comment, renderedComment, List.of(), null);
    }

    public static DocDescription legacy(String description) {
        return local("", description);
    }

    public DocDescription with(Source source, String text) {
        return with(source, text, true);
    }

    public DocDescription with(Source source, String text, boolean rendered) {
        if (text == null || (text.isBlank() && source != Source.OPEN_API && source != Source.PROTO_GETTER)) {
            return this;
        }
        var parts = new ArrayList<>(supplements);
        parts.add(new Supplement(source, text, rendered));
        return new DocDescription(sourceComment, renderedComment, parts, draftText);
    }

    public DocDescription withDraft(String text) {
        return new DocDescription(sourceComment, renderedComment, supplements, text);
    }

    public String editableText() {
        return draftText == null ? sourceComment : draftText;
    }

    public DocDescription orEmpty() {
        return renderedComment == null && supplements.isEmpty() && draftText == null ? EMPTY : this;
    }

    public String render() {
        String result = draftText == null ? renderedComment : draftText;
        for (var part : supplements) {
            if (!part.rendered()) {
                continue;
            }
            result = switch (part.source()) {
                case OPEN_API, DEPENDENCY, GENERATED, PROTO_GETTER -> draftText == null ? part.text() : result;
                case VALIDATION, SPRING_DEFAULT -> (result == null ? "" : result) + part.text();
                case ENUM_REFERENCE -> result == null || result.isBlank()
                        ? part.text() : result + "(" + part.text() + ")";
            };
        }
        return result;
    }

    public String supplementaryText() {
        return supplements.stream()
                .map(part -> "[" + part.source().label + "] " + part.text().strip())
                .collect(Collectors.joining(" · "));
    }
}
