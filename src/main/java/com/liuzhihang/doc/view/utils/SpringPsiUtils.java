package com.liuzhihang.doc.view.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.constant.SpringConstant;
import com.liuzhihang.doc.view.constant.ValidationConstant;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.liuzhihang.doc.view.constant.MethodConstant.*;

/**
 * Spring 相关操作工具类
 *
 * @author liuzhihang
 * @date 2020/3/4 19:45
 */
public class SpringPsiUtils extends ParamPsiUtils {

    /**
     * 检查类或者接口是否是 Spring 接口
     *
     * @param psiClass
     * @return
     */
    public static boolean isSpringClass(@NotNull PsiClass psiClass) {

        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {

            Settings settings = Settings.getInstance(psiClass.getProject());

            return AnnotationUtil.isAnnotated(psiClass, settings.getContainClassAnnotationName(), 0);
        });

    }

    /**
     * 检查方法是否满足 Spring 相关条件
     * <p>
     * 不是构造方法, 且 公共 非静态, 有相关注解
     *
     * @param psiMethod
     * @return true 是spring 方法
     */
    public static boolean isSpringMethod(@NotNull PsiMethod psiMethod) {

        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            Settings settings = Settings.getInstance(psiMethod.getProject());

            return !psiMethod.isConstructor() && CustomPsiUtils.hasModifierProperty(psiMethod, PsiModifier.PUBLIC) && !CustomPsiUtils.hasModifierProperty(psiMethod, PsiModifier.STATIC) && AnnotationUtil.isAnnotated(psiMethod, settings.getContainMethodAnnotationName(), 0);
        });

    }

    /**
     * 从 module 中获取所有符合生成 DocView 文档的类
     *
     * @param module 项目 Module
     * @return 所有符合 DocView 文档类
     */
    public static List<PsiClass> findDocViewFromModule(Module module) {

        Collection<PsiAnnotation> psiAnnotations = JavaAnnotationIndex.getInstance().get("Controller", module.getProject(), GlobalSearchScope.moduleScope(module));
        Collection<PsiAnnotation> restController = JavaAnnotationIndex.getInstance().get("RestController", module.getProject(), GlobalSearchScope.moduleScope(module));
        psiAnnotations.addAll(restController);
        List<PsiClass> psiClasses = new LinkedList<>();

        for (PsiAnnotation psiAnnotation : psiAnnotations) {
            PsiModifierList psiModifierList = (PsiModifierList) psiAnnotation.getParent();
            PsiElement psiElement = psiModifierList.getParent();

            if (psiElement instanceof PsiClass && isSpringClass((PsiClass) psiElement)) {
                psiClasses.add((PsiClass) psiElement);
            }
        }
        return psiClasses;
    }

    @NotNull
    public static String method(PsiMethod psiMethod) {

        String method;

        if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.GET_MAPPING, 0)) {
            method = GET;
        } else if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.POST_MAPPING, 0)) {
            method = POST;
        } else if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.PUT_MAPPING, 0)) {
            method = PUT;
        } else if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.DELETE_MAPPING, 0)) {
            method = DELETE;
        } else if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.PATCH_MAPPING, 0)) {
            method = PATCH;
        } else if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.REQUEST_MAPPING, 0)) {
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiMethod, SpringConstant.REQUEST_MAPPING);
            if (annotation == null) {
                method = GET;
            } else {
                String value = AnnotationUtil.getStringAttributeValue(annotation, "method");
                method = value == null ? GET : value;
            }
        } else {
            method = GET;
        }

        return method.toUpperCase();
    }

    /**
     * 从类注解中解析出请求路径
     *
     * @param psiClass  类
     * @param psiMethod 方法
     * @return 路径, 路径开头为 /
     * @see SpringPsiUtils#path(PsiAnnotation)
     */
    @NotNull
    public static String path(PsiClass psiClass, @NotNull PsiMethod psiMethod) {

        String classPath = classPath(psiClass);
        String methodPath = methodPath(psiMethod);

        if (StringUtils.isBlank(classPath)) {
            return methodPath;
        }

        if (StringUtils.isBlank(methodPath)) {
            return classPath;
        }

        // 拼接最终结果
        return classPath + methodPath;
    }

    /**
     * 获取类的路径, 比如 @RequestMapping("/xxx")
     *
     * @param psiClass 类
     * @return 路径
     */
    @NotNull
    public static String classPath(PsiClass psiClass) {
        // controller 路径
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiClass, SpringConstant.REQUEST_MAPPING);

        return path(annotation);
    }

    /**
     * 根据方法获取请求路径, 就是方法注解中写的路径
     *
     * @param psiMethod 方法
     * @return 请求方式
     */
    public static String methodPath(PsiMethod psiMethod) {

        String url = "";

        // 是否包含 Spring xxxMapping 注解
        if (AnnotationUtil.isAnnotated(psiMethod, SpringConstant.MAPPING_ANNOTATIONS, 0)) {
            url = path(AnnotationUtil.findAnnotation(psiMethod, SpringConstant.MAPPING_ANNOTATIONS));
        }
        return url;
    }

    /**
     * 从注解中解析路径, 路径结构都为 /xxx
     * <p>
     * 开头为 /
     * 结束没有 /
     * 空时为 ""
     *
     * @param annotation 需要解析路径的注解, 可能是 xxxController 也可能是 xxxMapping
     * @return 注解 value 字段对应的路径
     */
    @NotNull
    private static String path(PsiAnnotation annotation) {
        if (annotation == null) {
            return "";
        }
        // 获取注解中 value 字段对应的值
        String path = AnnotationUtil.getStringAttributeValue(annotation, "value");
        if (path != null) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            return path;
        }
        return "";
    }

    /**
     * 获取当前方法的 Context-Type
     *
     * @param psiMethod 方法
     * @return Context-Type
     */
    public static ContentTypeEnum contentType(@NotNull PsiMethod psiMethod) {

        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

        for (PsiParameter parameter : parameters) {

            // 通用排除字段
            if (DocViewUtils.isExcludeParameter(parameter)) {
                continue;
            }

            if (AnnotationUtil.isAnnotated(parameter, SpringConstant.REQUEST_BODY, 0)) {
                return ContentTypeEnum.JSON;
            }
        }
        return ContentTypeEnum.FORM;
    }

    /**
     * 获取被 RequestBody 注解修饰的参数, 只需要获取第一个即可, 因为多个不会生效.
     *
     * @param psiMethod 方法
     * @return 被 @RequestBody 修饰的参数
     */
    public static PsiParameter requestBodyParam(@NotNull PsiMethod psiMethod) {
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

        for (PsiParameter parameter : parameters) {

            // 通用排除字段
            if (DocViewUtils.isExcludeParameter(parameter)) {
                continue;
            }

            if (AnnotationUtil.isAnnotated(parameter, SpringConstant.REQUEST_BODY, 0)) {
                return parameter;
            }
        }
        return null;
    }

    /**
     * 构建 Header
     *
     * @param psiMethod
     * @return
     */
    @NotNull
    public static List<Header> buildHeader(@NotNull PsiMethod psiMethod) {
        List<Header> list = new ArrayList<>();

        // 先设置 header 中的 contentType
        ContentTypeEnum contentType = contentType(psiMethod);
        Header contentTypeHeader = new Header();
        contentTypeHeader.setRequired(true);
        contentTypeHeader.setName(contentType.getKey());
        contentTypeHeader.setValue(contentType.getValue());
        list.add(contentTypeHeader);

        // 判断有无 @Header 注解的参数
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

        for (PsiParameter parameter : parameters) {

            if (!AnnotationUtil.isAnnotated(parameter, SpringConstant.REQUEST_HEADER, 0)) {
                continue;
            }

//            PsiType type = parameter.getType();

            // 不是 String 就不处理了
//            if (!"String".equals(type.getPresentableText())) {
//                continue;
//            }
            PsiAnnotation requestHeader = AnnotationUtil.findAnnotation(parameter, SpringConstant.REQUEST_HEADER);

            List<JvmAnnotationAttribute> attributes = requestHeader.getAttributes();

            String headerName = parameter.getName();
            String defaultValue = null;
            boolean required = true;
            for (JvmAnnotationAttribute attribute : attributes) {
                if ("name".equals(attribute.getAttributeName()) || "value".equals(attribute.getAttributeName())) {
//                    获取 headerName
                    headerName = Objects.requireNonNull(((JvmAnnotationConstantValue) Objects.requireNonNull(attribute.getAttributeValue())).getConstantValue()).toString();
                }
                if ("defaultValue".equals(attribute.getAttributeName())) {
                    defaultValue = Objects.requireNonNull(((JvmAnnotationConstantValue) Objects.requireNonNull(attribute.getAttributeValue())).getConstantValue()).toString();
                }
                if ("required".equals(attribute.getAttributeName())) {
                    required = Boolean.parseBoolean(Objects.requireNonNull(((JvmAnnotationConstantValue) Objects.requireNonNull(attribute.getAttributeValue())).getConstantValue()).toString());
                }
            }

            Header header = new Header();
            header.setRequired(required);
            header.setName(headerName);
            header.setValue(defaultValue);
            list.add(header);
        }
        return list;
    }

    /**
     * 构建请求 body
     *
     * @param parameter 请求参数
     * @return Body
     */
    @NotNull
    public static Body buildBody(@NotNull PsiParameter parameter) {
        Body root = new Body();
        PsiType type = parameter.getType();
        // 基本类型
        if (type instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
            Body body = new Body();
            body.setRequired(DocViewUtils.isRequired(parameter));
            body.setName(parameter.getName());
            body.setType(parameter.getType().getPresentableText());
            body.setParent(root);
            // 子集合只有一个
            root.getChildList().add(body);
            return root;
        }
        boolean isProto = ProtoUtils.isProto(type);

        // 对象类型：对对象进行解析
        PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass != null) {
            root.setQualifiedNameForClassType(psiClass.getQualifiedName());
            // 获取请求的参数中，是否存在泛型，将泛型与原始对象存储到 map 中
            PsiClassType psiClassType = (PsiClassType) type;
            Map<String, PsiType> genericsMap = CustomPsiUtils.getGenericsMap(psiClassType);
            for (PsiField field : psiClass.getAllFields()) {
                // 通用排除字段
                if (DocViewUtils.isExcludeField(field, isProto)) {
                    continue;
                }
                // 增加 genericsMap 参数传入，用于将泛型 T 替换为原始对象
                ParamPsiUtils.buildBodyParam(psiClass, field, genericsMap, root, new HashMap<>(), isProto);
            }
        }
        return root;
    }

    /**
     * 请求对象的 Json 格式
     *
     * @param parameter 参数
     * @return json 字符串
     */
    @NotNull
    public static String reqBodyJson(@NotNull PsiParameter parameter) {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        String name = parameter.getName();
        PsiType type = parameter.getType();

        if (type instanceof PsiPrimitiveType) {
            fieldMap.put(name, PsiTypesUtil.getDefaultValue(type));
        } else if (FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
            fieldMap.put(name, FieldTypeConstant.FIELD_TYPE.get(type.getPresentableText()));
        } else {
            PsiClass psiClass = PsiUtil.resolveClassInType(type);
            if (psiClass != null) {
                fieldMap = ParamPsiUtils.getFieldsAndDefaultValue(psiClass, null);
            }
        }

        return GsonFormatUtil.gsonFormat(fieldMap);

    }

    /**
     * 拼装为 kv 形式的键值对
     *
     * @param requestParam
     * @return
     */
    @NotNull
    public static String reqParamKV(List<Param> requestParam) {

        if (requestParam == null || requestParam.isEmpty()) {
            return "";
        }

        StringBuilder paramKV = new StringBuilder();

        for (Param param : requestParam) {
            paramKV.append("&").append(param.getName()).append("=").append(param.getExample());
        }

        return paramKV.substring(1);
    }

    /**
     * 解析方法中的参数
     *
     * @param psiMethod 方法
     * @return 参数
     */
    public static List<Param> buildFormParam(PsiMethod psiMethod) {

        if (!psiMethod.hasParameters()) {
            return null;
        }

        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

        Set<String> paramNameSet = new HashSet<>();
        List<Param> list = new ArrayList<>();
        for (PsiParameter parameter : parameters) {

            // 有 @RequestBody 注解
            if (AnnotationUtil.isAnnotated(parameter, SpringConstant.REQUEST_BODY, 0)) {
                continue;
            }

            // 有 @RequestHeader 注解
            if (AnnotationUtil.isAnnotated(parameter, SpringConstant.REQUEST_HEADER, 0)) {
                continue;
            }

            // 需要排除的字段
            if (DocViewUtils.isExcludeParameter(parameter)) {
                continue;
            }
            String parameterName = parameter.getName();
            if(AnnotationUtil.isAnnotated(parameter, SpringConstant.REQUEST_PARAM, 0)){
                PsiAnnotation requestParam = AnnotationUtil.findAnnotation(parameter, SpringConstant.REQUEST_PARAM);
                if (requestParam != null) {
                    List<JvmAnnotationAttribute> attributes = requestParam.getAttributes();
                    for (JvmAnnotationAttribute attribute : attributes) {
                        if ("name".equals(attribute.getAttributeName()) || "value".equals(attribute.getAttributeName())) {
//                    获取 headerName
                            parameterName = Objects.requireNonNull(((JvmAnnotationConstantValue) Objects.requireNonNull(attribute.getAttributeValue())).getConstantValue()).toString();
                        }
                    }
                }
            }

            // 已经包含该字段
            if (!paramNameSet.add(parameterName)) {
                continue;
            }

            PsiType type = parameter.getType();

            if (type instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
                list.add(buildPramFromParameter(psiMethod, parameter, parameterName));
            } else if (InheritanceUtil.isInheritor(type, "org.springframework.core.io.InputStreamSource")) {
                list.add(buildPramFromParameter(psiMethod, parameter, parameterName));
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                list.add(buildPramFromParameter(psiMethod, parameter, parameterName));
            } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
                list.add(buildPramFromParameter(psiMethod, parameter, parameterName));
            } else {
                PsiClass fieldClass = PsiUtil.resolveClassInClassTypeOnly(type);
                if (fieldClass == null) {
                    continue;
                }
                // 参数是类, get 请求只有一层
                PsiField[] psiFields = fieldClass.getAllFields();
                for (PsiField field : psiFields) {
                    // 已经包含该字段
                    if (!paramNameSet.add(parameterName)) {
                        continue;
                    }
                    if (field.getType() instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(field.getType().getPresentableText())) {
                        list.add(buildPramFromField(field));
                    }

                }
            }

        }
        return list;

    }

    @NotNull
    private static Param buildPramFromField(PsiField field) {

        Param param = new Param();
        param.setPsiElement(field);
        param.setRequired(DocViewUtils.isRequired(field));
        param.setName(field.getName());
        param.setDesc(DocViewUtils.fieldDesc(field));
        param.setExample(DocViewUtils.fieldExample(field));
        param.setType(field.getType().getPresentableText());

        return param;
    }

    @NotNull
    private static Param buildPramFromParameter(PsiMethod psiMethod, PsiParameter parameter, String parameterName) {

        Param param = new Param();
        param.setRequired(DocViewUtils.isRequired(parameter));
        param.setName(parameterName);
        param.setType(parameter.getType().getPresentableText());

        // 备注需要从注释中获取
        PsiDocComment docComment = psiMethod.getDocComment();
        if (docComment != null) {
            param.setDesc(CustomPsiCommentUtils.paramDocComment(docComment, parameter));
        }

        return param;
    }

    public static String getValidatedValue(@NotNull PsiField psiField) {
        StringBuilder validatedValue = new StringBuilder(" ");
        PsiAnnotation minAnnotation = AnnotationUtil.findAnnotation(psiField, ValidationConstant.MIN);
        if (minAnnotation != null) {
            validatedValue.append("最小值:").append(AnnotationUtil.getLongAttributeValue(minAnnotation, "value")).append(";");
        }
        PsiAnnotation maxAnnotation = AnnotationUtil.findAnnotation(psiField, ValidationConstant.MAX);
        if (maxAnnotation != null) {
            validatedValue.append("最大值:").append(AnnotationUtil.getLongAttributeValue(maxAnnotation, "value")).append(";");
        }
        PsiAnnotation sizeAnnotation = AnnotationUtil.findAnnotation(psiField, ValidationConstant.SIZE);
        if (sizeAnnotation != null) {
            validatedValue.append("长度范围:").append("最小值:").append(AnnotationUtil.getLongAttributeValue(sizeAnnotation, "min")).append(",");
            validatedValue.append("最大值:").append(AnnotationUtil.getLongAttributeValue(sizeAnnotation, "max")).append(";");
        }
        return validatedValue.toString();
    }
}
