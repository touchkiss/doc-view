package com.liuzhihang.doc.view.utils;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for generating gRPC curl commands.
 * Formats curl commands for gRPC services in the standard grpcurl format.
 *
 * @author liuzhihang
 */
public final class GrpcCurlUtils {

    private static final String DEFAULT_GRPC_HOST = "localhost:9090";

    private GrpcCurlUtils() {
    }

    /**
     * Build a gRPC curl command.
     *
     * @param serviceName the gRPC service name
     * @param methodName the gRPC method name
     * @param jsonBody the JSON request body
     * @return formatted curl command or null if parameters are invalid
     */
    @Nullable
    public static String build(@NotNull String serviceName, @NotNull String methodName, @NotNull String jsonBody) {
        return build(DEFAULT_GRPC_HOST, serviceName, methodName, jsonBody);
    }

    /**
     * Build a gRPC curl command with custom host.
     *
     * @param host the gRPC host (e.g., "localhost:9090")
     * @param serviceName the gRPC service name
     * @param methodName the gRPC method name
     * @param jsonBody the JSON request body
     * @return formatted curl command or null if parameters are invalid
     */
    @Nullable
    public static String build(@NotNull String host, @NotNull String serviceName,
                               @NotNull String methodName, @NotNull String jsonBody) {
        if (StringUtils.isBlank(serviceName) || StringUtils.isBlank(methodName)) {
            return null;
        }

        if (StringUtils.isBlank(host)) {
            host = DEFAULT_GRPC_HOST;
        }

        String url = buildUrl(host, serviceName, methodName);
        return formatCommand(url, jsonBody);
    }

    /**
     * Build the gRPC URL.
     *
     * @param host the host
     * @param serviceName the service name
     * @param methodName the method name
     * @return formatted URL
     */
    @NotNull
    private static String buildUrl(@NotNull String host, @NotNull String serviceName, @NotNull String methodName) {
        // Remove trailing slash from host if present
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        return host + "/" + serviceName + "/" + methodName;
    }

    /**
     * Format the curl command with proper line continuation.
     *
     * @param url the request URL
     * @param jsonBody the JSON body
     * @return formatted curl command
     */
    @NotNull
    private static String formatCommand(@NotNull String url, @NotNull String jsonBody) {
        // Escape single quotes in JSON body
        String escapedBody = jsonBody.replace("'", "'\\''");

        // Format: curl -X GRPC "url" -d 'body'
        return "curl -X GRPC \"" + url + "\" \\\n  -d '" + escapedBody + "'";
    }

    /**
     * Get the default gRPC host.
     *
     * @return default host string
     */
    @NotNull
    public static String getDefaultHost() {
        return DEFAULT_GRPC_HOST;
    }
}
