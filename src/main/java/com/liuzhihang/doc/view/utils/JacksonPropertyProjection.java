package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.liuzhihang.doc.view.constant.JsonPropertyConstant;

import java.beans.Introspector;
import java.util.*;

/** Jackson property names and inclusion rules shared by schema and example generation. */
final class JacksonPropertyProjection {
    private static final String JSON_IGNORE_PROPERTIES = "com.fasterxml.jackson.annotation.JsonIgnoreProperties";
    private static final String JSON_INCLUDE_PROPERTIES = "com.fasterxml.jackson.annotation.JsonIncludeProperties";
    private static final String JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";

    private final PsiClass owner;
    private final Set<String> included;
    private final Set<String> ignored;
    private final PsiAnnotation inclusion;

    JacksonPropertyProjection(PsiClass owner, boolean proto) {
        this.owner = owner;
        PsiAnnotation allowlist = proto ? null : owner.getAnnotation(JSON_INCLUDE_PROPERTIES);
        inclusion = proto ? null : owner.getAnnotation(JSON_INCLUDE);
        included = allowlist == null ? null : propertyNames(allowlist);
        PsiAnnotation ignoreList = proto ? null : owner.getAnnotation(JSON_IGNORE_PROPERTIES);
        // These projections are shared by request and response. Apply only bidirectional exclusions.
        ignored = ignoreList != null && !allows(ignoreList, "allowGetters") && !allows(ignoreList, "allowSetters")
                ? propertyNames(ignoreList) : Set.of();
    }

    private static Set<String> propertyNames(PsiAnnotation annotation) {
        Set<String> names = new HashSet<>();
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue item : array.getInitializers()) {
                if (PsiConstantUtils.constantValue(item) instanceof String name) {
                    names.add(name);
                }
            }
        } else if (PsiConstantUtils.constantValue(value) instanceof String name) {
            names.add(name);
        }
        return names;
    }

    private static boolean allows(PsiAnnotation annotation, String attribute) {
        return Boolean.TRUE.equals(PsiConstantUtils.constantValue(annotation.findAttributeValue(attribute)));
    }

    boolean includes(PsiModifierListOwner member, String name) {
        return (included == null || included.contains(name))
                && (ignored.isEmpty() || !ignored.contains(explicitName(member, name)));
    }

    String name(PsiField field, boolean proto) {
        return included != null || inclusion != null
                ? explicitName(field, field.getName()) : DocViewUtils.fieldName(field, proto);
    }

    String name(PsiRecordComponent component) {
        return included != null || inclusion != null
                ? explicitName(component, component.getName()) : DocViewUtils.fieldName(component, false);
    }

    List<PsiDocCommentOwner> members(boolean proto) {
        List<PsiDocCommentOwner> members = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (PsiField field : owner.getAllFields()) {
            ProgressManager.checkCanceled();
            if (DocViewUtils.isExcludeField(field, proto)) {
                continue;
            }
            String name = name(field, proto);
            if (includes(field, name) && names.add(included == null ? field.getName() : name)) {
                members.add(field);
            }
        }
        // Discover computed properties explicitly selected by a class allowlist.
        if (included != null) {
            for (PsiMethod method : owner.getAllMethods()) {
                ProgressManager.checkCanceled();
                String name = getterName(method);
                if (name != null && includes(method, name) && names.add(name)) {
                    members.add(method);
                }
            }
        }
        return members;
    }

    private static String explicitName(PsiModifierListOwner member, String fallback) {
        PsiAnnotation property = member.getAnnotation(JsonPropertyConstant.JSON_PROPERTY);
        if (property != null
                && PsiConstantUtils.constantValue(property.findAttributeValue("value")) instanceof String name
                && !name.isEmpty()) {
            return name;
        }
        return fallback;
    }

    static String getterName(PsiMethod method) {
        PsiType type = method.getReturnType();
        if (type == null || PsiTypes.voidType().equals(type) || method.hasParameters()
                || method.hasModifierProperty(PsiModifier.STATIC)) {
            return null;
        }
        String methodName = method.getName();
        String beanName = null;
        if (method.hasModifierProperty(PsiModifier.PUBLIC) && !"getClass".equals(methodName)) {
            if (methodName.startsWith("get") && methodName.length() > 3) {
                beanName = Introspector.decapitalize(methodName.substring(3));
            } else if (methodName.startsWith("is") && methodName.length() > 2
                    && (PsiTypes.booleanType().equals(type) || type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN))) {
                beanName = Introspector.decapitalize(methodName.substring(2));
            }
        }
        return explicitName(method, beanName);
    }

    PsiType memberType(PsiMember member, PsiType type, Map<String, PsiType> generics) {
        PsiClass declaringClass = member.getContainingClass();
        if (included == null || declaringClass == null || declaringClass.equals(owner)) {
            return DtoPropertyTypes.replaceFieldType(generics, type);
        }
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        if (generics != null) {
            for (PsiTypeParameter parameter : owner.getTypeParameters()) {
                PsiType argument = generics.get(parameter.getName());
                if (argument != null) {
                    substitutor = substitutor.put(parameter, argument);
                }
            }
        }
        return TypeConversionUtil.getSuperClassSubstitutor(declaringClass, owner, substitutor).substitute(type);
    }

    boolean omitsNull(PsiModifierListOwner member) {
        PsiAnnotation annotation = member.getAnnotation(JSON_INCLUDE);
        String policy = inclusionPolicy(annotation);
        return "NON_NULL".equals(policy)
                || ((policy == null || "USE_DEFAULTS".equals(policy)) && "NON_NULL".equals(inclusionPolicy(inclusion)));
    }

    private static String inclusionPolicy(PsiAnnotation annotation) {
        if (annotation != null && annotation.findAttributeValue("value") instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiEnumConstant constant) {
            return constant.getName();
        }
        return null;
    }

    static boolean hasNullInitializer(PsiField field) {
        return field != null
                && PsiUtil.skipParenthesizedExprDown(field.getInitializer()) instanceof PsiLiteralExpression literal
                && PsiTypes.nullType().equals(literal.getType());
    }
}
