package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.editor.Editor;
import com.intellij.protobuf.lang.psi.PbFile;
import com.intellij.protobuf.lang.psi.PbMessageType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 根据光标位置定位 .proto 文件中的 message 定义。
 * <p>
 * 该类直接引用 com.intellij.protobuf.* API, 调用方必须先通过
 * {@link ProtoPluginSupport#isAvailable()} 判断 Protocol Buffers 插件是否可用。
 *
 * @author liuzhihang
 */
public final class ProtoMessageResolver {

    private ProtoMessageResolver() {
    }

    /**
     * 判断文件是否是 proto 文件。
     * <p>
     * 使用 PSI 类型判断, 而不是语言的 displayName（受本地化/品牌影响）。
     *
     * @param psiFile 文件
     * @return true 表示是 proto 文件
     */
    public static boolean isProtoFile(@Nullable PsiFile psiFile) {
        return ProtoPluginSupport.isAvailable() && psiFile instanceof PbFile;
    }

    /**
     * 获取光标所在的 message 定义。
     * <p>
     * 优先取光标所处的 message（含嵌套 message, 取最内层）;
     * 光标不在任何 message 内时, 若文件仅声明了一个 message 则返回该 message;
     * 文件声明了多个 message 且光标不在其中任何一个内时返回 null。
     *
     * @param editor  编辑器
     * @param psiFile 文件
     * @return 光标所在的 message, 无法确定时返回 null
     */
    @Nullable
    public static PbMessageType findTargetMessage(@NotNull Editor editor, @NotNull PsiFile psiFile) {

        if (!isProtoFile(psiFile)) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);

        if (element != null) {
            // getParentOfType 返回最近的祖先, 因此嵌套 message 内会命中内层 message
            PbMessageType message = PsiTreeUtil.getParentOfType(element, PbMessageType.class);
            if (message != null) {
                return message;
            }
        }

        // 光标不在 message 内（空行、文件末尾、service/rpc 行等）:
        // 文件只声明了一个顶层 message 时按该 message 处理, 否则无法确定用户意图。
        // 只统计顶层 message: message A { message B {} } 仍应回退到 A
        List<PbMessageType> topLevelMessages = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, PbMessageType.class);
        if (topLevelMessages.size() == 1) {
            return topLevelMessages.get(0);
        }

        return null;
    }

    /**
     * 获取光标所在 message 合成出来的 Java 类。
     * <p>
     * 该方法是 protobuf PSI 与其余代码之间的桥: 调用方只接触 PsiClass,
     * 从而不会因为方法签名/局部变量引用 com.intellij.protobuf.* 而触发类加载。
     *
     * @param editor  编辑器
     * @param psiFile 文件
     * @return 合成出来的 PsiClass; 光标不在 message 内或转换失败时返回 null
     */
    @Nullable
    public static PsiClass findTargetPsiClass(@NotNull Editor editor, @NotNull PsiFile psiFile) {

        PbMessageType message = findTargetMessage(editor, psiFile);

        if (message == null) {
            return null;
        }

        return ProtoToPsiClassConverter.convert(message, psiFile.getProject());
    }
}
