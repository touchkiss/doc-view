package com.liuzhihang.doc.view.utils;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * 从注释中解析注解的工具类
 *
 * @author liuzhihang
 * @date 2020/3/18 17:00
 */
public class CustomPsiCommentUtils {

    /**
     * 获取注释中 tagName 对应的注释, 如果指定 tagName 则直接从 tagName 里面获取
     *
     * @param docComment 注释 PsiDocComment
     * @param tagName    注释中的 @xxx 标签
     * @return 注释
     */
    @NotNull
    public static String tagDocComment(PsiDocComment docComment, String tagName) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            String comment = "";

            if (docComment == null) {
                return comment;
            }

            for (PsiElement element : docComment.getChildren()) {

                // 不是 tagName 则继续查找
                if (!("PsiDocTag:@" + tagName).equalsIgnoreCase(element.toString())) {
                    continue;
                }

                return element.getText().replace(("@" + tagName), StringUtils.EMPTY).trim();

            }

            return "";
        });


    }

    /**
     * 获取方法中字段的注释, 一般会用 @param 标注出来
     *
     * @param docComment 方法的注释 Psi
     * @param parameter  参数
     * @return 字段注释
     */
    @NotNull
    public static String paramDocComment(PsiDocComment docComment, @NotNull PsiParameter parameter) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {

            String comment = "";

            if (docComment == null) {
                return comment;
            }

            for (PsiElement element : docComment.getChildren()) {

                // 不是当前字段则继续循环
                if (!("PsiDocTag:@param").equalsIgnoreCase(element.toString())) {
                    continue;
                }

                // 在注释中定位到该参数
                if (element.getText().startsWith("@param " + parameter.getName())) {

                    String paramWithComment = element.getText();

                    if (paramWithComment.contains("\n")) {
                        // 该字段后面还有注释
                        comment = paramWithComment.substring(("@param " + parameter.getName()).length(), element.getText().indexOf("\n"));
                    } else {
                        // 该字段后面没有其他注释, 只有 */
                        comment = paramWithComment.substring(("@param " + parameter.getName()).length());
                    }
                }
            }
            // 移除前后的空格
            return comment.trim();
        });

    }

    /**
     * 获取注释, 没有 tag 的注释, 一般是写在注释开头的位置
     *
     * @param docComment 注释 PSI
     * @return 注释
     */
    @NotNull
    public static String tagDocComment(PsiDocComment docComment) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {

            StringBuilder sb = new StringBuilder();

            if (docComment != null) {
                for (PsiElement element : docComment.getChildren()) {

                    if (!"PsiDocToken:DOC_COMMENT_DATA".equalsIgnoreCase(element.toString())) {
                        continue;
                    }
                    // 原注释中的换行符移除，移除注释中的 html 标签：<p> </p>
                    sb.append(element.getText().replaceAll("[* \\n]|<p>|</p>", ""));

                }
            }
            return sb.toString();
        });

    }

    public static String tagValueFromDocComment(PsiDocComment docComment, String tagName) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            String comment = "";
            if (docComment == null) {
                return comment;
            }
            for (PsiElement element : docComment.getChildren()) {
                if (!("PsiDocTag:@" + tagName).equalsIgnoreCase(element.toString())) {
                    continue;
                }
                return element.getText().replace(("@" + tagName), StringUtils.EMPTY).trim();
            }
            return "";
        });
    }

    /**
     * 获取注释, 没有 tag 的注释
     *
     * @param docComment 注释 PSI
     * @return 注释
     */
    @NotNull
    public static String tagDocCommentForOneLine(PsiDocComment docComment) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            if (docComment != null) {
                for (PsiElement element : docComment.getChildren()) {

                    if ("PsiDocToken:DOC_COMMENT_DATA".equalsIgnoreCase(element.toString())) {
                        // 只获取第一行注释
                        return element.getText().replaceAll("[* \n]+", StringUtils.EMPTY);
                    }
                }
            }
            return "";
        });

    }

    /**
     * 获取字段的注释
     * <p>
     * 支持普通注释 (// xxx) 和 JavaDoc 注释，若 JavaDoc 中含有 @see 指向枚举，
     * 则自动遍历枚举常量将约束信息追加到注释中
     *
     * @param psiComment 字段的注释 PSI
     * @return 注释
     */
    @NotNull
    public static String fieldComment(PsiComment psiComment) {

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            if (psiComment == null) {
                return "";
            }

            // JavaDoc 注释 (/** ... */)：提取主注释文本并处理 @see 枚举
            if (psiComment instanceof PsiDocComment) {
                PsiDocComment docComment = (PsiDocComment) psiComment;

                // 提取主注释文本（与 tagDocComment 逻辑一致）
                StringBuilder sb = new StringBuilder();
                for (PsiElement element : docComment.getChildren()) {
                    if ("PsiDocToken:DOC_COMMENT_DATA".equalsIgnoreCase(element.toString())) {
                        sb.append(element.getText().replaceAll("[* \\n]|<p>|</p>", ""));
                    }
                }
                String mainComment = sb.toString();

                // 解析 @see 标签，若指向枚举则拼接枚举值描述
                String enumInfo = resolveEnumInfoFromSeeTag(docComment);
                if (StringUtils.isNotBlank(enumInfo)) {
                    if (StringUtils.isNotBlank(mainComment)) {
                        return mainComment + "(" + enumInfo + ")";
                    }
                    return enumInfo;
                }
                return mainComment;
            }

            // 普通注释 (// xxx 或 /* xxx */)
            if (StringUtils.isNotBlank(psiComment.getText())) {
                // 原注释中的换行符移除
                return psiComment.getText().replace("/", StringUtils.EMPTY).trim();
            }
            return "";
        });

    }

    /**
     * 从 @see 标签中解析枚举约束信息
     * <p>
     * 格式：@see EnumClass#fieldName 或 @see EnumClass
     *
     * @param docComment JavaDoc 注释 PSI
     * @return 枚举约束描述，如 "click: 商品点击; cart: 加入购物车"，未找到枚举时返回空字符串
     */
    @NotNull
    private static String resolveEnumInfoFromSeeTag(@NotNull PsiDocComment docComment) {
        for (PsiElement element : docComment.getChildren()) {
            if (!("PsiDocTag:@see").equalsIgnoreCase(element.toString())) {
                continue;
            }

            // 取 @see 后面的值，如 "UserLogEnum#name" 或 "UserLogEnum"
            String seeText = element.getText().replace("@see", "").trim();
            if (StringUtils.isBlank(seeText)) {
                continue;
            }

            // 解析类名和字段名
            String className;
            String fieldName = null;
            if (seeText.contains("#")) {
                String[] parts = seeText.split("#", 2);
                className = parts[0].trim();
                fieldName = parts[1].trim();
            } else {
                className = seeText.trim();
            }

            if (StringUtils.isBlank(className)) {
                continue;
            }

            // 根据类名查找 PsiClass
            Project project = docComment.getProject();
            PsiClass psiClass = findClassByName(project, className);
            if (psiClass == null || !psiClass.isEnum()) {
                continue;
            }

            // 校验字段是否真实存在于枚举中（排除枚举常量本身）
            String resolvedFieldName = fieldName;
            if (resolvedFieldName != null) {
                boolean fieldExists = false;
                for (PsiField f : psiClass.getFields()) {
                    if (!(f instanceof PsiEnumConstant) && resolvedFieldName.equals(f.getName())) {
                        fieldExists = true;
                        break;
                    }
                }
                if (!fieldExists) {
                    resolvedFieldName = null;
                }
            }

            // 遍历枚举常量，拼接说明
            StringBuilder enumInfo = new StringBuilder();
            for (PsiField field : psiClass.getFields()) {
                if (!(field instanceof PsiEnumConstant)) {
                    continue;
                }
                PsiEnumConstant enumConstant = (PsiEnumConstant) field;
                String constantComment = getEnumConstantComment(enumConstant);

                if (resolvedFieldName != null) {
                    String fieldValue = getEnumConstantFieldValue(enumConstant, resolvedFieldName);
                    String key = fieldValue != null ? fieldValue : enumConstant.getName();
                    enumInfo.append(key).append(": ").append(constantComment).append("; ");
                } else {
                    enumInfo.append(enumConstant.getName()).append(": ").append(constantComment).append("; ");
                }
            }

            if (enumInfo.length() > 0) {
                String result = enumInfo.toString().trim();
                // 去掉末尾多余的分号
                if (result.endsWith(";")) {
                    result = result.substring(0, result.length() - 1).trim();
                }
                return result;
            }
        }
        return "";
    }

    /**
     * 根据类名（短名或全限定名）在项目中查找 PsiClass
     */
    @Nullable
    private static PsiClass findClassByName(@NotNull Project project, @NotNull String className) {
        try {
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            if (className.contains(".")) {
                // 全限定类名
                return facade.findClass(className, GlobalSearchScope.allScope(project));
            }
            // 短类名：在整个项目范围内搜索
            PsiClass[] classes = PsiShortNamesCache.getInstance(project)
                    .getClassesByName(className, GlobalSearchScope.allScope(project));
            return classes.length > 0 ? classes[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取枚举常量自身的注释（JavaDoc 首行）；无注释时回退为常量名
     */
    @NotNull
    private static String getEnumConstantComment(@NotNull PsiEnumConstant enumConstant) {
        PsiDocComment docComment = enumConstant.getDocComment();
        if (docComment != null) {
            String comment = tagDocComment(docComment);
            if (StringUtils.isNotBlank(comment)) {
                return comment;
            }
        }
        return enumConstant.getName();
    }

    /**
     * 获取枚举常量中指定字段对应的构造参数值
     * <p>
     * 约定：构造器参数名与字段名相同，如 {@code this.name = name}
     *
     * @param enumConstant 枚举常量
     * @param fieldName    目标字段名
     * @return 字段值字符串（去除首尾引号），找不到时返回 null
     */
    @Nullable
    private static String getEnumConstantFieldValue(@NotNull PsiEnumConstant enumConstant,
                                                     @NotNull String fieldName) {
        PsiClass enumClass = enumConstant.getContainingClass();
        if (enumClass == null) {
            return null;
        }
        for (PsiMethod constructor : enumClass.getConstructors()) {
            PsiParameter[] params = constructor.getParameterList().getParameters();
            for (int i = 0; i < params.length; i++) {
                if (fieldName.equals(params[i].getName())) {
                    PsiExpressionList argList = enumConstant.getArgumentList();
                    if (argList != null && i < argList.getExpressionCount()) {
                        // 去掉字符串字面量两侧的双引号
                        return argList.getExpressions()[i].getText().replaceAll("^\"|\"$", "");
                    }
                }
            }
        }
        return null;
    }

    /**
     * 构建参数
     *
     * @param elements      元素
     * @param paramNameList 参数名称数组
     * @return {@link java.util.List<java.lang.String>}
     */
    @NotNull
    public static List<String> buildParams(@NotNull List<PsiElement> elements, List<String> paramNameList) {

        return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> {
            List<String> paramDocList = Lists.newArrayList();

            for (Iterator<PsiElement> iterator = elements.iterator(); iterator.hasNext(); ) {
                PsiElement element = iterator.next();
                if (!"PsiDocTag:@param".equalsIgnoreCase(element.toString())) {
                    continue;
                }
                String paramName = null;
                String paramData = null;
                for (PsiElement child : element.getChildren()) {
                    if (StringUtils.isBlank(paramName) && "PsiElement(DOC_PARAMETER_REF)".equals(child.toString())) {
                        paramName = StringUtils.trim(child.getText());
                    } else if (StringUtils.isBlank(paramData) && "PsiDocToken:DOC_COMMENT_DATA".equals(child.toString())) {
                        paramData = StringUtils.trim(child.getText());
                    }
                }
                if (StringUtils.isBlank(paramName) || StringUtils.isBlank(paramData)) {
                    iterator.remove();
                    continue;
                }
                if (!paramNameList.contains(paramName)) {
                    iterator.remove();
                    continue;
                }
                paramNameList.remove(paramName);
            }
            for (String paramName : paramNameList) {
                paramDocList.add("@param " + paramName);
            }
            return paramDocList;
        });
    }

    /**
     * 构建返回
     *
     * @param elements   元素
     * @param returnName 返回名称
     * @return {@link java.lang.String}
     */
    @Nullable
    public static String buildReturn(@NotNull List<PsiElement> elements, String returnName) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            boolean isInsert = true;
            for (Iterator<PsiElement> iterator = elements.iterator(); iterator.hasNext(); ) {
                PsiElement element = iterator.next();
                if (!"PsiDocTag:@return".equalsIgnoreCase(element.toString())) {
                    continue;
                }
                PsiDocTagValue value = ((PsiDocTag) element).getValueElement();
                if (value == null || StringUtils.isBlank(value.getText())) {
                    iterator.remove();
                } else if (returnName.isEmpty() || "void".equals(returnName)) {
                    iterator.remove();
                } else {
                    isInsert = false;
                }
            }
            if (isInsert && !returnName.isEmpty() && !"void".equals(returnName)) {
                if (FieldTypeConstant.BASE_TYPE_SET.contains(returnName)) {
                    return "@return " + returnName;
                } else {
                    return "@return {@link " + returnName + "}";
                }
            }
            return "";
        });
    }

    /**
     * 构建异常
     *
     * @param elements          元素
     * @param exceptionNameList 异常名称数组
     * @return {@link java.util.List<java.lang.String>}
     */
    @NotNull
    public static List<String> buildException(@NotNull List<PsiElement> elements, List<String> exceptionNameList) {

        return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> {
            List<String> paramDocList = Lists.newArrayList();
            for (Iterator<PsiElement> iterator = elements.iterator(); iterator.hasNext(); ) {
                PsiElement element = iterator.next();
                if (!"PsiDocTag:@throws".equalsIgnoreCase(element.toString()) && !"PsiDocTag:@exception".equalsIgnoreCase(element.toString())) {
                    continue;
                }
                String exceptionName = null;
                String exceptionData = null;
                for (PsiElement child : element.getChildren()) {
                    if (StringUtils.isBlank(exceptionName) && "PsiElement(DOC_TAG_VALUE_ELEMENT)".equals(child.toString())) {
                        exceptionName = StringUtils.trim(child.getText());
                    } else if (StringUtils.isBlank(exceptionData) && "PsiDocToken:DOC_COMMENT_DATA".equals(child.toString())) {
                        exceptionData = StringUtils.trim(child.getText());
                    }
                }
                if (StringUtils.isBlank(exceptionName) || StringUtils.isBlank(exceptionData)) {
                    iterator.remove();
                    continue;
                }
                if (!exceptionNameList.contains(exceptionName)) {
                    iterator.remove();
                    continue;
                }
                exceptionNameList.remove(exceptionName);
            }
            for (String exceptionName : exceptionNameList) {
                paramDocList.add("@throws " + exceptionName);
            }
            return paramDocList;
        });

    }

}
