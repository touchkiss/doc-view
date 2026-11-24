package com.liuzhihang.doc.view.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;

import java.util.Map;

/** Type rules shared by documentation schemas and JSON examples. */
final class DtoPropertyTypes {
    private DtoPropertyTypes() {
    }

    static PsiType replaceFieldType(Map<String, PsiType> generics, PsiType type) {
        if (generics == null || generics.isEmpty() || !(type instanceof PsiClassReferenceType reference)) {
            return type;
        }
        if (generics.containsKey(type.getPresentableText())) {
            return generics.get(type.getPresentableText());
        }
        PsiClass fieldClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (fieldClass == null || !fieldClass.hasTypeParameters()) {
            return type;
        }
        PsiType[] parameters = reference.getParameters().clone();
        if (parameters.length == 0) {
            return type;
        }
        for (int i = 0; i < parameters.length; i++) {
            PsiType substitution = generics.get(parameters[i].getPresentableText());
            if (substitution != null) {
                parameters[i] = substitution;
            }
        }
        return PsiElementFactory.getInstance(fieldClass.getProject()).createType(fieldClass, parameters);
    }

    static boolean isJsonSimpleType(String jsonType) {
        return FieldTypeConstant.FIELD_TYPE.containsKey(jsonType)
                || FieldTypeConstant.BASE_TYPE_SET.contains(jsonType);
    }

    static boolean ignoreField(PsiType fieldType) {

        return fieldType instanceof PsiPrimitiveType
                || fieldType == null
                || FieldTypeConstant.FIELD_TYPE.containsKey(fieldType.getPresentableText());
    }
}
