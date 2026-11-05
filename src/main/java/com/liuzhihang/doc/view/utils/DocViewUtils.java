package com.liuzhihang.doc.view.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.constant.JsonPropertyConstant;
import com.liuzhihang.doc.view.constant.SpringConstant;
import com.liuzhihang.doc.view.constant.SwaggerConstant;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.exception.DocViewException;
import com.liuzhihang.doc.view.service.impl.WriterService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * DocView 通用处理类
 * <p>
 * 从 settings 中获取配置,以及用户自定义的配置进行判断 统一返回
 *
 * @author liuzhihang
 * @date 2021/6/10 20:10
 */
public class DocViewUtils {

    private DocViewUtils() {
    }


    /**
     * 判断是否是 DocView  的类
     *
     * @param psiClass
     * @return
     */
    public static boolean isDocViewClass(@Nullable PsiClass psiClass) {

        if (psiClass == null || psiClass.isAnnotationType() || psiClass.isEnum()) {
            return false;
        }

        if (psiClass.isRecord()) {
            return true;
        }

        // Spring Controller 还需要检查方法是否满足条件
        if (SpringPsiUtils.isSpringClass(psiClass)) {
            return true;
        }

        if (psiClass.isInterface()) {
            return Settings.getInstance(psiClass.getProject()).getIncludeNormalInterface() || DubboPsiUtils.isDubboClass(psiClass) || FeignPsiUtil.isFeignClass(psiClass);
        }

        // 其他判断在下面添加

        return false;
    }

    /**
     * 判断当前方法是不是 Doc View 的方法
     *
     * @param psiMethod
     * @return
     */
    public static boolean isDocViewMethod(@Nullable PsiMethod psiMethod) {

        if (psiMethod == null) {
            return false;
        }

        if (SpringPsiUtils.isSpringMethod(psiMethod)) {
            return true;
        }

        if (DubboPsiUtils.isDubboMethod(psiMethod)) {
            return true;
        }

        // 其他判断在下面添加

        return false;
    }

    /**
     * 匿名类和内部类可能返回 null
     * <p>
     * 文档标题(接口分类):
     * 从类/类注释中获取标题
     *
     * @param psiClass
     * @return
     */
    @NotNull
    public static String getTitle(@NotNull PsiClass psiClass) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Settings settings = Settings.getInstance(psiClass.getProject());

            if (settings.getTitleUseCommentTag()) {
                // 注释 @DocView.Title
                String docTitleTagValue = CustomPsiCommentUtils.tagDocComment(psiClass.getDocComment(), settings.getTitleTag());

                if (StringUtils.isNotBlank(docTitleTagValue)) {
                    return docTitleTagValue;
                }
            }

            if (settings.getTitleClassComment()) {
                // 获取类注释

                String comment = CustomPsiCommentUtils.tagDocComment(psiClass.getDocComment());

                if (StringUtils.isNotBlank(comment)) {
                    return comment;
                }
            }

            if (settings.getTitleUseSimpleClassName()) {
                String className = psiClass.getName();
                if (StringUtils.isNotBlank(className)) {
                    return className;
                }
            }

            if (settings.getTitleUseFullClassName()) {
                // 获取全类名
                String fullClassName = psiClass.getQualifiedName();

                if (StringUtils.isNotBlank(fullClassName)) {
                    return fullClassName;
                }
            }

            return "DocView";
        });
    }

    /**
     * 获取方法名字:
     * <p>
     * 方法名字(接口标题):
     * <p>
     * 支持 Swagger/方法名/自定义注释 tag
     * <p>
     * 方法注释保留完整首行内容
     *
     * @param psiMethod 当前方法
     * @return
     */
    @NotNull
    public static String getName(@NotNull PsiMethod psiMethod) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Settings settings = Settings.getInstance(psiMethod.getProject());
            // swagger v3 @Operation
            if (settings.getNameUseSwagger3()) {
                PsiAnnotation tagAnnotation = psiMethod.getAnnotation(SwaggerConstant.OPERATION);
                if (tagAnnotation != null) {
                    String summary = AnnotationUtil.getStringAttributeValue(tagAnnotation, "summary");
                    if (StringUtils.isNotBlank(summary)) {
                        return summary;
                    }
                }
            }
            // swagger @ApiOperation
            if (settings.getNameUseSwagger()) {
                PsiAnnotation apiAnnotation = psiMethod.getAnnotation(SwaggerConstant.API_OPERATION);
                if (apiAnnotation != null) {
                    String value = AnnotationUtil.getStringAttributeValue(apiAnnotation, "value");
                    if (StringUtils.isNotBlank(value)) {
                        return value;
                    }
                }
            }

            // 注释上的 tag
            if (settings.getNameUseCommentTag()) {
                String comment = CustomPsiCommentUtils.tagDocComment(psiMethod.getDocComment(), settings.getNameTag());

                if (StringUtils.isNotBlank(comment)) {
                    return comment;
                }
            }

            if (settings.getNameMethodComment()) {

                // 方法注释
                String comment = CustomPsiCommentUtils.tagDocCommentForOneLine(psiMethod.getDocComment());

                if (StringUtils.isNotBlank(comment)) {

//                    if (comment.length() > 15) {
//                        comment = comment.substring(0, 15);
//                    }

                    return comment;
                }
            }

            return psiMethod.getName();
        });
    }

    /**
     * 获取方法描述, 方法描述直接获取注释
     * <p>
     * 可从 Settings 中按照配置获取
     *
     * @param psiMethod
     * @return
     */
    @NotNull
    public static String getMethodDesc(@NotNull PsiMethod psiMethod) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {

            Settings settings = Settings.getInstance(psiMethod.getProject());

            // 从 swagger3 中获取描述
            if (settings.getDescUseSwagger3()) {
                PsiAnnotation operationAnnotation = psiMethod.getAnnotation(SwaggerConstant.OPERATION);
                if (operationAnnotation != null) {
                    String description = AnnotationUtil.getStringAttributeValue(operationAnnotation, "description");
                    if (StringUtils.isNotBlank(description)) {
                        return description;
                    }
                }
            }
            // 先从 swagger 中获取描述
            if (settings.getDescUseSwagger()) {
                PsiAnnotation apiOperationAnnotation = psiMethod.getAnnotation(SwaggerConstant.API_OPERATION);
                if (apiOperationAnnotation != null) {
                    String notes = AnnotationUtil.getStringAttributeValue(apiOperationAnnotation, "notes");
                    if (StringUtils.isNotBlank(notes)) {
                        return notes;
                    }
                }
            }
            // 最后从注释中获取

            return CustomPsiCommentUtils.tagDocComment(psiMethod.getDocComment());
        });
    }

    /**
     * 判断是否是需要排除的字段
     *
     * @param psiField
     * @param isProto
     * @return 需要排除字段, 返回 true
     */
    @NotNull
    public static boolean isExcludeField(@NotNull PsiField psiField, boolean isProto) {

        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            Settings settings = Settings.getInstance(psiField.getProject());

//            proto类中的字段都是以_结尾的
            if (isProto&&!psiField.getName().endsWith("_")){
                return true;
            }

            if (settings.getExcludeFieldNames().contains(psiField.getName())) {
                return true;
            }
            // 排除掉被 static 修饰的字段
            if (CustomPsiUtils.hasModifierProperty(psiField, PsiModifier.STATIC)) {
                return true;
            }

            if (CustomPsiUtils.hasModifierProperty(psiField, PsiModifier.TRANSIENT)) {
                return true;
            }

            // 排除部分注解的字段
            if (AnnotationUtil.isAnnotated(psiField, settings.getExcludeFieldAnnotation(), 0)) {
                return true;
            }

            PsiClass containingClass = psiField.getContainingClass();
            if (containingClass == null) {
                return true;
            }

            return excludeClassPackage(containingClass, settings);
        });
    }

    /**
     * 是否在需要排除的包内
     *
     * @param psiClass
     * @param settings
     * @return 需要排除 返回 true
     */
    private static boolean excludeClassPackage(@NotNull PsiClass psiClass, @NotNull Settings settings) {

        String qualifiedName = psiClass.getQualifiedName();

        if (qualifiedName == null) {
            return true;
        }

        for (String packagePrefix : settings.getExcludeClassPackage()) {

            if (qualifiedName.startsWith(packagePrefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否是需要排除的字段
     *
     * @param psiParameter
     * @return
     */
    @NotNull
    public static boolean isExcludeParameter(@NotNull PsiParameter psiParameter) {

        Settings settings = Settings.getInstance(psiParameter.getProject());

        PsiType parameterType = psiParameter.getType();

        Set<String> excludeParameterTypeSet = settings.getExcludeParameterType();

        for (String excludeParameterType : excludeParameterTypeSet) {

            if (InheritanceUtil.isInheritor(parameterType, excludeParameterType)) {
                return true;
            }
        }

        if (settings.getExcludeFieldNames().contains(psiParameter.getName())) {
            return true;
        }

        return false;
    }

    /**
     * 判断字段是否必填
     *
     * @param field 字段
     * @return 是否必填
     */
    public static boolean isRequired(@NotNull PsiField field) {

        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            Settings settings = Settings.getInstance(field.getProject());

            Boolean schemaRequired = explicitSchemaRequired(field);
            if (schemaRequired != null) return schemaRequired;

            if (AnnotationUtil.isAnnotated(field, settings.getRequiredFieldAnnotation(), 0)) {
                return true;
            }
            // swagger @ApiModelProperty
            PsiAnnotation apiModelPropertyAnnotation = field.getAnnotation(SwaggerConstant.API_MODEL_PROPERTY);
            if (apiModelPropertyAnnotation != null) {
                PsiAnnotationMemberValue value = apiModelPropertyAnnotation.findAttributeValue("required");
                if (value != null && StringUtils.isNotBlank(value.getText()) && value.getText().contains("true")) {
                    return true;
                }
            }

            if (settings.getRequiredUseCommentTag()) {
                // 查看注释
                PsiDocComment docComment = field.getDocComment();

                if (docComment == null) {
                    // 没有注释, 非必填
                    return false;
                }

                PsiDocTag requiredTag = docComment.findTagByName(settings.getRequired());

                if (requiredTag != null) {
                    return true;
                }

            }

            return false;
        });

    }

    public static boolean isRequired(@NotNull PsiParameter psiParameter) {

        Settings settings = Settings.getInstance(psiParameter.getProject());

        // 必填标识
        if (AnnotationUtil.isAnnotated(psiParameter, settings.getRequiredFieldAnnotation(), 0)) {
            return true;
        }

        if (AnnotationUtil.isAnnotated(psiParameter, SpringConstant.REQUEST_PARAM, 0)) {
            PsiAnnotation annotation = psiParameter.getAnnotation(SpringConstant.REQUEST_PARAM);
            if (annotation != null) {
                // 没有设置注解参数
                PsiNameValuePair[] nameValuePairs = annotation.getParameterList().getAttributes();
                for (PsiNameValuePair nameValuePair : nameValuePairs) {
                    if (nameValuePair.getAttributeName().equalsIgnoreCase("required") && Objects.requireNonNull(nameValuePair.getLiteralValue()).equalsIgnoreCase("false")) {
                        return false;
                    }
                }
                return true;
            }
        }

        return false;
    }

    /**
     * 获取字段名称，需要处理注解，有些字段自己指定了名称
     *
     * @return 字段名称
     */
    public static String fieldName(PsiField field, boolean parentIsProto) {
        String fieldName = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Settings settings = Settings.getInstance(field.getProject());
            Boolean useJsonProperty = settings.getFieldNameJsonProperty();
            Boolean fieldNameUseSnakeCase = settings.getFieldNameCaseType();
            if (!Boolean.TRUE.equals(useJsonProperty)) {
                if (Boolean.TRUE.equals(fieldNameUseSnakeCase)) {
                    return camelToSnake(field.getName());
                }
                return field.getName();
            }
            // 判断是否有注解
            if (!AnnotationUtil.isAnnotated(field, settings.getFieldNameAnnotation(), 0)) {
                if (Boolean.TRUE.equals(fieldNameUseSnakeCase)) {
                    return camelToSnake(field.getName());
                }
                return field.getName();
            }
            // 从注解中解析字段名称
            PsiAnnotation jsonPropertyAnnotation = field.getAnnotation(JsonPropertyConstant.JSON_PROPERTY);
            if (jsonPropertyAnnotation != null) {
                PsiAnnotationMemberValue value = jsonPropertyAnnotation.findAttributeValue("value");
                Object resolved = PsiConstantUtils.constantValue(value);
                if (resolved instanceof String name && !name.isEmpty()) {
                    return name;
                }
            }
            if (Boolean.TRUE.equals(fieldNameUseSnakeCase)) {
                return camelToSnake(field.getName());
            }
            return field.getName();
        });
        if (parentIsProto&&fieldName.endsWith("_")){
//            proto类中的字段都是以_结尾的,去掉
            return fieldName.substring(0, fieldName.length() - 1);
        }
        return fieldName;
    }

    /**
     * 将 camelCase 转换为 snake_case
     *
     * @param camelCaseStr 输入的 camelCase 字符串
     * @return 转换后的 snake_case 字符串
     */
    public static String camelToSnake(String camelCaseStr) {
        if (camelCaseStr == null || camelCaseStr.isEmpty()) {
            return "";
        }
        // 使用正则表达式将大写字母替换为下划线+小写字母
        return camelCaseStr
                .replaceAll("([a-z])([A-Z])", "$1_$2") // 匹配小写字母和大写字母的边界
                .toLowerCase(); // 转换为小写
    }

    /**
     * snakeCase 转换为 PascalCase
     *
     * @param snakeCaseStr 蛇壳力量
     * @return {@link String }
     */
    public static String snakeToPascal(String snakeCaseStr) {
        if (snakeCaseStr == null || snakeCaseStr.isEmpty()) {
            return "";
        }
        // 使用正则表达式将下划线+小写字母替换为大写字母
        StringBuilder pascalCase = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : snakeCaseStr.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                pascalCase.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                pascalCase.append(Character.toLowerCase(c));
            }
        }

        return pascalCase.toString();
    }

    /**
     * 获取字段的描述
     * <p>
     * 优先从 swagger 中获取注释
     *
     * @param psiField
     * @return
     */
    @NotNull
    public static String fieldDesc(@NotNull PsiField psiField) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            // swagger v3 @Schema
            PsiAnnotation schemaAnnotation = psiField.getAnnotation(SwaggerConstant.SCHEMA);
            if (schemaAnnotation != null) {
                PsiAnnotationMemberValue descriptionValue = schemaAnnotation.findDeclaredAttributeValue("description");
                String description = evaluateStringConstant(psiField.getProject(), descriptionValue);
                if (description != null) {
                    return description;
                }
            }
            // swagger @ApiModelProperty
            PsiAnnotation apiModelPropertyAnnotation = psiField.getAnnotation(SwaggerConstant.API_MODEL_PROPERTY);
            if (apiModelPropertyAnnotation != null) {
                PsiAnnotationMemberValue value = apiModelPropertyAnnotation.findAttributeValue("value");
                if (value != null && StringUtils.isNotBlank(value.getText())) {
                    return value.getText().replace("\"", "");
                }
            }

            String getValidatedValue = SpringPsiUtils.getValidatedValue(psiField);

            PsiField documentationField = DependencySourceComments.documentationField(psiField);
            PsiComment comment = PsiTreeUtil.findChildOfType(documentationField, PsiComment.class);

            if (comment != null) {
                // param.setExample();
                // 参数举例, 使用 tag 判断; fieldComment 已统一支持 PsiDocComment 及 @see 枚举解析
                return CustomPsiCommentUtils.fieldComment(comment) + getValidatedValue;
            }
            return getValidatedValue;
        });
    }

    public static String fieldExample(@NotNull PsiField psiField) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            PsiComment comment = PsiTreeUtil.findChildOfType(psiField, PsiComment.class);

            if (comment != null) {
                // 参数举例, 优先使用 @value 注释 tag
                if (comment instanceof PsiDocComment) {
                    String tagValue = CustomPsiCommentUtils.tagValueFromDocComment((PsiDocComment) comment, "value");
                    if (StringUtils.isNotBlank(tagValue)) {
                        return tagValue;
                    }
                }
            }

            // 没有注释 tag 时, 回退读取字段的默认初始化值
            // 例如: int age = 15;  ->  "15"
            //       String name = "hello";  ->  "hello"
            String example = PsiConstantUtils.expressionText(psiField.getInitializer());
            if (example != null) {
                return example;
            }

            return "";
        });
    }

    // ── PsiRecordComponent overloads (JDK 16+ record support) ──────────────

    @NotNull
    public static String fieldDesc(@NotNull PsiRecordComponent component) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            // 1. @Schema annotation on component
            PsiAnnotation schemaAnnotation = component.getAnnotation(SwaggerConstant.SCHEMA);
            if (schemaAnnotation != null) {
                String description = evaluateStringConstant(component.getProject(),
                        schemaAnnotation.findDeclaredAttributeValue("description"));
                if (description != null) return description;
            }
            // 2. @ApiModelProperty annotation on component
            PsiAnnotation apiModelPropertyAnnotation = component.getAnnotation(SwaggerConstant.API_MODEL_PROPERTY);
            if (apiModelPropertyAnnotation != null) {
                PsiAnnotationMemberValue value = apiModelPropertyAnnotation.findAttributeValue("value");
                if (value != null && StringUtils.isNotBlank(value.getText())) {
                    return value.getText().replace("\"", "");
                }
            }
            String validatedValue = SpringPsiUtils.getValidatedValue(component);
            // 3. Inline comment directly on the component declaration
            PsiComment inlineComment = PsiTreeUtil.findChildOfType(component, PsiComment.class);
            if (inlineComment != null) {
                String desc = CustomPsiCommentUtils.fieldComment(inlineComment);
                if (StringUtils.isNotBlank(desc)) {
                    return desc + validatedValue;
                }
            }
            // 4. Class-level @param tag in the containing record's JavaDoc
            //    e.g.  /** @param name 名称 */  public record Foo(String name) {}
            PsiClass containingClass = component.getContainingClass();
            if (containingClass != null) {
                PsiDocComment classDoc = containingClass.getDocComment();
                if (classDoc != null) {
                    String paramDesc = recordComponentParamDesc(classDoc, component.getName());
                    if (StringUtils.isNotBlank(paramDesc)) {
                        return paramDesc + validatedValue;
                    }
                }
            }
            return validatedValue;
        });
    }

    private static String recordComponentParamDesc(@NotNull PsiDocComment classDoc, @NotNull String componentName) {
        for (PsiDocTag tag : classDoc.findTagsByName("param")) {
            if (tag.getValueElement() != null && componentName.equals(tag.getValueElement().getText().trim())) {
                return java.util.Arrays.stream(tag.getDataElements()).skip(1).map(PsiElement::getText)
                        .collect(java.util.stream.Collectors.joining(" ")).replaceAll("\\s+", " ").trim();
            }
        }
        return "";
    }

    public static String fieldName(@NotNull PsiRecordComponent component, boolean parentIsProto) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Settings settings = Settings.getInstance(component.getProject());
            Boolean useJsonProperty = settings.getFieldNameJsonProperty();
            Boolean fieldNameUseSnakeCase = settings.getFieldNameCaseType();
            if (!Boolean.TRUE.equals(useJsonProperty) || !AnnotationUtil.isAnnotated(component, settings.getFieldNameAnnotation(), 0)) {
                return Boolean.TRUE.equals(fieldNameUseSnakeCase) ? camelToSnake(component.getName()) : component.getName();
            }
            PsiAnnotation jsonPropertyAnnotation = component.getAnnotation(JsonPropertyConstant.JSON_PROPERTY);
            if (jsonPropertyAnnotation != null) {
                PsiAnnotationMemberValue value = jsonPropertyAnnotation.findAttributeValue("value");
                Object resolved = PsiConstantUtils.constantValue(value);
                if (resolved instanceof String name && !name.isEmpty()) {
                    return name;
                }
            }
            return Boolean.TRUE.equals(fieldNameUseSnakeCase) ? camelToSnake(component.getName()) : component.getName();
        });
    }

    public static boolean isRequired(@NotNull PsiRecordComponent component) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            Settings settings = Settings.getInstance(component.getProject());
            Boolean schemaRequired = explicitSchemaRequired(component);
            if (schemaRequired != null) return schemaRequired;
            if (AnnotationUtil.isAnnotated(component, settings.getRequiredFieldAnnotation(), 0)) {
                return true;
            }
            PsiAnnotation apiModelPropertyAnnotation = component.getAnnotation(SwaggerConstant.API_MODEL_PROPERTY);
            if (apiModelPropertyAnnotation != null) {
                PsiAnnotationMemberValue value = apiModelPropertyAnnotation.findAttributeValue("required");
                if (value != null && StringUtils.isNotBlank(value.getText()) && value.getText().contains("true")) {
                    return true;
                }
            }
            if (settings.getRequiredUseCommentTag()) {
                PsiDocComment docComment = PsiTreeUtil.findChildOfType(component, PsiDocComment.class);
                if (docComment != null && docComment.findTagByName(settings.getRequired()) != null) {
                    return true;
                }
            }
            return false;
        });
    }

    @Nullable
    private static Boolean explicitSchemaRequired(PsiModifierListOwner owner) {
        PsiAnnotation schema = owner.getAnnotation(SwaggerConstant.SCHEMA);
        if (schema == null) return null;
        PsiAnnotationMemberValue mode = schema.findAttributeValue("requiredMode");
        if (mode instanceof PsiReferenceExpression reference && reference.resolve() instanceof PsiEnumConstant constant) {
            PsiClass enumClass = constant.getContainingClass();
            if (enumClass != null && (SwaggerConstant.SCHEMA + ".RequiredMode").equals(enumClass.getQualifiedName())) {
                if ("REQUIRED".equals(constant.getName())) return true;
                if ("NOT_REQUIRED".equals(constant.getName())) return false;
            }
        }
        PsiClass schemaClass = schema.resolveAnnotationType();
        if (schemaClass != null && schemaClass.findMethodsByName("requiredMode", false).length == 0) {
            Object required = PsiConstantUtils.constantValue(schema.findAttributeValue("required"));
            if (Boolean.TRUE.equals(required)) return true;
        }
        return null;
    }

    /**
     * 变动的字段生成注释
     */
    public static void writeComment(Project project, @NotNull Map<PsiElement, DocViewParamData> modifyBodyMap) {

        // Validate every Schema capability before writing any field, so a missing annotation dependency
        // cannot leave a partially applied edit. Older Schema definitions use their existing required member.
        for (PsiElement element : modifyBodyMap.keySet()) {
            PsiField psiField = (PsiField) element;
            PsiAnnotation schemaAnnotation = psiField.getAnnotation(SwaggerConstant.SCHEMA);
            if (schemaAnnotation != null) {
                validateSchemaRequiredMode(schemaAnnotation);
            }
        }

        for (PsiElement element : modifyBodyMap.keySet()) {
            DocViewParamData data = modifyBodyMap.get(element);
            String docComment;

            PsiField psiField = (PsiField) element;

            // swagger v3 @Schema 直接修改属性
            PsiAnnotation schemaAnnotation = psiField.getAnnotation(SwaggerConstant.SCHEMA);
            if (schemaAnnotation != null) {
                writeSchemaRequiredMode(project, psiField, schemaAnnotation, data);
                continue;
            }
            // swagger @ApiModelProperty 直接修改属性
            PsiAnnotation apiModelPropertyAnnotation = psiField.getAnnotation(SwaggerConstant.API_MODEL_PROPERTY);
            if (apiModelPropertyAnnotation != null) {

                String annotationText = "";

                if (StringUtils.isNotBlank(data.getDesc()) && data.getRequired()) {
                    annotationText = "@ApiModelProperty(value = \"" + data.getDesc() + "\", required = true)";
                } else if (StringUtils.isNotBlank(data.getDesc()) && !data.getRequired()) {
                    annotationText = "@ApiModelProperty(\"" + data.getDesc() + "\")";
                } else if (StringUtils.isBlank(data.getDesc()) && data.getRequired()) {
                    annotationText = "@ApiModelProperty(required = true)";
                }

                if (StringUtils.isNotBlank(annotationText)) {
                    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                    PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText(annotationText, psiField);
                    // 调用生成逻辑
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        apiModelPropertyAnnotation.replace(newAnnotation);
                    });

                }
                return;
            }

            // 不修改原有注解
            if (!DocViewUtils.isRequired(psiField) && data.getRequired()) {
                docComment = "/** " + data.getDesc() + "\n" + "* @" + Settings.getInstance(project).getRequired() + " */";
            } else {
                docComment = "/** " + data.getDesc() + " */";
            }

            PsiComment comment = PsiTreeUtil.findChildOfType(psiField, PsiComment.class);

            if (comment != null && !(comment instanceof PsiDocComment)) {
                WriteCommandAction.runWriteCommandAction(project, comment::delete);
            }

            PsiElementFactory factory = PsiElementFactory.getInstance(project);
            PsiDocComment psiDocComment = factory.createDocCommentFromText(docComment);
            ApplicationManager.getApplication().getService(WriterService.class).write(project, element, psiDocComment);

        }
    }

    private static void writeSchemaRequiredMode(Project project, PsiField field,
                                                PsiAnnotation schemaAnnotation, DocViewParamData data) {
        validateSchemaRequiredMode(schemaAnnotation);

        List<String> members = new ArrayList<>();
        boolean hasDescription = false;
        boolean hasRequiredMode = false;
        String description = data.getDesc();
        String descriptionMember = "description = \""
                + StringUtil.escapeStringCharacters(description == null ? "" : description) + "\"";
        PsiClass schemaClass = schemaAnnotation.resolveAnnotationType();
        boolean supportsMode = schemaClass != null && schemaClass.findMethodsByName("requiredMode", false).length > 0;
        String modeMember = supportsMode
                ? "requiredMode = " + SwaggerConstant.SCHEMA + ".RequiredMode."
                    + (Boolean.TRUE.equals(data.getRequired()) ? "REQUIRED" : "NOT_REQUIRED")
                : "required = " + Boolean.TRUE.equals(data.getRequired());

        for (PsiNameValuePair pair : schemaAnnotation.getParameterList().getAttributes()) {
            String name = pair.getName();
            if ("required".equals(name)) {
                continue;
            }
            if ("requiredMode".equals(name)) {
                members.add(modeMember);
                hasRequiredMode = true;
                continue;
            }
            if ("description".equals(name)) {
                if (description != null) {
                    members.add(descriptionMember);
                    hasDescription = true;
                }
                continue;
            }
            members.add(pair.getText());
        }

        if (!hasDescription && description != null) {
            members.add(descriptionMember);
        }
        if (!hasRequiredMode) {
            members.add(modeMember);
        }

        String annotationText = "@" + SwaggerConstant.SCHEMA
                + (members.isEmpty() ? "" : "(" + String.join(", ", members) + ")");
        PsiAnnotation replacement = JavaPsiFacade.getElementFactory(project)
                .createAnnotationFromText(annotationText, field);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            schemaAnnotation.replace(replacement);
        });
    }

    private static void validateSchemaRequiredMode(PsiAnnotation schemaAnnotation) {
        PsiClass schemaClass = schemaAnnotation.resolveAnnotationType();
        if (schemaClass == null || (schemaClass.findMethodsByName("requiredMode", false).length == 0
                && schemaClass.findMethodsByName("required", false).length == 0)) {
            throw new DocViewException(DocViewBundle.message("schema.required.mode.unsupported"));
        }
    }

    @Nullable
    private static String evaluateStringConstant(Project project, @Nullable PsiAnnotationMemberValue value) {
        if (value == null) {
            return null;
        }
        Object evaluatedValue = JavaPsiFacade.getInstance(project)
                .getConstantEvaluationHelper().computeConstantExpression(value);
        return evaluatedValue instanceof String stringValue ? stringValue : null;
    }

}
