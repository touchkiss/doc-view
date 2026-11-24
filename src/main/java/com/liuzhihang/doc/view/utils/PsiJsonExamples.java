package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.dto.JsonWireType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

import static com.liuzhihang.doc.view.utils.DtoPropertyTypes.*;
import static com.liuzhihang.doc.view.utils.ParamPsiUtils.DEFAULT_MAX_SCHEMA_DEPTH;

/** Projects Java property values to request and response JSON examples. */
final class PsiJsonExamples {
    private PsiJsonExamples() {
    }

    /**
     * 获取字段的默认值
     *
     * @param psiClass          当前类
     * @param genericMap        泛型
     * @param qualifiedNameList 根节点到当前节点的链表
     * @return 字段默认值
     */
    static Map<String, Object> getFieldsAndDefaultValue(PsiClass psiClass, Map<String, PsiType> genericMap, LinkedList<String> qualifiedNameList) {

        Map<String, Object> fieldMap = new LinkedHashMap<>();
        ProgressManager.checkCanceled();

        if (psiClass == null || psiClass.isEnum() || psiClass.isInterface() || psiClass.isAnnotationType()) {
            return fieldMap;
        }

        if (qualifiedNameList.size() >= DEFAULT_MAX_SCHEMA_DEPTH) return fieldMap;
        if (psiClass.isRecord()) {
            return getFieldsAndDefaultValueForRecord(psiClass, genericMap, qualifiedNameList);
        }

        boolean isProto = ProtoUtils.isProto(PsiTypesUtil.getClassType(psiClass));

        JacksonPropertyProjection projection = new JacksonPropertyProjection(psiClass, isProto);
        // 设置当前类的类型
        qualifiedNameList.add(psiClass.getQualifiedName());
        for (PsiDocCommentOwner member : projection.members(isProto)) {
            PsiField field = member instanceof PsiField propertyField ? propertyField : null;
            ProgressManager.checkCanceled();
            // Protobuf maps do not expose implementation properties.
            if (("Map".equals(psiClass.getName()) || Objects.requireNonNull(psiClass.getName()).startsWith("Map<")) && isProto) {
                break;
            }
            boolean omitNull = projection.omitsNull(member);
            if (omitNull && JacksonPropertyProjection.hasNullInitializer(field)) {
                continue;
            }
            String fieldName = field == null ? JacksonPropertyProjection.getterName((PsiMethod) member)
                    : projection.name(field, isProto);
            boolean parseProtoFieldType = false;
            String fieldTypeName = "";
            boolean isProtoMap = false;
            PsiType type = field == null ? ((PsiMethod) member).getReturnType() : field.getType();
            if (isProto) {
//            snake_case -> PascalCase
                String pascalCaseName = DocViewUtils.snakeToPascal(fieldName);
                boolean isProtoList = false;
                if (field.getType().getCanonicalText().matches("com\\.google\\.protobuf\\..+List")) {
                    pascalCaseName += "List";
                    isProtoList = true;
                } else if (field.getType().getCanonicalText().matches("com\\.google\\.protobuf\\.MapField.*")) {
                    pascalCaseName += "Map";
                    isProtoMap = true;
                }
                final String finalPascalCaseName = pascalCaseName;
                Optional<PsiMethod> psiMethod = Stream.of(psiClass.getMethods()).filter(method -> method.getName().equals("get" + finalPascalCaseName)).findFirst();
                if (psiMethod.isPresent()) {
                    PsiMethod method = psiMethod.get();
                    type = method.getReturnType();
                    if (type instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText()) || isProtoMap || isProtoList) {
                        fieldTypeName = type.getPresentableText();
                    } else {
                        PsiClass returnTypeClass = PsiUtil.resolveClassInType(type);
                        if (returnTypeClass != null) {
                            fieldTypeName = returnTypeClass.getName();
                        }
                    }
                    parseProtoFieldType = true;
                }
            }
            Object defaultValue = field == null ? null : SpringPsiUtils.getDefaultValue(field, type);
            if (!parseProtoFieldType) {
                // 如果是泛型, 且泛型字段是当前字段, 将当前字段类型替换为泛型类型
                type = projection.memberType(member, type, genericMap);

                // 引用类型
                fieldTypeName = type.getPresentableText();
            }
            JsonWireType wireType = JacksonPsiUtils.resolveJsonWireType(member, type);
            if (wireType.isOverridden() && isJsonSimpleType(wireType.getJsonType())) {
                fieldMap.put(fieldName, wireType.getDefaultValue() != null ? wireType.getDefaultValue() : "");
                continue;
            }
            if (type instanceof PsiPrimitiveType) {
                // 基本类型
                fieldMap.put(fieldName, defaultValue == null ? PsiTypesUtil.getDefaultValue(type) : defaultValue);
                continue;
            }
            if (wireType.isOverridden()) {
                fieldTypeName = wireType.getJsonType();
            }
            // 指定的类型
            if (FieldTypeConstant.FIELD_TYPE.containsKey(fieldTypeName)) {
                Object value = defaultValue == null ? FieldTypeConstant.FIELD_TYPE.get(fieldTypeName) : defaultValue;
                if (wireType.isOverridden() && wireType.getDefaultValue() != null) {
                    value = wireType.getDefaultValue();
                }
                if (!omitNull || value != null) {
                    fieldMap.put(fieldName, value);
                }
            } else if (type instanceof PsiArrayType) {
                // 数组类型
                List<Object> list = new ArrayList<>();
                PsiType deepType = type.getDeepComponentType();
                if (deepType instanceof PsiPrimitiveType) {
                    list.add(PsiTypesUtil.getDefaultValue(deepType));
                } else if (FieldTypeConstant.FIELD_TYPE.containsKey(deepType.getPresentableText())) {
                    list.add(FieldTypeConstant.FIELD_TYPE.get(deepType.getPresentableText()));
                } else {
                    // 参数类型为对象 校验是否递归
                    PsiClass classInType = PsiUtil.resolveClassInType(deepType);

                    LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                    if (classInType != null && (containsQualifiedName(temp, classInType.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH)) {
                        list.add("Object for " + classInType.getName());
                    } else {
                        list.add(getFieldsAndDefaultValue(classInType, null, temp));
                    }
                }
                fieldMap.put(fieldName, list);
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                // List Set or HashSet
                List<Object> list = new ArrayList<>();
                PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                JsonWireType contentWireType = JacksonPsiUtils.resolveContentUsing(member, iterableType);
                PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                if (iterableClass != null) {
                    String classTypeName = iterableClass.getName();
                    if (FieldTypeConstant.FIELD_TYPE.containsKey(classTypeName)) {
                        Object elementValue = FieldTypeConstant.FIELD_TYPE.get(classTypeName);
                        if (contentWireType.isOverridden() && contentWireType.getDefaultValue() != null) {
                            elementValue = contentWireType.getDefaultValue();
                        }
                        list.add(elementValue);
                    } else {

                        // 参数类型为对象 校验是否递归
                        LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                        if (containsQualifiedName(temp, iterableClass.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH) {
                            list.add("Object for " + iterableClass.getName());
                        } else {
                            Map<String, PsiType> elementGenerics = iterableType instanceof PsiClassType classType
                                    ? CustomPsiUtils.getGenericsMap(classType) : null;
                            list.add(getFieldsAndDefaultValue(iterableClass, elementGenerics, temp));
                        }

                    }
                }
                fieldMap.put(fieldName, list);
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
                // HashMap or Map

                HashMap<String, Object> hashMap = new HashMap<>(4);

                PsiType matKeyType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);

                // key 只能是 包装类型或者 String
                if (matKeyType != null && FieldTypeConstant.PACKAGE_TYPE_SET.contains(matKeyType.getPresentableText())) {
                    // Value
                    PsiType matValueType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
                    if (!ignoreField(matValueType)) {
                        PsiClass valueClass = PsiUtil.resolveClassInClassTypeOnly(matValueType);
                        if (valueClass != null) {
                            LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                            if (containsQualifiedName(temp, valueClass.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH) {
                                hashMap.put(matKeyType.getPresentableText(), "Object for " + valueClass.getName());
                            } else {
                                hashMap.put(matKeyType.getPresentableText(), getFieldsAndDefaultValue(valueClass, CustomPsiUtils.getGenericsMap((PsiClassType) matValueType), temp));
                            }
                        }
                    }

                }

                fieldMap.put(fieldName, hashMap);

            } else if (psiClass.isEnum() || psiClass.isInterface() || psiClass.isAnnotationType()) {
                // enum or interface
                fieldMap.put(fieldName, "");
            } else {

                // 参数类型为对象 校验是否递归
                PsiClass classInType = PsiUtil.resolveClassInType(type);

                LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                if (classInType != null && (containsQualifiedName(temp, classInType.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH)) {
                    fieldMap.put(fieldName, "Object for " + classInType.getName());
                } else {
                    fieldMap.put(fieldName, getFieldsAndDefaultValue(PsiUtil.resolveClassInType(type), CustomPsiUtils.getGenericsMap((PsiClassType) type), temp));
                }

            }

        }
        return fieldMap;
    }

    static Map<String, Object> getFieldsAndDefaultValueForRecord(@NotNull PsiClass psiClass, Map<String, PsiType> genericMap) {
        return getFieldsAndDefaultValueForRecord(psiClass, genericMap, new LinkedList<>());
    }

    static Map<String, Object> getFieldsAndDefaultValueForRecord(@NotNull PsiClass psiClass,
                                                                         Map<String, PsiType> genericMap,
                                                                         @NotNull LinkedList<String> qualifiedNameList) {
        ProgressManager.checkCanceled();
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        if (qualifiedNameList.size() >= DEFAULT_MAX_SCHEMA_DEPTH) return fieldMap;
        qualifiedNameList.add(psiClass.getQualifiedName());

        JacksonPropertyProjection projection = new JacksonPropertyProjection(psiClass, false);
        for (PsiRecordComponent component : psiClass.getRecordComponents()) {
            ProgressManager.checkCanceled();
            String fieldName = projection.name(component);
            if (!projection.includes(component, fieldName)) {
                continue;
            }
            PsiType type = replaceFieldType(genericMap, component.getType());
            String fieldTypeName = type.getPresentableText();
            JsonWireType wireType = JacksonPsiUtils.resolveJsonWireType(component, type);
            if (wireType.isOverridden() && isJsonSimpleType(wireType.getJsonType())) {
                fieldMap.put(fieldName, wireType.getDefaultValue() != null ? wireType.getDefaultValue() : "");
                continue;
            }
            if (wireType.isOverridden()) {
                fieldTypeName = wireType.getJsonType();
            }

            if (type instanceof PsiPrimitiveType) {
                fieldMap.put(fieldName, PsiTypesUtil.getDefaultValue(type));
                continue;
            }

            if (FieldTypeConstant.FIELD_TYPE.containsKey(fieldTypeName)) {
                Object value = FieldTypeConstant.FIELD_TYPE.get(fieldTypeName);
                if (wireType.isOverridden() && wireType.getDefaultValue() != null) {
                    value = wireType.getDefaultValue();
                }
                if (value != null || !projection.omitsNull(component)) {
                    fieldMap.put(fieldName, value);
                }
            } else if (type instanceof PsiArrayType) {
                List<Object> list = new ArrayList<>();
                PsiType deepType = type.getDeepComponentType();
                if (deepType instanceof PsiPrimitiveType) {
                    list.add(PsiTypesUtil.getDefaultValue(deepType));
                } else if (FieldTypeConstant.FIELD_TYPE.containsKey(deepType.getPresentableText())) {
                    list.add(FieldTypeConstant.FIELD_TYPE.get(deepType.getPresentableText()));
                } else {
                    PsiClass classInType = PsiUtil.resolveClassInType(deepType);
                    LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                    if (classInType != null && (containsQualifiedName(temp, classInType.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH)) {
                        list.add("Object for " + classInType.getName());
                    } else if (classInType != null && classInType.isRecord()) {
                        list.add(getFieldsAndDefaultValueForRecord(classInType, null, temp));
                    } else {
                        list.add(getFieldsAndDefaultValue(classInType, null, temp));
                    }
                }
                fieldMap.put(fieldName, list);
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                List<Object> list = new ArrayList<>();
                PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                JsonWireType contentWireType = JacksonPsiUtils.resolveContentUsing(component, iterableType);
                PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                if (iterableClass != null) {
                    if (FieldTypeConstant.FIELD_TYPE.containsKey(iterableClass.getName())) {
                        Object elementValue = FieldTypeConstant.FIELD_TYPE.get(iterableClass.getName());
                        if (contentWireType.isOverridden() && contentWireType.getDefaultValue() != null) {
                            elementValue = contentWireType.getDefaultValue();
                        }
                        list.add(elementValue);
                    } else {
                        LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                        if (containsQualifiedName(temp, iterableClass.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH) {
                            list.add("Object for " + iterableClass.getName());
                        } else if (iterableClass.isRecord()) {
                            list.add(getFieldsAndDefaultValueForRecord(iterableClass, null, temp));
                        } else {
                            list.add(getFieldsAndDefaultValue(iterableClass, null, temp));
                        }
                    }
                }
                fieldMap.put(fieldName, list);
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
                HashMap<String, Object> hashMap = new HashMap<>(4);
                PsiType matKeyType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);
                if (matKeyType != null && FieldTypeConstant.PACKAGE_TYPE_SET.contains(matKeyType.getPresentableText())) {
                    PsiType matValueType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
                    if (!ignoreField(matValueType)) {
                        PsiClass valueClass = PsiUtil.resolveClassInClassTypeOnly(matValueType);
                        if (valueClass != null) {
                            LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                            if (containsQualifiedName(temp, valueClass.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH) {
                                hashMap.put(matKeyType.getPresentableText(), "Object for " + valueClass.getName());
                            } else if (valueClass.isRecord()) {
                                hashMap.put(matKeyType.getPresentableText(), getFieldsAndDefaultValueForRecord(valueClass, CustomPsiUtils.getGenericsMap((PsiClassType) matValueType), temp));
                            } else {
                                hashMap.put(matKeyType.getPresentableText(), getFieldsAndDefaultValue(valueClass, CustomPsiUtils.getGenericsMap((PsiClassType) matValueType), temp));
                            }
                        }
                    }
                }
                fieldMap.put(fieldName, hashMap);
            } else {
                PsiClass classInType = PsiUtil.resolveClassInType(type);
                LinkedList<String> temp = new LinkedList<>(qualifiedNameList);
                if (classInType != null && (containsQualifiedName(temp, classInType.getQualifiedName()) || temp.size() >= DEFAULT_MAX_SCHEMA_DEPTH)) {
                    fieldMap.put(fieldName, "Object for " + classInType.getName());
                } else if (classInType != null && classInType.isRecord()) {
                    fieldMap.put(fieldName, getFieldsAndDefaultValueForRecord(classInType, type instanceof PsiClassType ? CustomPsiUtils.getGenericsMap((PsiClassType) type) : null, temp));
                } else {
                    fieldMap.put(fieldName, getFieldsAndDefaultValue(classInType, type instanceof PsiClassType ? CustomPsiUtils.getGenericsMap((PsiClassType) type) : null, temp));
                }
            }
        }
        return fieldMap;
    }

    private static boolean containsQualifiedName(LinkedList<String> qualifiedNameList, String qualifiedName) {

        if (qualifiedNameList.isEmpty()) {
            return false;
        }

        for (String s : qualifiedNameList) {
            ProgressManager.checkCanceled();
            if (s.equals(qualifiedName)) {
                return true;
            }
        }

        return false;

    }


    /**
     * 获取字段和字段的默认值
     *
     * @param psiClass
     * @param genericMap
     * @return
     */
    @NotNull
    static Map<String, Object> getFieldsAndDefaultValue(PsiClass psiClass, Map<String, PsiType> genericMap) {

        return getFieldsAndDefaultValue(psiClass, genericMap, new LinkedList<>());
    }

    @NotNull
    static String getRespBodyJson(PsiType returnType) {

        if (returnType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(returnType.getPresentableText())) {
            return "";
        } else if (returnType instanceof PsiClassType psiClassType) {

            PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
            if (psiClass != null) {
                // 返回类型是集合
                if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {

                    // 获取泛型
                    PsiType iterableType = PsiUtil.extractIterableTypeParameter(psiClassType, false);

                    if (iterableType == null) {
                        return "[]";
                    }


                    if (iterableType instanceof PsiPrimitiveType) {
                        return "[]";
                    }

                    if (CommonClassNames.JAVA_LANG_STRING_SHORT.equals(iterableType.getPresentableText())) {
                        return "[\"\"]";
                    }

                    if (FieldTypeConstant.FIELD_TYPE.containsKey(iterableType.getPresentableText())) {
                        return "[\"\"]";
                    }

                    PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                    Map<String, PsiType> elementGenerics = iterableType instanceof PsiClassType classType
                            ? CustomPsiUtils.getGenericsMap(classType) : null;
                    Map<String, Object> fieldMap = getFieldsAndDefaultValue(iterableClass, elementGenerics);

                    Object[] objectArr = {fieldMap};

                    return GsonFormatUtil.gsonFormat(objectArr);
                } else {

                    Map<String, PsiType> genericsMap = CustomPsiUtils.getGenericsMap(psiClassType);

                    Map<String, Object> fieldMap = getFieldsAndDefaultValue(psiClass, genericsMap);

                    return GsonFormatUtil.gsonFormat(fieldMap);
                }
            }
        } else {
            // 其他类型
        }

        return "";
    }
}
