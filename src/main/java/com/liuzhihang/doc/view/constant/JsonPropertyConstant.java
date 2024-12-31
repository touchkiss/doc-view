package com.liuzhihang.doc.view.constant;

/**
 * JsonProperty 配置
 *
 * @author liuzhihang
 * @since 2023/12/23 18:42
 */
public class JsonPropertyConstant {
    private JsonPropertyConstant() {
    }

    /**
     * 注解 @JsonProperty 的全路径
     */
    public static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";


    public static final String CAMEL_CASE = "camelCase";

    public static final String SNAKE_CASE = "snakeCase";
}
