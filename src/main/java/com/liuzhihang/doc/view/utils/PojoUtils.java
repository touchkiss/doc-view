package com.liuzhihang.doc.view.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.dto.Body;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pojo utils
 *
 * @author necksas.liu
 * @date 2024/12/31 11:12:06
 */
public class PojoUtils extends ParamPsiUtils {
    public static final boolean isPojoClass(@NotNull PsiClass psiClass) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {

            Settings settings = Settings.getInstance(psiClass.getProject());

            if (AnnotationUtil.isAnnotated(psiClass, settings.getContainPojoClassAnnotationName(), 0) || ProtoUtils.isProto(psiClass)) {
                return true;
            }
            String className = Objects.requireNonNull(psiClass.getName()).toLowerCase();
            return className.endsWith("dto") || className.endsWith("vo") || className.endsWith("bo") || className.endsWith("po") || className.endsWith("entity") || className.endsWith("model") || className.endsWith("bean");
        });
    }

    @NotNull
    public static Body buildBody(@NotNull PsiClass psiClass) {
        Body root = new Body();

        // 对象类型：对对象进行解析
        PsiType type = PsiTypesUtil.getClassType(psiClass);
        boolean isProto = ProtoUtils.isProto(type);
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
        return root;
    }

    public static String reqBodyJson(PsiClass psiClass) {

        Map<String, Object> fieldMap = new LinkedHashMap<>();
        String name = psiClass.getName();
        PsiType type = PsiTypesUtil.getClassType(psiClass);

        if (type instanceof PsiPrimitiveType) {
            fieldMap.put(name, PsiTypesUtil.getDefaultValue(type));
        } else if (FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
            fieldMap.put(name, FieldTypeConstant.FIELD_TYPE.get(type.getPresentableText()));
        } else {
            if (psiClass != null) {
                fieldMap = ParamPsiUtils.getFieldsAndDefaultValue(psiClass, null);
            }
        }

        return GsonFormatUtil.gsonFormat(fieldMap);
    }
}
