package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocDescription;
import com.liuzhihang.doc.view.dto.DocDescription.Source;
import com.liuzhihang.doc.view.dto.JsonWireType;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

import static com.liuzhihang.doc.view.utils.DtoPropertyTypes.*;


/**
 * 参数处理工具
 *
 * @author liuzhihang
 * @date 2020/11/17 15:15
 */
public class ParamPsiUtils {

    public static final int DEFAULT_MAX_SCHEMA_DEPTH = 8;

    /**
     * 生成 body
     *
     * @param parentClass     父类
     * @param field           字段
     * @param genericsMap     key 是泛型 value 是对应的类型
     * @param parent          父字段
     * @param parentChildPair 父子对
     * @param parentIsProto   parent 是 proto
     */
    public static void buildBodyParam(PsiClass parentClass, PsiField field, Map<String, PsiType> genericsMap, Body parent, Map<String, Boolean> parentChildPair, boolean parentIsProto) {
        buildBodyParam(parentClass, field, genericsMap, parent, parentChildPair, parentIsProto,
                DtoParsingContext.root(DEFAULT_MAX_SCHEMA_DEPTH));
    }

    public static void buildBodyParam(PsiField field, Map<String, PsiType> genericsMap, Body parent,
                                      Map<String, Boolean> parentChildPair) {
        PsiClass owner = field.getContainingClass();
        buildBodyParam(owner, field, genericsMap, parent, parentChildPair,
                owner != null && ProtoUtils.isProto(PsiTypesUtil.getClassType(owner)));
    }

    public static void buildBodyParam(PsiField field, Map<String, PsiType> genericsMap, Body parent,
                                      Map<String, Boolean> parentChildPair, DtoParsingContext context) {
        PsiClass owner = field.getContainingClass();
        buildBodyParam(owner, field, genericsMap, parent, parentChildPair,
                owner != null && ProtoUtils.isProto(PsiTypesUtil.getClassType(owner)), context);
    }

    private static void buildBodyParam(PsiClass parentClass, PsiField field, Map<String, PsiType> genericsMap,
                                       Body parent, Map<String, Boolean> parentChildPair, boolean parentIsProto,
                                       DtoParsingContext context) {
        JacksonPropertyProjection projection = parentClass == null ? null : new JacksonPropertyProjection(parentClass, parentIsProto);
        if (projection != null && !projection.includes(field, projection.name(field, parentIsProto))) {
            return;
        }
        buildBodyProperty(parentClass, field, genericsMap, parent, parentChildPair, parentIsProto, context, projection);
    }

    private static void buildBodyProperty(PsiClass parentClass, PsiDocCommentOwner member, Map<String, PsiType> genericsMap,
                                          Body parent, Map<String, Boolean> parentChildPair, boolean parentIsProto,
                                          DtoParsingContext context, JacksonPropertyProjection projection) {
        ProgressManager.checkCanceled();
        PsiField field = member instanceof PsiField propertyField ? propertyField : null;
        PsiType declaredType = field != null ? field.getType() : ((PsiMethod) member).getReturnType();
        String fieldName = field != null
                ? (projection == null ? DocViewUtils.fieldName(field, parentIsProto) : projection.name(field, parentIsProto))
                : JacksonPropertyProjection.getterName((PsiMethod) member);

        // parentChildPair 语义：当前递归路径栈（而非"全局已访问集合"）
        // - 进入时入栈，退出时出栈（try-finally 保证）
        // - 仅防止同一条递归链上重复出现（如 A→B→A、List<A>→B→List<A>）
        // - 不同分支（A.c 和 B.c 都持有 C 类型字段）可以各自完整解析 C 的子字段
        String parentQName = (parentClass != null && parentClass.getQualifiedName() != null)
                ? parentClass.getQualifiedName() : "";
        String pair = parentQName + "_" + member.getName();
        if (parentChildPair.containsKey(pair)) {
            // 当前字段已在递归路径上，跳过以防循环
            return;
        }
        parentChildPair.put(pair, true);
        try {

        PsiType type = declaredType;
        boolean isProto = ProtoUtils.isProto(type);
        Body body = new Body();
        body.setRequired(field != null && DocViewUtils.isRequired(field));
        body.setName(fieldName);
        body.setPsiElement(member);
        PsiDocCommentOwner documentationMember = field == null ? member : DependencySourceComments.documentationField(field);
        body.setSince(CustomPsiCommentUtils.tagDocComment(documentationMember.getDocComment(), "since"));
        body.setVersion(CustomPsiCommentUtils.tagDocComment(documentationMember.getDocComment(), "version"));
        boolean parseProtoFieldDesc = false;
        boolean isProtoMap = false;
        if (parentIsProto) {
//            snake_case -> PascalCase
            String pascalCaseName = DocViewUtils.snakeToPascal(fieldName);
            boolean isProtoList = false;
            if (type.getCanonicalText().matches("com\\.google\\.protobuf\\..+List")) {
                pascalCaseName += "List";
                isProtoList = true;
            } else if (type.getCanonicalText().matches("com\\.google\\.protobuf\\.MapField.*")) {
                pascalCaseName += "Map";
                isProtoMap = true;
            }
//            field.getType().getPresentableText()
            final String finalPascalCaseName = pascalCaseName;
            Optional<PsiMethod> psiMethod = Stream.of(parentClass.getMethods()).filter(m -> m.getName().equals("get" + finalPascalCaseName)).findFirst();
            if (psiMethod.isPresent()) {
                PsiMethod method = psiMethod.get();
                PsiType returnType = method.getReturnType();
                String methodDesc = DocViewUtils.getMethodDesc(method);
                if (isProtoList) {
                    body.setType(returnType.getPresentableText());
                    body.setCollection(true);
                } else if (returnType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(returnType.getPresentableText()) || isProtoMap) {
                    body.setType(returnType.getPresentableText());
                } else {
                    PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
                    if (returnTypeClass != null) {
                        body.setType(returnTypeClass.getName());
                    }
                }
                body.setQualifiedNameForClassType("String".equals(body.getType()) ? "java.lang.String" : body.getType());
//                去掉<code>int64next_cursor=9;</code>包含的内容
                methodDesc = methodDesc
                        .replaceAll("<code>.*?</code>", "")
//                        去掉 <pre> </pre> 标签（@see 为真实 tag 时 </pre> 会被吸入 tag, 标签可能不成对, 故单独剥离）
                        .replaceAll("</?pre>", "")
//                        去掉残留在正文中的 @see / &#64;see 引用文本（escaped 情况下不会被解析为 tag, 会落到正文）
                        .replaceAll("(?i)(&#64;|@)see\\s*[\\w.#$()]*", "")
                        .trim();
//                proto getter 注释中携带 @see/@link 指向枚举时, 沿引用拼接枚举约束信息
                var description = DocDescription.EMPTY.with(Source.PROTO_GETTER, methodDesc);
                if (method.getDocComment() != null) {
                    description = description.with(Source.ENUM_REFERENCE,
                            CustomPsiCommentUtils.resolveEnumInfoFromSeeTag(method.getDocComment()));
                }
                body.setDescription(description);
                parseProtoFieldDesc = true;
            }
        }
        // 剩下都是 PsiClass 类型处理
        PsiClass fieldClass = PsiUtil.resolveClassInClassTypeOnly(type);

        String qualifiedName = "";
        if (fieldClass == null && !parentIsProto && isProto) {
            return;
        }
        if (fieldClass != null) {
            qualifiedName = fieldClass.getQualifiedName();
        }
        if (!parseProtoFieldDesc) {
            body.setType(type.getPresentableText());
            if (field == null) {
                String comment = CustomPsiCommentUtils.descriptionBody(member.getDocComment());
                body.setDescription(DocDescription.local(comment, comment));
            } else {
                body.setDescription(DocViewUtils.fieldDescription(field));
            }

            // 提前替换字段比如 T -> UserDTO   List<T> -> List<UserDTO>
            // 如果是泛型, 且泛型字段是当前字段, 将当前字段类型替换為泛型類型, 替換完之後重新設置 body 的 type
            type = projection == null ? replaceFieldType(genericsMap, type) : projection.memberType(member, type, genericsMap);
            JsonWireType wireType = JacksonPsiUtils.resolveJsonWireType(member, type);
            body.setType(wireType.isOverridden() ? wireType.getJsonType() : type.getPresentableText());
            qualifiedName = wireType.isOverridden() ? wireType.getJsonType() : type.getPresentableText();
            body.setQualifiedNameForClassType(qualifiedName);
            fieldClass = PsiUtil.resolveClassInType(type);
            if (wireType.isOverridden() && wireType.getExampleOverride() != null) {
                body.setExample(wireType.getExampleOverride());
            }
        }
        body.setParent(parent);

        parent.getChildList().add(body);
        JsonWireType fieldWireType = JacksonPsiUtils.resolveJsonWireType(member, type);
        boolean omitNull = projection != null && projection.omitsNull(member);
        if (omitNull && JacksonPropertyProjection.hasNullInitializer(field)) {
            body.setOmitFromExample(true);
        }
        if (fieldWireType.isOverridden() && isJsonSimpleType(fieldWireType.getJsonType())) {
            if (fieldWireType.getExampleOverride() != null) {
                body.setExample(fieldWireType.getExampleOverride());
            }
            return;
        }
        if (type instanceof PsiArrayType) {
            body.setCollection(true);
            while (type instanceof PsiArrayType arrayType) {
                type = arrayType.getComponentType();
            }
            fieldClass = PsiUtil.resolveClassInType(type);
            qualifiedName = type.getCanonicalText();
            body.setQualifiedNameForClassType(qualifiedName);
            if (setScalarArrayItemExample(body, type)) {
                return;
            }
        }
        if (type instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {

            String example = field == null ? null : PsiConstantUtils.expressionText(field.getInitializer());
            if (omitNull && example == null) {
                Object sample = type instanceof PsiPrimitiveType ? PsiTypesUtil.getDefaultValue(type)
                        : FieldTypeConstant.FIELD_TYPE.get(type.getPresentableText());
                body.setOmitFromExample(sample == null);
                example = sample == null ? null : String.valueOf(sample);
            }
            if (example != null && !body.isOmitFromExample()) {
                body.setExample(example);
            }
            return;
        }

        // 判断 childClass 是否已经在根节点到当前节点的链表上存在, 存在的话则不继续递归
        if (StringUtils.isBlank(qualifiedName) || checkLinkedListHasTypeClass(body, qualifiedName, type)) {
            return;
        }

        String typeIdentity = typeIdentity(type, qualifiedName);
        if (context.isDepthLimitReached() || context.hasVisited(typeIdentity)) {
            return;
        }
        Map<String, PsiType> fieldGenericsMap = null;
        PsiClass childClass;
        Body parentBody;
        DtoParsingContext childContext;
        // List Set or HashSet
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {

            PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);

            body.setCollection(true);

            if (iterableType == null) {
                return;
            }

            childClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
            if (childClass == null) {
                return;
            }
            // 基本类型或者包装类型：不要创建 name="" 的子节点，否则 JSON5 会生成 {"":"..."}
            // 这里保持字段本身是 collection，仅设置 example 为元素示例，渲染层会输出 [example]
            JsonWireType contentWireType = JacksonPsiUtils.resolveContentUsing(member, iterableType);
            if (iterableType instanceof PsiPrimitiveType
                    || FieldTypeConstant.FIELD_TYPE.containsKey(iterableType.getPresentableText())) {
                if (contentWireType.isOverridden()) {
                    applyCollectionContentOverride(body, contentWireType);
                    return;
                }
                Object defaultValue = iterableType instanceof PsiPrimitiveType
                        ? PsiTypesUtil.getDefaultValue(iterableType)
                        : FieldTypeConstant.FIELD_TYPE.get(iterableType.getPresentableText());
                body.setExample(defaultValue == null ? "null" : String.valueOf(defaultValue));
                return;
            } else {
                parentBody = buildFieldGenericsBody("element", childClass, body);
                parentBody.setCollection(true);
                String childTypeIdentity = typeIdentity(iterableType, childClass.getQualifiedName());
                if (context.hasVisited(childTypeIdentity)) {
                    return;
                }
                childContext = context.descend(childTypeIdentity);

                // 集合参数构建, 集合就一个参数, 泛型 E
                fieldGenericsMap = CustomPsiUtils.getGenericsMap((PsiClassType) iterableType);
                isProto = ProtoUtils.isProto(iterableType);
            }
        } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
            // HashMap or Map 的泛型获取 value
            PsiType matKeyType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);

            // key 只能是 包装类型或者 String
            if (matKeyType == null || !FieldTypeConstant.PACKAGE_TYPE_SET.contains(matKeyType.getPresentableText())) {
                return;
            }
            PsiClass matKeyClass = PsiUtil.resolveClassInClassTypeOnly(matKeyType);
            if (matKeyClass == null) {
                return;
            }

            buildFieldGenericsBody("key", matKeyClass, body).setMap(true);

            // Value
            PsiType matValueType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);

            if (ignoreField(matValueType)) {
                return;
            }
            childClass = PsiUtil.resolveClassInClassTypeOnly(matValueType);
            if (childClass == null) {
                return;
            }

            fieldGenericsMap = CustomPsiUtils.getGenericsMap((PsiClassType) matValueType);
            parentBody = buildFieldGenericsBody("value", childClass, body);
            isProto = ProtoUtils.isProto(matValueType);

            parentBody.setMap(true);
            String childTypeIdentity = typeIdentity(matValueType, childClass.getQualifiedName());
            if (context.hasVisited(childTypeIdentity)) {
                return;
            }
            childContext = context.descend(childTypeIdentity);

        } else if (fieldClass == null || fieldClass.isEnum() || fieldClass.isInterface() || fieldClass.isAnnotationType()) {
            // 字段是类, 也可能带泛型
            return;

        } else {
            // 当前字段的泛型 和当前字段
            fieldGenericsMap = CustomPsiUtils.getGenericsMap((PsiClassType) type);
            parentBody = body;
            childClass = fieldClass;
            childContext = context.descend(typeIdentity);
        }

        if (childContext.isDepthLimitReached()) {
            return;
        }

        if (FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText()) || isProtoMap) {
            return;
        }
        buildBodyProperties(childClass, fieldGenericsMap, parentBody, parentChildPair, isProto, childContext);

        } finally {
            // 出栈：退出时从路径栈中移除，允许同类型字段在不同分支中各自被完整解析
            parentChildPair.remove(pair);
        }
    }

    @NotNull
    private static Body buildFieldGenericsBody(String name, PsiClass genericsClass, Body parent) {
        Body listBody = new Body();
        listBody.setRequired(true);
        listBody.setSyntheticContainerNode(true);
        listBody.setName(name);
        listBody.setPsiElement(genericsClass);
        listBody.setType(genericsClass.getName());
        // 设置全限定类名，避免 checkLinkedListHasTypeClass 环检测因 qualifiedNameForClassType 为 null 而失效
        listBody.setQualifiedNameForClassType(genericsClass.getQualifiedName());
        listBody.setDesc("");
        listBody.setParent(parent);

        parent.getChildList().add(listBody);
        return listBody;
    }

    /** Array examples hold an element sample, not a Java array initializer expression. */
    private static boolean setScalarArrayItemExample(Body body, PsiType itemType) {
        if (!(itemType instanceof PsiPrimitiveType) && !FieldTypeConstant.FIELD_TYPE.containsKey(itemType.getPresentableText())) {
            return false;
        }
        Object sample = itemType instanceof PsiPrimitiveType ? PsiTypesUtil.getDefaultValue(itemType)
                : FieldTypeConstant.FIELD_TYPE.get(itemType.getPresentableText());
        body.setExample(sample == null ? "null" : String.valueOf(sample));
        return true;
    }

    static void applyCollectionContentOverride(@NotNull Body body, @NotNull JsonWireType contentWireType) {
        if (!contentWireType.isOverridden()) {
            return;
        }

        String collectionType = StringUtils.defaultString(body.getType());
        int genericStart = collectionType.indexOf('<');
        int genericEnd = collectionType.lastIndexOf('>');
        if (genericStart >= 0 && genericEnd > genericStart) {
            body.setType(collectionType.substring(0, genericStart + 1)
                    + contentWireType.getJsonType()
                    + collectionType.substring(genericEnd));
        }

        String example = contentWireType.getExampleOverride() != null
                ? contentWireType.getExampleOverride() : "0";
        body.setExample(example);
    }

    private static String typeIdentity(@NotNull PsiType type, String qualifiedName) {
        return StringUtils.defaultIfBlank(qualifiedName, type.getCanonicalText()) + "<" + type.getCanonicalText() + ">";
    }

    /**
     * 检查从当前节点到根节点的链表上是否存在当前类型的节点, 存在则说明递归了
     *
     * @param body
     * @param qualifiedName
     * @return
     */
    public static boolean checkLinkedListHasTypeClass(@NotNull Body body, @NotNull String qualifiedName) {

        Body temp = body.getParent();

        while (temp != null) {
            if (qualifiedName.equals(temp.getQualifiedNameForClassType())) {
                return true;
            }
            temp = temp.getParent();
        }

        return false;

    }

    /**
     * 检查从当前节点到根节点的链表上是否存在当前类型的节点, 存在则说明递归了
     * <p>
     * 排除多个List，Map类型只能递归到第一个集合
     * eg:
     * class A {
     * List<B> bs;
     * public static class B {
     * String b1;
     * String b2
     * List<C> cs;
     * }
     * <p>
     * public static class C {
     * String c1;
     * String c2
     * }
     * }
     * <p>
     * 只能展示 B 类型的字段，C 类型的字段不展示
     *
     * @param body
     * @param qualifiedName
     * @param type
     * @return
     */
    public static boolean checkLinkedListHasTypeClass(@NotNull Body body, @NotNull String qualifiedName, @NotNull PsiType type) {
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
                InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
            return false;
        }

        Body temp = body.getParent();

        while (temp != null) {
            // 需要去除jdk的集合、数组等

            if (qualifiedName.equals(temp.getQualifiedNameForClassType())) {
                return true;
            }
            temp = temp.getParent();
        }

        return false;

    }

    public static Map<String, Object> getFieldsAndDefaultValue(PsiClass psiClass, Map<String, PsiType> genericMap,
                                                               LinkedList<String> qualifiedNameList) {
        return PsiJsonExamples.getFieldsAndDefaultValue(psiClass, genericMap, qualifiedNameList);
    }

    @NotNull
    public static Map<String, Object> getFieldsAndDefaultValue(PsiClass psiClass, Map<String, PsiType> genericMap) {
        return PsiJsonExamples.getFieldsAndDefaultValue(psiClass, genericMap);
    }

    public static Map<String, Object> getFieldsAndDefaultValueForRecord(@NotNull PsiClass psiClass,
                                                                        Map<String, PsiType> genericMap) {
        return PsiJsonExamples.getFieldsAndDefaultValueForRecord(psiClass, genericMap);
    }

    public static Map<String, Object> getFieldsAndDefaultValueForRecord(@NotNull PsiClass psiClass,
                                                                        Map<String, PsiType> genericMap,
                                                                        @NotNull LinkedList<String> qualifiedNameList) {
        return PsiJsonExamples.getFieldsAndDefaultValueForRecord(psiClass, genericMap, qualifiedNameList);
    }

    /**
     * 构造返回 body
     *
     * @param returnType 返回类型
     * @return 返回结果
     */
    @NotNull
    public static Body buildRespBody(PsiType returnType) {
        Body root = new Body();

        if (returnType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(returnType.getPresentableText())) {
            Body body = new Body();
            body.setRequired(false);
            body.setName(null);
            body.setType(returnType.getPresentableText());
            body.setParent(root);
            root.getChildList().add(body);
            return root;
        }
        boolean isProto = ProtoUtils.isProto(returnType);

        if (returnType instanceof PsiClassType psiClassType) {
            PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
            if (psiClass != null) {
                root.setQualifiedNameForClassType(psiClass.getQualifiedName());

                // Map 类型处理
                if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                    Body collectionBody = new Body();
                    collectionBody.setRequired(true);
                    collectionBody.setName("");
                    collectionBody.setPsiElement(psiClass);
                    collectionBody.setType(returnType.getPresentableText());
                    collectionBody.setDesc("");
                    collectionBody.setParent(root);
                    root.getChildList().add(collectionBody);
                    PsiType[] parameters = psiClassType.getParameters();
                    if (parameters.length != 0) {
                        PsiType psiType = parameters[0];

                        if (psiType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(psiType.getPresentableText())) {
                            return root;
                        }
                        // 泛型是类
                        PsiClass genericsPsiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
                        if (genericsPsiClass != null) {
                            Map<String, PsiType> elementGenerics = psiType instanceof PsiClassType classType
                                    ? CustomPsiUtils.getGenericsMap(classType) : null;
                            buildBodyList(genericsPsiClass, elementGenerics, collectionBody, isProto);
                        }
                    }
                } else {
                    // 返回值可能是带泛型的, psiClassType.getParameters() 获取到的
                    Map<String, PsiType> genericMap = CustomPsiUtils.getGenericsMap(psiClassType);
                    buildBodyList(psiClass, genericMap, root, isProto);
                }
            }
        }
        return root;
    }

    public static void buildBodyList(@NotNull PsiClass psiClass, Map<String, PsiType> genericMap, Body parent) {
        buildBodyList(psiClass, genericMap, parent, ProtoUtils.isProto(PsiTypesUtil.getClassType(psiClass)));
    }

    public static void buildBodyList(@NotNull PsiClass psiClass, Map<String, PsiType> genericMap, Body parent, boolean isProto) {
        buildBodyList(psiClass, genericMap, parent, isProto, DtoParsingContext.root(DEFAULT_MAX_SCHEMA_DEPTH));
    }

    public static void buildBodyList(@NotNull PsiClass psiClass, Map<String, PsiType> genericMap, Body parent,
                                     DtoParsingContext context) {
        buildBodyList(psiClass, genericMap, parent, ProtoUtils.isProto(PsiTypesUtil.getClassType(psiClass)), context);
    }

    private static void buildBodyList(@NotNull PsiClass psiClass, Map<String, PsiType> genericMap, Body parent,
                                      boolean isProto, DtoParsingContext context) {
        buildBodyProperties(psiClass, genericMap, parent, new HashMap<>(), isProto, context);
    }

    private static void buildBodyProperties(PsiClass psiClass, Map<String, PsiType> generics, Body parent,
                                            Map<String, Boolean> parentChildPair, boolean proto, DtoParsingContext context) {
        ProgressManager.checkCanceled();
        JacksonPropertyProjection projection = new JacksonPropertyProjection(psiClass, proto);
        if (psiClass.isRecord()) {
            for (PsiRecordComponent component : psiClass.getRecordComponents()) {
                ProgressManager.checkCanceled();
                buildBodyParamFromComponent(psiClass, component, generics, parent, parentChildPair, context);
            }
            return;
        }
        for (PsiDocCommentOwner member : projection.members(proto)) {
            buildBodyProperty(psiClass, member, generics, parent, parentChildPair, proto, context, projection);
        }
    }

    public static void buildBodyParamFromComponent(@NotNull PsiClass parentClass, @NotNull PsiRecordComponent component,
                                                   Map<String, PsiType> genericsMap, @NotNull Body parent,
                                                   @NotNull Map<String, Boolean> parentChildPair) {
        buildBodyParamFromComponent(parentClass, component, genericsMap, parent, parentChildPair,
                DtoParsingContext.root(DEFAULT_MAX_SCHEMA_DEPTH));
    }

    private static void buildBodyParamFromComponent(@NotNull PsiClass parentClass, @NotNull PsiRecordComponent component,
                                                    Map<String, PsiType> genericsMap, @NotNull Body parent,
                                                    @NotNull Map<String, Boolean> parentChildPair, DtoParsingContext context) {
        ProgressManager.checkCanceled();
        JacksonPropertyProjection projection = new JacksonPropertyProjection(parentClass, false);
        String name = projection.name(component);
        if (!projection.includes(component, name)) {
            return;
        }
        String parentQName = parentClass.getQualifiedName() != null ? parentClass.getQualifiedName() : "";
        String pair = parentQName + "_" + component.getName();
        if (parentChildPair.containsKey(pair)) {
            return;
        }
        parentChildPair.put(pair, true);
        try {
            PsiType type = component.getType();
            Body body = new Body();
            body.setRequired(DocViewUtils.isRequired(component));
            body.setName(name);
            body.setPsiElement(component);
            body.setDescription(DocViewUtils.fieldDescription(component));
            PsiField recordField = parentClass.findFieldByName(component.getName(), false);
            if (recordField != null) {
                body.setSince(CustomPsiCommentUtils.tagDocComment(recordField.getDocComment(), "since"));
                body.setVersion(CustomPsiCommentUtils.tagDocComment(recordField.getDocComment(), "version"));
            }

            type = replaceFieldType(genericsMap, type);
            JsonWireType wireType = JacksonPsiUtils.resolveJsonWireType(component, type);
            body.setType(wireType.isOverridden() ? wireType.getJsonType() : type.getPresentableText());
            String qualifiedName = wireType.isOverridden() ? wireType.getJsonType() : type.getPresentableText();
            body.setQualifiedNameForClassType(qualifiedName);
            PsiClass fieldClass = PsiUtil.resolveClassInType(type);
            if (wireType.isOverridden() && wireType.getExampleOverride() != null) {
                body.setExample(wireType.getExampleOverride());
            }

            body.setParent(parent);
            parent.getChildList().add(body);

            JsonWireType componentWireType = JacksonPsiUtils.resolveJsonWireType(component, type);
            if (componentWireType.isOverridden() && isJsonSimpleType(componentWireType.getJsonType())) {
                if (componentWireType.getExampleOverride() != null) {
                    body.setExample(componentWireType.getExampleOverride());
                }
                return;
            }

            if (type instanceof PsiArrayType) {
                body.setCollection(true);
                while (type instanceof PsiArrayType arrayType) {
                    type = arrayType.getComponentType();
                }
                fieldClass = PsiUtil.resolveClassInType(type);
                qualifiedName = type.getCanonicalText();
                body.setQualifiedNameForClassType(qualifiedName);
                if (setScalarArrayItemExample(body, type)) {
                    return;
                }
            }
            if (type instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
                if (projection.omitsNull(component)) {
                    Object sample = type instanceof PsiPrimitiveType ? PsiTypesUtil.getDefaultValue(type)
                            : FieldTypeConstant.FIELD_TYPE.get(type.getPresentableText());
                    body.setOmitFromExample(sample == null);
                    body.setExample(sample == null ? null : String.valueOf(sample));
                }
                return;
            }

            if (StringUtils.isBlank(qualifiedName) || checkLinkedListHasTypeClass(body, qualifiedName, type)) {
                return;
            }

            String typeIdentity = typeIdentity(type, qualifiedName);
            if (context.isDepthLimitReached() || context.hasVisited(typeIdentity)) {
                return;
            }
            DtoParsingContext childContext;
            Map<String, PsiType> fieldGenericsMap;
            PsiClass childClass;
            Body parentBody;

            if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                body.setCollection(true);
                if (iterableType == null) {
                    return;
                }
                childClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                if (childClass == null) {
                    return;
                }
                JsonWireType contentWireType = JacksonPsiUtils.resolveContentUsing(component, iterableType);
                if (iterableType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(iterableType.getPresentableText())) {
                    if (contentWireType.isOverridden()) {
                        applyCollectionContentOverride(body, contentWireType);
                        return;
                    }
                    Object defaultValue = iterableType instanceof PsiPrimitiveType
                            ? PsiTypesUtil.getDefaultValue(iterableType)
                            : FieldTypeConstant.FIELD_TYPE.get(iterableType.getPresentableText());
                    body.setExample(defaultValue == null ? "null" : String.valueOf(defaultValue));
                    return;
                }
                parentBody = buildFieldGenericsBody("element", childClass, body);
                parentBody.setCollection(true);
                String childIdentity = typeIdentity(iterableType, childClass.getQualifiedName());
                if (context.hasVisited(childIdentity)) {
                    return;
                }
                childContext = context.descend(childIdentity);
                fieldGenericsMap = CustomPsiUtils.getGenericsMap((PsiClassType) iterableType);
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
                PsiType matKeyType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);
                if (matKeyType == null || !FieldTypeConstant.PACKAGE_TYPE_SET.contains(matKeyType.getPresentableText())) {
                    return;
                }
                PsiClass matKeyClass = PsiUtil.resolveClassInClassTypeOnly(matKeyType);
                if (matKeyClass == null) {
                    return;
                }
                buildFieldGenericsBody("key", matKeyClass, body).setMap(true);
                PsiType matValueType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
                if (ignoreField(matValueType)) {
                    return;
                }
                childClass = PsiUtil.resolveClassInClassTypeOnly(matValueType);
                if (childClass == null) {
                    return;
                }
                fieldGenericsMap = CustomPsiUtils.getGenericsMap((PsiClassType) matValueType);
                parentBody = buildFieldGenericsBody("value", childClass, body);
                parentBody.setMap(true);
                String childIdentity = typeIdentity(matValueType, childClass.getQualifiedName());
                if (context.hasVisited(childIdentity)) {
                    return;
                }
                childContext = context.descend(childIdentity);
            } else if (fieldClass == null || fieldClass.isEnum() || fieldClass.isInterface() || fieldClass.isAnnotationType()) {
                return;
            } else {
                fieldGenericsMap = CustomPsiUtils.getGenericsMap((PsiClassType) type);
                parentBody = body;
                childClass = fieldClass;
                childContext = context.descend(typeIdentity);
            }

            if (childContext.isDepthLimitReached()) {
                return;
            }
            if (FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
                return;
            }

            buildBodyProperties(childClass, fieldGenericsMap, parentBody, parentChildPair, false, childContext);
        } finally {
            parentChildPair.remove(pair);
        }
    }


    @NotNull
    public static String getRespBodyJson(PsiType returnType) {
        return PsiJsonExamples.getRespBodyJson(returnType);
    }

    public static final class DtoParsingContext {
        private final int maxDepth;
        private final int depth;
        private final Set<String> visitedTypes;

        private DtoParsingContext(int maxDepth, int depth, Set<String> visitedTypes) {
            this.maxDepth = maxDepth;
            this.depth = depth;
            this.visitedTypes = visitedTypes;
        }

        public static DtoParsingContext root(int maxDepth) {
            return new DtoParsingContext(maxDepth, 0, new LinkedHashSet<>());
        }

        public boolean isDepthLimitReached() {
            return depth >= maxDepth;
        }

        public boolean hasVisited(@NotNull String typeIdentity) {
            return visitedTypes.contains(typeIdentity);
        }

        public DtoParsingContext descend(@NotNull String typeIdentity) {
            Set<String> childVisitedTypes = new LinkedHashSet<>(visitedTypes);
            childVisitedTypes.add(typeIdentity);
            return new DtoParsingContext(maxDepth, depth + 1, childVisitedTypes);
        }
    }

}
