package com.liuzhihang.doc.view.dto;

import lombok.Getter;

/**
 * Jackson 注解解析后的 JSON 实际类型
 */
@Getter
public class JsonWireType {

    private final String jsonType;
    private final boolean overridden;
    private final Object defaultValue;
    private final String exampleOverride;

    private JsonWireType(String jsonType, boolean overridden, Object defaultValue, String exampleOverride) {
        this.jsonType = jsonType;
        this.overridden = overridden;
        this.defaultValue = defaultValue;
        this.exampleOverride = exampleOverride;
    }

    public static JsonWireType ofJavaType(String javaTypeName) {
        return new JsonWireType(javaTypeName, false, null, null);
    }

    public static JsonWireType overridden(String jsonType, Object defaultValue, String exampleOverride) {
        return new JsonWireType(jsonType, true, defaultValue, exampleOverride);
    }
}
