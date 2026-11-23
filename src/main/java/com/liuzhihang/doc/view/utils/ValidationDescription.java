package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.liuzhihang.doc.view.constant.ValidationConstant;
import com.liuzhihang.doc.view.dto.DocDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

/** Describes Bean Validation declarations without inferring API requiredness. */
record ValidationDescription(String constraints, String presenceConstraints) {
    private record Rule(String[] annotationNames, Function<PsiAnnotation, String> describe, boolean presence) {}

    private static final List<Rule> RULES = List.of(
            rule(ValidationConstant.MIN, annotation -> numericBound(annotation, "最小值")),
            rule(ValidationConstant.MAX, annotation -> numericBound(annotation, "最大值")),
            rule(ValidationConstant.SIZE, ValidationDescription::size),
            rule(ValidationConstant.ASSERT_FALSE, "必须为 false"),
            rule(ValidationConstant.ASSERT_TRUE, "必须为 true"),
            rule(ValidationConstant.DECIMAL_MAX, annotation -> decimalBound(annotation, "≤", "<")),
            rule(ValidationConstant.DECIMAL_MIN, annotation -> decimalBound(annotation, "≥", ">")),
            rule(ValidationConstant.DIGITS, ValidationDescription::digits),
            rule(ValidationConstant.EMAIL, ValidationDescription::email),
            rule(ValidationConstant.FUTURE, "晚于当前时间"),
            rule(ValidationConstant.FUTURE_OR_PRESENT, "不早于当前时间"),
            rule(ValidationConstant.NEGATIVE, "< 0"),
            rule(ValidationConstant.NEGATIVE_OR_ZERO, "≤ 0"),
            new Rule(ValidationConstant.NOT_BLANK, ignored -> "不能为 null 或空白", true),
            new Rule(ValidationConstant.NOT_EMPTY, ignored -> "不能为 null 或空", true),
            new Rule(ValidationConstant.NOT_NULL, ignored -> "不能为 null", true),
            rule(ValidationConstant.NULL, "必须为 null"),
            rule(ValidationConstant.PAST, "早于当前时间"),
            rule(ValidationConstant.PAST_OR_PRESENT, "不晚于当前时间"),
            rule(ValidationConstant.PATTERN, ValidationDescription::pattern),
            rule(ValidationConstant.POSITIVE, "> 0"),
            rule(ValidationConstant.POSITIVE_OR_ZERO, "≥ 0")
    );

    static ValidationDescription extract(PsiModifierListOwner owner) {
        var rootAnnotations = new LinkedHashSet<>(List.of(owner.getAnnotations()));
        if (owner instanceof PsiVariable variable) {
            Collections.addAll(rootAnnotations, variable.getType().getAnnotations());
        }
        Map<String, List<PsiAnnotation>> annotations = new HashMap<>();
        for (var annotation : rootAnnotations) {
            collect(annotation, annotations);
        }
        if (annotations.isEmpty()) {
            return new ValidationDescription("", "");
        }

        StringBuilder constraints = new StringBuilder();
        StringBuilder presenceConstraints = new StringBuilder();
        for (Rule rule : RULES) {
            for (String name : rule.annotationNames()) {
                for (var annotation : annotations.getOrDefault(name, List.of())) {
                    String text = rule.describe().apply(annotation);
                    if (text != null) {
                        var target = rule.presence() ? presenceConstraints : constraints;
                        if (target.isEmpty()) {
                            target.append(' ');
                        }
                        target.append(text);
                        String groups = attributeNames(annotation, "groups");
                        if (!groups.isEmpty()) {
                            target.append("（分组 ").append(groups).append('）');
                        }
                        target.append(';');
                    }
                }
            }
        }
        return new ValidationDescription(constraints.toString(), presenceConstraints.toString());
    }

    DocDescription appendTo(DocDescription description, boolean renderInDocument) {
        return description.with(DocDescription.Source.VALIDATION, constraints, renderInDocument)
                .with(DocDescription.Source.VALIDATION, presenceConstraints, false);
    }

    private static Rule rule(String[] names, String description) {
        return rule(names, ignored -> description);
    }

    private static Rule rule(String[] names, Function<PsiAnnotation, String> describe) {
        return new Rule(names, describe, false);
    }

    private static void collect(PsiAnnotation annotation, Map<String, List<PsiAnnotation>> annotations) {
        ProgressManager.checkCanceled();
        String name = annotation.getQualifiedName();
        if (name == null || !isValidationConstraint(name)) {
            return;
        }
        if (name.endsWith(".List")) {
            String constraintName = name.substring(0, name.length() - ".List".length());
            for (var item : attributeItems(annotation, "value")) {
                if (item instanceof PsiAnnotation constraint && constraintName.equals(constraint.getQualifiedName())) {
                    annotations.computeIfAbsent(constraintName, ignored -> new ArrayList<>()).add(constraint);
                }
            }
        } else {
            annotations.computeIfAbsent(name, ignored -> new ArrayList<>()).add(annotation);
        }
    }

    private static boolean isValidationConstraint(String name) {
        for (String namespace : ValidationConstant.CONSTRAINTS) {
            if (name.startsWith(namespace + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String numericBound(PsiAnnotation annotation, String label) {
        var value = numberAttribute(annotation, "value");
        return value == null ? null : label + ":" + value;
    }

    private static String size(PsiAnnotation annotation) {
        var min = numberAttribute(annotation, "min");
        var max = numberAttribute(annotation, "max");
        return min == null || max == null ? null : "长度范围:最小值:" + min + ",最大值:" + max;
    }

    private static String decimalBound(PsiAnnotation annotation, String inclusive, String exclusive) {
        String value = stringAttribute(annotation, "value");
        if (value == null) {
            return null;
        }
        var inclusiveMember = annotation.findDeclaredAttributeValue("inclusive");
        if (inclusiveMember == null) {
            return inclusive + " " + value;
        }
        return PsiConstantUtils.constantValue(inclusiveMember) instanceof Boolean includeBound
                ? (includeBound ? inclusive : exclusive) + " " + value : null;
    }

    private static String digits(PsiAnnotation annotation) {
        var integer = numberAttribute(annotation, "integer");
        var fraction = numberAttribute(annotation, "fraction");
        return integer == null || fraction == null ? null : "整数最多 " + integer + " 位，小数最多 " + fraction + " 位";
    }

    private static String email(PsiAnnotation annotation) {
        String regex = stringAttribute(annotation, "regexp");
        String flags = attributeNames(annotation, "flags");
        if (regex == null || (".*".equals(regex) && flags.isEmpty())) {
            return "邮箱格式";
        }
        return "邮箱格式，另须匹配正则:" + regex + flagSuffix(flags);
    }

    private static String pattern(PsiAnnotation annotation) {
        String regex = stringAttribute(annotation, "regexp");
        return regex == null ? null : "匹配正则:" + regex + flagSuffix(attributeNames(annotation, "flags"));
    }

    private static String flagSuffix(String flags) {
        return flags.isEmpty() ? "" : "（" + flags + "）";
    }

    private static String attributeNames(PsiAnnotation annotation, String attribute) {
        var names = new StringJoiner(", ");
        for (var item : attributeItems(annotation, attribute)) {
            String name = item instanceof PsiReferenceExpression reference ? reference.getReferenceName() : item.getText();
            if (name != null) {
                names.add(name);
            }
        }
        return names.toString();
    }

    private static List<PsiAnnotationMemberValue> attributeItems(PsiAnnotation annotation, String attribute) {
        var value = annotation.findDeclaredAttributeValue(attribute);
        if (value instanceof PsiArrayInitializerMemberValue array) {
            return List.of(array.getInitializers());
        }
        return value == null ? List.of() : List.of(value);
    }

    private static Number numberAttribute(PsiAnnotation annotation, String attribute) {
        return PsiConstantUtils.constantValue(annotation.findAttributeValue(attribute)) instanceof Number value ? value : null;
    }

    private static String stringAttribute(PsiAnnotation annotation, String attribute) {
        return PsiConstantUtils.constantValue(annotation.findAttributeValue(attribute)) instanceof String value ? value : null;
    }
}
