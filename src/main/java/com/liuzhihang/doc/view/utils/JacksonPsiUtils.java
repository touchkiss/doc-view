package com.liuzhihang.doc.view.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.constant.JacksonConstant;
import com.liuzhihang.doc.view.dto.JsonWireType;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 解析 Jackson {@code @JsonSerialize} / {@code @JsonDeserialize} 注解，推断 JSON 实际类型
 */
public final class JacksonPsiUtils {

    private static final Map<String, String> WELL_KNOWN_SERIALIZERS = new HashMap<>();
    private static final Map<String, String> WELL_KNOWN_DESERIALIZERS = new HashMap<>();

    static {
        WELL_KNOWN_SERIALIZERS.put(JacksonConstant.TO_STRING_SERIALIZER, "String");
        WELL_KNOWN_SERIALIZERS.put(JacksonConstant.NUMBER_SERIALIZER, "Number");
        WELL_KNOWN_SERIALIZERS.put(JacksonConstant.DATE_SERIALIZER, "String");
        WELL_KNOWN_SERIALIZERS.put(JacksonConstant.NULL_SERIALIZER, "null");
        WELL_KNOWN_SERIALIZERS.put(JacksonConstant.STRING_SERIALIZER, "String");
        WELL_KNOWN_SERIALIZERS.put(JacksonConstant.BOOLEAN_SERIALIZER, "Boolean");

        WELL_KNOWN_DESERIALIZERS.put(JacksonConstant.STRING_DESERIALIZER, "String");
    }

    private JacksonPsiUtils() {
    }

    @NotNull
    public static JsonWireType resolveJsonWireType(@NotNull PsiModifierListOwner owner, @NotNull PsiType javaType) {
        String javaTypeName = javaType.getPresentableText();

        PsiAnnotation serializeAnnotation = AnnotationUtil.findAnnotation(owner, JacksonConstant.JSON_SERIALIZE);
        if (serializeAnnotation != null) {
            PsiClass serializerClass = resolveClassFromAnnotationAttribute(serializeAnnotation, "using");
            if (serializerClass != null && !isNoneClass(serializerClass)) {
                String jsonType = resolveSerializerClass(serializerClass);
                if (jsonType != null && !jsonType.equals(javaTypeToJsonType(javaType))) {
                    return buildOverridden(jsonType, javaType);
                }
            }
        }

        PsiAnnotation deserializeAnnotation = AnnotationUtil.findAnnotation(owner, JacksonConstant.JSON_DESERIALIZE);
        if (deserializeAnnotation != null) {
            PsiClass deserializerClass = resolveClassFromAnnotationAttribute(deserializeAnnotation, "using");
            if (deserializerClass != null && !isNoneClass(deserializerClass)) {
                String jsonType = resolveDeserializerClass(deserializerClass, javaType);
                if (jsonType != null && !jsonType.equals(javaTypeToJsonType(javaType))) {
                    return buildOverridden(jsonType, javaType);
                }
            }
        }

        return JsonWireType.ofJavaType(javaTypeName);
    }

    @NotNull
    public static JsonWireType resolveContentUsing(@NotNull PsiModifierListOwner owner, @Nullable PsiType elementType) {
        if (elementType == null) {
            return JsonWireType.ofJavaType("");
        }

        PsiAnnotation serializeAnnotation = AnnotationUtil.findAnnotation(owner, JacksonConstant.JSON_SERIALIZE);
        if (serializeAnnotation != null) {
            PsiClass serializerClass = resolveClassFromAnnotationAttribute(serializeAnnotation, "contentUsing");
            if (serializerClass != null && !isNoneClass(serializerClass)) {
                String jsonType = resolveSerializerClass(serializerClass);
                if (jsonType != null && !jsonType.equals(javaTypeToJsonType(elementType))) {
                    return buildOverridden(jsonType, elementType);
                }
            }
        }

        PsiAnnotation deserializeAnnotation = AnnotationUtil.findAnnotation(owner, JacksonConstant.JSON_DESERIALIZE);
        if (deserializeAnnotation != null) {
            PsiClass deserializerClass = resolveClassFromAnnotationAttribute(deserializeAnnotation, "contentUsing");
            if (deserializerClass != null && !isNoneClass(deserializerClass)) {
                String jsonType = resolveDeserializerClass(deserializerClass, elementType);
                if (jsonType != null && !jsonType.equals(javaTypeToJsonType(elementType))) {
                    return buildOverridden(jsonType, elementType);
                }
            }
        }

        return JsonWireType.ofJavaType(elementType.getPresentableText());
    }

    @Nullable
    public static String resolveSerializerClass(@NotNull PsiClass serializerClass) {
        String qualifiedName = serializerClass.getQualifiedName();
        if (StringUtils.isBlank(qualifiedName)) {
            return null;
        }

        String wellKnown = WELL_KNOWN_SERIALIZERS.get(qualifiedName);
        if (wellKnown != null) {
            return wellKnown;
        }

        if (InheritanceUtil.isInheritor(serializerClass, JacksonConstant.TO_STRING_SERIALIZER)) {
            return "String";
        }

        String fromGeneric = resolveGenericHandledType(serializerClass, JacksonConstant.JSON_SERIALIZER);
        if (fromGeneric != null) {
            return fromGeneric;
        }

        return null;
    }

    @Nullable
    public static String resolveDeserializerClass(@NotNull PsiClass deserializerClass, @NotNull PsiType javaType) {
        String qualifiedName = deserializerClass.getQualifiedName();
        if (StringUtils.isBlank(qualifiedName)) {
            return null;
        }

        String wellKnown = WELL_KNOWN_DESERIALIZERS.get(qualifiedName);
        if (wellKnown != null) {
            return wellKnown;
        }

        if (InheritanceUtil.isInheritor(deserializerClass, JacksonConstant.FROM_STRING_DESERIALIZER)) {
            return "String";
        }

        if (deserializerClass.getName() != null && deserializerClass.getName().contains("String")) {
            return "String";
        }

        String fromGeneric = resolveGenericHandledType(deserializerClass, JacksonConstant.JSON_DESERIALIZER);
        if (fromGeneric != null) {
            return fromGeneric;
        }

        return null;
    }

    @NotNull
    public static String javaTypeToJsonType(@NotNull PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return type.getPresentableText();
        }
        String presentable = type.getPresentableText();
        if (FieldTypeConstant.FIELD_TYPE.containsKey(presentable)) {
            return presentable;
        }
        PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass != null && psiClass.getName() != null) {
            return psiClass.getName();
        }
        return presentable;
    }

    @Nullable
    private static PsiClass resolveClassFromAnnotationAttribute(@Nullable PsiAnnotation annotation, @NotNull String attributeName) {
        if (annotation == null) {
            return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value == null) {
            return null;
        }
        if (value instanceof PsiClassObjectAccessExpression classObject) {
            PsiType type = classObject.getOperand().getType();
            if (type instanceof PsiClassType classType) {
                return PsiUtil.resolveClassInClassTypeOnly(classType);
            }
        }
        if (value instanceof PsiReferenceExpression ref) {
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiClass psiClass) {
                return psiClass;
            }
        }
        return null;
    }

    @Nullable
    private static String resolveGenericHandledType(@NotNull PsiClass handlerClass, @NotNull String baseClassFqn) {
        PsiClassType[] superTypes = handlerClass.getSuperTypes();
        for (PsiClassType superType : superTypes) {
            PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(superType);
            if (resolved == null) {
                continue;
            }
            if (!InheritanceUtil.isInheritor(resolved, baseClassFqn)) {
                continue;
            }
            PsiType[] params = superType.getParameters();
            if (params.length == 1) {
                return javaTypeToJsonType(params[0]);
            }
        }
        return null;
    }

    private static boolean isNoneClass(@NotNull PsiClass psiClass) {
        String qn = psiClass.getQualifiedName();
        return JacksonConstant.JSON_SERIALIZER_NONE.equals(qn)
                || JacksonConstant.JSON_DESERIALIZER_NONE.equals(qn);
    }

    @NotNull
    private static JsonWireType buildOverridden(@NotNull String jsonType, @NotNull PsiType javaType) {
        if ("String".equals(jsonType) && isNumericJavaType(javaType)) {
            return JsonWireType.overridden(jsonType, "0", "0");
        }
        if ("null".equals(jsonType)) {
            return JsonWireType.overridden(jsonType, null, "null");
        }
        Object defaultValue = FieldTypeConstant.FIELD_TYPE.get(jsonType);
        if (defaultValue == null && FieldTypeConstant.FIELD_TYPE.containsKey(javaType.getPresentableText())) {
            defaultValue = FieldTypeConstant.FIELD_TYPE.get(javaType.getPresentableText());
        }
        return JsonWireType.overridden(jsonType, defaultValue, defaultValue == null ? null : String.valueOf(defaultValue));
    }

    private static boolean isNumericJavaType(@NotNull PsiType type) {
        String name = type.getPresentableText();
        return FieldTypeConstant.BASE_TYPE_SET.contains(name)
                || "Long".equals(name) || "Integer".equals(name) || "Short".equals(name)
                || "Byte".equals(name) || "Float".equals(name) || "Double".equals(name)
                || "BigDecimal".equals(name) || "BigInteger".equals(name);
    }
}
