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
        INVALID_ARGUMENT,
        PROJECT_NOT_OPEN,
        REFERENCE_NOT_FOUND,
        REFERENCE_AMBIGUOUS,
        UNSUPPORTED_CONTROLLER,
        YAPI_NOT_CONFIGURED,
        DOC_GENERATION_FAILED,
        YAPI_REQUEST_FAILED,
        YAPI_RESPONSE_INVALID
    }
}
