package com.liuzhihang.doc.view.mcp;

import java.util.Objects;

/** A business failure returned by MCP tools as a stable, structured error code. */
public final class McpException extends RuntimeException {

    private final Code code;

    public McpException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        /** 请求参数缺失或无效。 */
        INVALID_ARGUMENT,
        /** 目标项目未打开。 */
        PROJECT_NOT_OPEN,
        /** 未找到目标声明。 */
        REFERENCE_NOT_FOUND,
        /** 引用匹配多个声明。 */
        REFERENCE_AMBIGUOUS,
        /** 目标不支持作为 Controller 上传。 */
        UNSUPPORTED_CONTROLLER,
        /** 项目缺少 YApi 配置。 */
        YAPI_NOT_CONFIGURED,
        /** 文档生成失败。 */
        DOC_GENERATION_FAILED,
        /** YApi 请求失败。 */
        YAPI_REQUEST_FAILED,
        /** YApi 响应无效。 */
        YAPI_RESPONSE_INVALID
    }
}
