package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProtoUtils {

    /**
     * 标记由 .proto message 合成出来的内存 Java 文件。
     * <p>
     * 该标记用于 {@link com.liuzhihang.doc.view.service.DocViewService#getInstance} 的路由判断:
     * message 名称不一定符合 POJO 的名称约定（如 Order）, 不能依赖 PojoUtils.isPojoClass 的名称启发。
     * <p>
     * 有意放在本类（不引用 com.intellij.protobuf.*）而不是 ProtoToPsiClassConverter,
     * 使未安装 Protocol Buffers 插件的 IDE 上读取标记时不会触发 protobuf 类加载。
     */
    public static final Key<Boolean> PROTO_SYNTHETIC = Key.create("docview.proto.synthetic");

    /**
     * 将内存 Java 文件标记为 proto message 合成类
     *
     * @param psiFile 合成出来的内存 Java 文件
     */
    public static void markSynthetic(@NotNull PsiFile psiFile) {
        psiFile.putUserData(PROTO_SYNTHETIC, Boolean.TRUE);
    }

    /**
     * 判断类是否是由 .proto message 合成出来的类
     *
     * @param psiClass 类
     * @return true 表示是 proto message 合成类
     */
    public static boolean isSyntheticProtoClass(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        PsiFile containingFile = psiClass.getContainingFile();
        return containingFile != null && Boolean.TRUE.equals(containingFile.getUserData(PROTO_SYNTHETIC));
    }

    public static boolean isProto(PsiType psiType) {
        PsiClass returnClass = PsiUtil.resolveClassInType(psiType);
        return isProto(returnClass);
    }

    public static boolean isProto(PsiClass returnClass) {
        if (returnClass == null) {
            return false;
        }
        PsiClassType[] extendsListTypes = returnClass.getExtendsListTypes();
        for (PsiClassType implementsListType : extendsListTypes) {
            if (implementsListType.getCanonicalText().contains("com.google.protobuf")) {
                return true;
            }
        }
        ////        Q:需要在这里判断returnClass是不是proto,如果是proto,则需要解析proto文件，如何实现？
////        A:可以通过判断returnClass的全限定名是否包含proto关键字来判断是否是proto类
////        Q:具体如何做
////        A:可以通过returnClass.getQualifiedName()方法获取全限定名，然后判断是否包含proto关键字
//        String qualifiedName = returnClass.getQualifiedName();
//        boolean isProto = false;
//        if (qualifiedName != null) {
//            //解析proto文件
//            return qualifiedName.matches("com\\.beeto\\.api\\.[a-z]+\\.grpc\\..*");
//        }
        return false;
    }
}
