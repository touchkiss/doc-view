package com.liuzhihang.doc.view.utils;

import com.intellij.protobuf.lang.psi.PbEnumBody;
import com.intellij.protobuf.lang.psi.PbEnumDefinition;
import com.intellij.protobuf.lang.psi.PbEnumValue;
import com.intellij.protobuf.lang.util.BuiltInType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * proto 类型 -> Java 类型映射。
 * <p>
 * 映射到包装类型而不是基本类型, 使 {@link com.liuzhihang.doc.view.constant.FieldTypeConstant#FIELD_TYPE}
 * 能按 presentableText 命中, 从而生成 JSON 示例默认值。
 * <p>
 * 该类直接引用 com.intellij.protobuf.* API, 调用方必须先通过
 * {@link ProtoPluginSupport#isAvailable()} 判断 Protocol Buffers 插件是否可用。
 *
 * @author liuzhihang
 */
public final class ProtoJavaTypeMapper {

    /**
     * 无法解析类型时使用的 Java 类型
     */
    public static final String UNKNOWN_JAVA_TYPE = "Object";

    /**
     * google.protobuf 包名前缀
     */
    private static final String WELL_KNOWN_PREFIX = "google.protobuf.";

    /**
     * well-known types -> Java 类型。
     * 按 protobuf 的 JSON 映射规则处理, 而不是展开其内部字段。
     */
    private static final Map<String, String> WELL_KNOWN_TYPES = new HashMap<>(32);

    static {
        // Timestamp/Duration/FieldMask 的 JSON 表示都是字符串
        WELL_KNOWN_TYPES.put("Timestamp", "String");
        WELL_KNOWN_TYPES.put("Duration", "String");
        WELL_KNOWN_TYPES.put("FieldMask", "String");
        // wrapper 类型的 JSON 表示就是被包装的标量本身
        WELL_KNOWN_TYPES.put("Int32Value", "Integer");
        WELL_KNOWN_TYPES.put("UInt32Value", "Integer");
        WELL_KNOWN_TYPES.put("Int64Value", "Long");
        WELL_KNOWN_TYPES.put("UInt64Value", "Long");
        WELL_KNOWN_TYPES.put("FloatValue", "Float");
        WELL_KNOWN_TYPES.put("DoubleValue", "Double");
        WELL_KNOWN_TYPES.put("BoolValue", "Boolean");
        WELL_KNOWN_TYPES.put("StringValue", "String");
        WELL_KNOWN_TYPES.put("BytesValue", "String");
        // 自由结构, 无法给出固定字段
        WELL_KNOWN_TYPES.put("Struct", UNKNOWN_JAVA_TYPE);
        WELL_KNOWN_TYPES.put("Value", UNKNOWN_JAVA_TYPE);
        WELL_KNOWN_TYPES.put("ListValue", UNKNOWN_JAVA_TYPE);
        WELL_KNOWN_TYPES.put("Any", UNKNOWN_JAVA_TYPE);
        WELL_KNOWN_TYPES.put("Empty", UNKNOWN_JAVA_TYPE);
    }

    private ProtoJavaTypeMapper() {
    }

    /**
     * proto 标量类型 -> Java 包装类型
     *
     * @param builtInType proto 内置类型
     * @return Java 类型简单名
     */
    @NotNull
    public static String javaTypeOf(@Nullable BuiltInType builtInType) {
        if (builtInType == null) {
            return UNKNOWN_JAVA_TYPE;
        }
        return switch (builtInType) {
            case INT32, SINT32, SFIXED32, FIXED32, UINT32 -> "Integer";
            case INT64, SINT64, SFIXED64, FIXED64, UINT64 -> "Long";
            case DOUBLE -> "Double";
            case FLOAT -> "Float";
            case BOOL -> "Boolean";
            // bytes 的 JSON 表示是 base64 字符串
            case STRING, BYTES -> "String";
        };
    }

    /**
     * 标量类型的补充说明, 用于类型信息在 Java 类型上丢失的场景
     *
     * @param builtInType proto 内置类型
     * @return 补充说明, 无需说明时返回 null
     */
    @Nullable
    public static String scalarDesc(@Nullable BuiltInType builtInType) {
        if (builtInType == BuiltInType.BYTES) {
            return "bytes, base64 编码字符串";
        }
        return null;
    }

    /**
     * well-known type 对应的 Java 类型
     *
     * @param protoQualifiedName proto 全限定名, 如 google.protobuf.Timestamp
     * @return Java 类型简单名; 不是 well-known type 时返回 null
     */
    @Nullable
    public static String wellKnownJavaType(@Nullable String protoQualifiedName) {
        if (protoQualifiedName == null || !protoQualifiedName.startsWith(WELL_KNOWN_PREFIX)) {
            return null;
        }
        return WELL_KNOWN_TYPES.get(protoQualifiedName.substring(WELL_KNOWN_PREFIX.length()));
    }

    /**
     * 枚举可选值说明, 如 "可选值: UNKNOWN(0), PAID(1)"
     *
     * @param enumDefinition 枚举定义
     * @return 可选值说明, 无可选值时返回 null
     */
    @Nullable
    public static String enumValuesDesc(@Nullable PbEnumDefinition enumDefinition) {
        if (enumDefinition == null) {
            return null;
        }
        PbEnumBody body = enumDefinition.getBody();
        if (body == null) {
            return null;
        }

        StringJoiner joiner = new StringJoiner(", ", "可选值: ", "");
        boolean hasValue = false;
        for (PbEnumValue enumValue : body.getEnumValueList()) {
            String name = enumValue.getName();
            if (name == null) {
                continue;
            }
            hasValue = true;
            String number = enumValue.getNumberValue() == null ? null : enumValue.getNumberValue().getText();
            joiner.add(number == null ? name : name + "(" + number + ")");
        }

        return hasValue ? joiner.toString() : null;
    }

    /**
     * 类型无法解析时的说明
     *
     * @param typeReference proto 中书写的类型名
     * @return 说明文案
     */
    @NotNull
    public static String unresolvedDesc(@Nullable String typeReference) {
        return "未解析类型: " + (typeReference == null || typeReference.isBlank() ? "?" : typeReference);
    }
}
