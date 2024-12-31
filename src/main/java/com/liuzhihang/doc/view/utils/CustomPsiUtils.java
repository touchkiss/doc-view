package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author liuzhihang
 * @date 2020/3/4 16:18
 */
public class CustomPsiUtils {

    /**
     * 通过 PsiFile 获取 PsiClass
     *
     * @param file PsiFile
     * @return PsiClass
     * @author lvgorice@gmail.com
     * @since 1.0.4
     */
    public static PsiClass getTargetClass(PsiFile file) {
        PsiElement[] children = file.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiClass) {
                return (PsiClass) child;
            }
        }
        return null;
    }

    @Nullable
    public static PsiClass getTargetClass(@NotNull Editor editor, @NotNull PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if ("protobuf".equalsIgnoreCase(file.getLanguage().getDisplayName())) {
            // Parse the .proto file and generate Java code (this part is simplified)
            PsiClass psiClass = ProtoToPsiClassConverter.convertProtoToPsiClass(file, editor.getProject());
            return psiClass;
        }
        if (element != null) {
            // 当前类
            PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);

            return target instanceof SyntheticElement ? null : target;
        }

        return null;
    }


    /**
     * 获取当前选择的方法
     *
     * @param editor 编辑器
     * @param file   文件
     * @return 当前选择的方法
     */
    @Nullable
    public static PsiMethod getTargetMethod(@NotNull Editor editor, @NotNull PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element != null) {
            // 当前方法
            PsiMethod target = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            return target instanceof SyntheticElement ? null : target;
        }
        return null;
    }

    /**
     * 校验方法是否含有修饰符
     *
     * @param psiMethod
     * @param modifier
     * @return
     * @see PsiModifier
     */
    public static boolean hasModifierProperty(@NotNull PsiMethod psiMethod, String modifier) {
        PsiModifierList modifierList = psiMethod.getModifierList();
        return modifierList.hasModifierProperty(modifier);
    }

    /**
     * 校验字段是否有某个修饰符
     *
     * @param psiField
     * @param modifier
     * @return
     */
    public static boolean hasModifierProperty(@NotNull PsiField psiField, String modifier) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (modifierList == null) {
            return false;
        }
        return modifierList.hasModifierProperty(modifier);
    }


    /**
     * 获取泛型Map, 将泛型的 参数和实际指定的泛型进行对应
     *
     * @param psiClassType 实际返回的填写泛型的代码 Result<User>
     * @return key 是 泛型参数 T K , value 是实际参数中写的类型
     */
    public static @Nullable Map<String, PsiType> getGenericsMap(@NotNull PsiClassType psiClassType) {

        Map<PsiTypeParameter, PsiType> substitutionMap = PsiUtil.resolveGenericsClassInType(psiClassType).getSubstitutor().getSubstitutionMap();
        Map<String, PsiType> hashMap = new HashMap<>();
        boolean isProto = ProtoUtils.isProto(psiClassType);
        for (PsiTypeParameter psiTypeParameter : substitutionMap.keySet()) {
            PsiType psiType = substitutionMap.get(psiTypeParameter);
            if (psiType instanceof PsiClassType) {

                String fieldName = psiTypeParameter.getName();
                if (isProto && fieldName != null && fieldName.endsWith("_")) {
                    fieldName = fieldName.substring(0, fieldName.length() - 1);
                }
                hashMap.put(fieldName, psiType);
            }
        }
        return hashMap;

    }


}
