package com.liuzhihang.doc.view.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;

public class ProtoUtils {
    public static boolean isProto(PsiType psiType) {
        PsiClass returnClass = PsiUtil.resolveClassInType(psiType);
        return isProto(returnClass);
    }

    public static boolean isProto(PsiClass returnClass) {
        if (returnClass == null) {
            return false;
        }
        PsiClassType[] extendsListTypes = returnClass.getExtendsListTypes();
        if (extendsListTypes != null && extendsListTypes.length > 0) {
            for (PsiClassType implementsListType : extendsListTypes) {
                if (implementsListType.getCanonicalText().contains("com.google.protobuf")) {
                    return true;
                }
            }
        }
//        Q:需要在这里判断returnClass是不是proto,如果是proto,则需要解析proto文件，如何实现？
//        A:可以通过判断returnClass的全限定名是否包含proto关键字来判断是否是proto类
//        Q:具体如何做
//        A:可以通过returnClass.getQualifiedName()方法获取全限定名，然后判断是否包含proto关键字
        String qualifiedName = returnClass.getQualifiedName();
        boolean isProto = false;
        if (qualifiedName != null) {
            //解析proto文件
            return qualifiedName.matches("com\\.beeto\\.api\\.[a-z]+\\.grpc\\..*");
        }
        return false;
    }
}
