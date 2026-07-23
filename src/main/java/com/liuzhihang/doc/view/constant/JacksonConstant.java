package com.liuzhihang.doc.view.constant;

/**
 * Jackson 注解与常用 Serializer/Deserializer 全路径常量
 *
 * @author liuzhihang
 */
public final class JacksonConstant {

    private JacksonConstant() {
    }

    public static final String JSON_SERIALIZE = "com.fasterxml.jackson.databind.annotation.JsonSerialize";
    public static final String JSON_DESERIALIZE = "com.fasterxml.jackson.databind.annotation.JsonDeserialize";

    public static final String JSON_SERIALIZER = "com.fasterxml.jackson.databind.JsonSerializer";
    public static final String JSON_DESERIALIZER = "com.fasterxml.jackson.databind.JsonDeserializer";
    public static final String JSON_SERIALIZER_NONE = "com.fasterxml.jackson.databind.JsonSerializer$None";
    public static final String JSON_DESERIALIZER_NONE = "com.fasterxml.jackson.databind.JsonDeserializer$None";

    public static final String TO_STRING_SERIALIZER = "com.fasterxml.jackson.databind.ser.std.ToStringSerializerBase";
    public static final String NUMBER_SERIALIZER = "com.fasterxml.jackson.databind.ser.std.NumberSerializer";
    public static final String DATE_SERIALIZER = "com.fasterxml.jackson.databind.ser.std.DateSerializer";
    public static final String NULL_SERIALIZER = "com.fasterxml.jackson.databind.ser.std.NullSerializer";
    public static final String STRING_SERIALIZER = "com.fasterxml.jackson.databind.ser.std.StringSerializer";
    public static final String BOOLEAN_SERIALIZER = "com.fasterxml.jackson.databind.ser.std.BooleanSerializer";

    public static final String FROM_STRING_DESERIALIZER = "com.fasterxml.jackson.databind.deser.std.FromStringDeserializer";
    public static final String STRING_DESERIALIZER = "com.fasterxml.jackson.databind.deser.std.StringDeserializer";
}
