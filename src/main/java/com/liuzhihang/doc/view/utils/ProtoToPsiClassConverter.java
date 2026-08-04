package com.liuzhihang.doc.view.utils;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.protobuf.lang.psi.PbCommentOwner;
import com.intellij.protobuf.lang.psi.PbEnumDefinition;
import com.intellij.protobuf.lang.psi.PbField;
import com.intellij.protobuf.lang.psi.PbMapField;
import com.intellij.protobuf.lang.psi.PbMessageBody;
import com.intellij.protobuf.lang.psi.PbMessageType;
import com.intellij.protobuf.lang.psi.PbOneofBody;
import com.intellij.protobuf.lang.psi.PbOneofDefinition;
import com.intellij.protobuf.lang.psi.PbSimpleField;
import com.intellij.protobuf.lang.psi.PbSymbol;
import com.intellij.protobuf.lang.psi.PbTypeName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.config.Settings;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 将 .proto 中的 message 合成为一个内存中的 Java 类, 以复用现有的 POJO 文档流水线
 * (PojoDocViewServiceImpl -> PojoUtils.buildBody -> Body -> DocViewData -> Velocity 模版)。
 * <p>
 * 生成规则:
 * <ul>
 *     <li>光标所在 message 生成为顶层 public class</li>
 *     <li>其可达的 message 生成为该顶层类的 public static 嵌套类, 保证内存文件内的类型引用可解析</li>
 *     <li>proto 注释转成 JavaDoc, 使 DocViewUtils.getTitle / fieldDesc 无需改动即可取到说明</li>
 *     <li>生成的类不继承 com.google.protobuf 下的任何类型, 因此 ProtoUtils.isProto 为 false,
 *         ParamPsiUtils / DocViewUtils 中针对 protobuf 生成类的字段过滤（字段名以 _ 结尾）不会生效</li>
 * </ul>
 * 该类直接引用 com.intellij.protobuf.* API, 调用方必须先通过
 * {@link ProtoPluginSupport#isAvailable()} 判断 Protocol Buffers 插件是否可用。
 *
 * @author liuzhihang
 */
public final class ProtoToPsiClassConverter {

    /**
     * 生成的内存 Java 文件名
     */
    private static final String GENERATED_FILE_NAME = "DocViewProtoMessage.java";

    /**
     * 类型展开的最大深度, 避免病态的类型图导致生成失控
     */
    private static final int MAX_DEPTH = 10;

    private ProtoToPsiClassConverter() {
    }

    /**
     * 将 proto message 转换为内存中的 Java 类
     *
     * @param message proto message
     * @param project 当前工程
     * @return 合成出来的 PsiClass, 失败时返回 null
     */
    @Nullable
    public static PsiClass convert(@NotNull PbMessageType message, @NotNull Project project) {

        String javaCode = new Generator(project).generate(message);

        if (StringUtils.isBlank(javaCode)) {
            return null;
        }

        PsiFile javaPsiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(GENERATED_FILE_NAME, JavaLanguage.INSTANCE, javaCode);

        // 标记为 proto 合成类, 供 DocViewService 路由到 PojoDocViewServiceImpl
        ProtoUtils.markSynthetic(javaPsiFile);

        return PsiTreeUtil.findChildOfType(javaPsiFile, PsiClass.class);
    }

    /**
     * 生成器, 一次转换一个实例, 保存本次转换的命名与去重状态
     */
    private static final class Generator {

        private final Project project;

        /**
         * 必填字段的 JavaDoc tag 名, 默认 DocView.Required
         */
        private final String requiredTag;

        /**
         * proto 全限定名 -> 生成的 Java 类名, 同时充当已生成集合, 保证递归类型图能终止
         */
        private final Map<String, String> javaNameByProtoName = new HashMap<>();

        /**
         * 已占用的 Java 类名
         */
        private final Set<String> usedTypeNames = new HashSet<>();

        /**
         * 待生成的类型
         */
        private final Deque<Pending> pending = new ArrayDeque<>();

        private Generator(Project project) {
            this.project = project;
            this.requiredTag = Settings.getInstance(project).getRequired();
        }

        private String generate(PbMessageType root) {

            String rootName = allocateTypeName(root);
            javaNameByProtoName.put(protoQualifiedName(root), rootName);

            StringBuilder rootFields = new StringBuilder();
            renderMembers(root, 0, rootFields);

            // 渲染 root 时可能又入队了新的类型, 循环直到没有待生成类型
            StringBuilder nested = new StringBuilder();
            while (!pending.isEmpty()) {
                Pending item = pending.poll();
                StringBuilder body = new StringBuilder();
                renderMembers(item.message(), item.depth(), body);

                appendJavaDoc(nested, commentOf(item.message()), null, "    ");
                nested.append("    public static class ").append(item.javaName()).append(" {\n\n");
                nested.append(body);
                nested.append("    }\n\n");
            }

            StringBuilder code = new StringBuilder();
            appendJavaDoc(code, commentOf(root), null, "");
            code.append("public class ").append(rootName).append(" {\n\n");
            code.append(rootFields);
            code.append(nested);
            code.append("}\n");

            return code.toString();
        }

        /**
         * 按声明顺序渲染 message 的字段: 普通字段、map 字段、oneof 内的字段。
         * 嵌套的 message/enum 声明不在此渲染, 仅在被字段引用时按需生成。
         */
        private void renderMembers(PbMessageType message, int depth, StringBuilder out) {

            PbMessageBody body = message.getBody();
            if (body == null) {
                return;
            }

            // 用 identity 去重, 避免子节点遍历与 getXxxList() 兜底重复渲染同一字段
            Map<PsiElement, Boolean> rendered = new IdentityHashMap<>();

            for (PsiElement child : body.getChildren()) {
                if (child instanceof PbMapField mapField) {
                    rendered.put(mapField, Boolean.TRUE);
                    renderMapField(mapField, depth, out);
                } else if (child instanceof PbField field) {
                    rendered.put(field, Boolean.TRUE);
                    renderField(field, null, depth, out);
                } else if (child instanceof PbOneofDefinition oneof) {
                    PbOneofBody oneofBody = oneof.getBody();
                    if (oneofBody == null) {
                        continue;
                    }
                    for (PbSimpleField oneofField : oneofBody.getSimpleFieldList()) {
                        rendered.put(oneofField, Boolean.TRUE);
                        renderField(oneofField, oneof.getName(), depth, out);
                    }
                }
            }

            // 兜底: 若字段并非 body 的直接子节点, 上面的遍历会漏掉
            for (PbSimpleField field : body.getSimpleFieldList()) {
                if (!rendered.containsKey(field)) {
                    renderField(field, null, depth, out);
                }
            }
            for (PbMapField mapField : body.getMapFieldList()) {
                if (!rendered.containsKey(mapField)) {
                    renderMapField(mapField, depth, out);
                }
            }
        }

        private void renderField(PbField field, @Nullable String oneofName, int depth, StringBuilder out) {

            String protoName = field.getName();
            if (StringUtils.isBlank(protoName)) {
                return;
            }

            ResolvedType type = resolveType(field.getTypeName(), depth);

            String javaType = field.isRepeated()
                    ? "java.util.List<" + type.javaType() + ">"
                    : type.javaType();

            // oneof 成员一定不是必填
            boolean required = oneofName == null && field.isRequired();

            appendField(out, protoName, javaType, required,
                    descOf(commentOf(field), oneofName, type.note()));
        }

        private void renderMapField(PbMapField mapField, int depth, StringBuilder out) {

            String protoName = mapField.getName();
            if (StringUtils.isBlank(protoName)) {
                return;
            }

            ResolvedType keyType = resolveType(mapField.getKeyType(), depth);
            ResolvedType valueType = resolveType(mapField.getValueType(), depth);

            String javaType = "java.util.Map<" + keyType.javaType() + ", " + valueType.javaType() + ">";

            appendField(out, protoName, javaType, false,
                    descOf(commentOf(mapField), null, valueType.note()));
        }

        private void appendField(StringBuilder out, String protoName, String javaType,
                                 boolean required, String desc) {

            String javaName = toJavaIdentifier(protoName);
            String finalDesc = javaName.equals(protoName)
                    ? desc
                    : joinDesc(desc, "proto 字段名: " + protoName);

            appendJavaDoc(out, finalDesc, required ? requiredTag : null, "    ");
            out.append("    private ").append(javaType).append(" ").append(javaName).append(";\n\n");
        }

        /**
         * 解析 proto 类型引用, 得到对应的 Java 类型与补充说明
         */
        private ResolvedType resolveType(@Nullable PbTypeName typeName, int depth) {

            if (typeName == null) {
                return new ResolvedType(ProtoJavaTypeMapper.UNKNOWN_JAVA_TYPE,
                        ProtoJavaTypeMapper.unresolvedDesc(null));
            }

            // 标量类型
            if (typeName.isBuiltInType()) {
                return new ResolvedType(ProtoJavaTypeMapper.javaTypeOf(typeName.getBuiltInType()),
                        ProtoJavaTypeMapper.scalarDesc(typeName.getBuiltInType()));
            }

            String referenceString = typeName.getReferenceString();

            // well-known types 先按书写的名称判断, well-known 描述文件无法解析时也能命中
            String wellKnown = ProtoJavaTypeMapper.wellKnownJavaType(normalizeReference(referenceString));
            if (wellKnown != null) {
                return new ResolvedType(wellKnown, null);
            }

            PsiElement resolved = resolveTarget(typeName);

            if (resolved instanceof PbEnumDefinition enumDefinition) {
                // 枚举不展开为对象: 枚举常量是 static 字段, 会被 isExcludeField 全部排除, 只会得到空对象
                return new ResolvedType("String", ProtoJavaTypeMapper.enumValuesDesc(enumDefinition));
            }

            if (resolved instanceof PbMessageType messageType) {
                String qualifiedName = protoQualifiedName(messageType);

                // 按解析结果再判断一次 well-known type
                String resolvedWellKnown = ProtoJavaTypeMapper.wellKnownJavaType(qualifiedName);
                if (resolvedWellKnown != null) {
                    return new ResolvedType(resolvedWellKnown, null);
                }

                // 已生成过: 直接引用, 递归 / 相互递归的类型图在此终止
                String existing = javaNameByProtoName.get(qualifiedName);
                if (existing != null) {
                    return new ResolvedType(existing, null);
                }

                if (depth + 1 > MAX_DEPTH) {
                    return new ResolvedType(ProtoJavaTypeMapper.UNKNOWN_JAVA_TYPE,
                            "嵌套层级超过 " + MAX_DEPTH + ", 未展开: " + referenceString);
                }

                String javaName = allocateTypeName(messageType);
                javaNameByProtoName.put(qualifiedName, javaName);
                pending.add(new Pending(messageType, javaName, depth + 1));

                return new ResolvedType(javaName, null);
            }

            return new ResolvedType(ProtoJavaTypeMapper.UNKNOWN_JAVA_TYPE,
                    ProtoJavaTypeMapper.unresolvedDesc(referenceString));
        }

        @Nullable
        private PsiElement resolveTarget(PbTypeName typeName) {
            PsiReference reference = typeName.getEffectiveReference();
            return reference == null ? null : reference.resolve();
        }

        /**
         * 分配一个未被占用的 Java 类名: 优先用 message 简单名, 冲突时退化为扁平化全限定名
         */
        private String allocateTypeName(PbMessageType message) {

            String simpleName = toJavaIdentifier(StringUtils.defaultIfBlank(message.getName(), "ProtoMessage"));
            if (usedTypeNames.add(simpleName)) {
                return simpleName;
            }

            String flattened = toJavaIdentifier(protoQualifiedName(message).replace('.', '_'));
            if (usedTypeNames.add(flattened)) {
                return flattened;
            }

            int index = 2;
            while (!usedTypeNames.add(simpleName + index)) {
                index++;
            }
            return simpleName + index;
        }

        /**
         * message 的 proto 全限定名, 作为去重与 well-known 判断的键
         */
        private String protoQualifiedName(PbMessageType message) {
            if (message instanceof PbSymbol symbol && symbol.getQualifiedName() != null) {
                return symbol.getQualifiedName().toString();
            }
            return StringUtils.defaultIfBlank(message.getName(), "ProtoMessage");
        }

        /**
         * 拼装字段说明: 注释 + oneof 标注 + 类型补充说明
         */
        private String descOf(@Nullable String comment, @Nullable String oneofName, @Nullable String typeNote) {
            String desc = StringUtils.defaultString(comment);
            if (StringUtils.isNotBlank(oneofName)) {
                desc = joinDesc(desc, "[oneof " + oneofName + "]");
            }
            return joinDesc(desc, typeNote);
        }

        private String joinDesc(@Nullable String left, @Nullable String right) {
            if (StringUtils.isBlank(left)) {
                return StringUtils.defaultString(right);
            }
            if (StringUtils.isBlank(right)) {
                return left;
            }
            return left + "; " + right;
        }

        /**
         * 提取 proto 注释: 优先前置注释, 没有则取行尾注释
         */
        @Nullable
        private String commentOf(PsiElement element) {

            if (!(element instanceof PbCommentOwner owner)) {
                return null;
            }

            List<PsiComment> comments = new ArrayList<>(owner.getLeadingComments());
            if (comments.isEmpty()) {
                comments.addAll(owner.getTrailingComments());
            }

            StringJoiner joiner = new StringJoiner(" ");
            for (PsiComment comment : comments) {
                String text = cleanComment(comment.getText());
                if (StringUtils.isNotBlank(text)) {
                    joiner.add(text);
                }
            }

            String result = joiner.toString();
            return StringUtils.isBlank(result) ? null : result;
        }

        /**
         * 去掉注释符号, 并压平换行
         */
        private String cleanComment(@Nullable String text) {
            if (text == null) {
                return "";
            }
            String cleaned = text.trim();
            if (cleaned.startsWith("/*")) {
                cleaned = cleaned.substring(2);
                if (cleaned.endsWith("*/")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 2);
                }
            } else if (cleaned.startsWith("//")) {
                cleaned = cleaned.substring(2);
            }
            // 逐行去掉 JavaDoc 风格的前导 *
            StringJoiner joiner = new StringJoiner(" ");
            for (String line : cleaned.split("\\R")) {
                String trimmed = StringUtils.removeStart(line.trim(), "*").trim();
                if (StringUtils.isNotBlank(trimmed)) {
                    joiner.add(trimmed);
                }
            }
            return joiner.toString();
        }

        /**
         * 输出 JavaDoc; 无内容且无 tag 时不输出
         *
         * @param tag    需要额外写入的 tag 名, 如 DocView.Required
         * @param indent 缩进
         */
        private void appendJavaDoc(StringBuilder out, @Nullable String desc,
                                   @Nullable String tag, String indent) {

            String safeDesc = escapeJavaDoc(desc);

            if (StringUtils.isBlank(safeDesc) && tag == null) {
                return;
            }

            out.append(indent).append("/**\n");
            if (StringUtils.isNotBlank(safeDesc)) {
                out.append(indent).append(" * ").append(safeDesc).append("\n");
            }
            if (tag != null) {
                out.append(indent).append(" * @").append(tag).append("\n");
            }
            out.append(indent).append(" */\n");
        }

        /**
         * 使注释内容能安全嵌入 JavaDoc: 去掉注释结束符与换行, 去掉行首 @ 避免被当成 tag
         */
        @Nullable
        private String escapeJavaDoc(@Nullable String desc) {
            if (desc == null) {
                return null;
            }
            String escaped = desc.replace("*/", "*").replaceAll("\\R", " ").trim();
            return StringUtils.removeStart(escaped, "@");
        }

        /**
         * proto 名称 -> 合法的 Java 标识符。
         * proto 允许使用 Java 关键字作为名称（如 message class / int32 class = 1;）,
         * 这类名称直接写入会导致整个内存类无法解析, 因此追加下划线, 并在说明中标注原名。
         */
        private String toJavaIdentifier(String name) {
            if (PsiNameHelper.getInstance(project).isIdentifier(name)) {
                return name;
            }
            String candidate = name.replaceAll("[^A-Za-z0-9_]", "_");
            if (candidate.isEmpty() || Character.isDigit(candidate.charAt(0))) {
                candidate = "_" + candidate;
            }
            while (!PsiNameHelper.getInstance(project).isIdentifier(candidate)) {
                candidate = candidate + "_";
            }
            return candidate;
        }

        /**
         * 去掉全限定引用前的 '.', proto 中 .google.protobuf.Timestamp 是合法写法
         */
        @Nullable
        private String normalizeReference(@Nullable String reference) {
            return reference == null ? null : StringUtils.removeStart(reference.trim(), ".");
        }
    }

    /**
     * 待生成的类型
     */
    private record Pending(PbMessageType message, String javaName, int depth) {
    }

    /**
     * 解析后的类型: Java 类型 + 补充说明
     */
    private record ResolvedType(@NotNull String javaType, @Nullable String note) {
    }
}
