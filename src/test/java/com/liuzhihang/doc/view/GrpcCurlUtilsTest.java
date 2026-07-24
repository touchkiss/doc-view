package com.liuzhihang.doc.view;

import com.liuzhihang.doc.view.utils.GrpcCurlUtils;

/**
 * GrpcCurlUtils self-check tests.
 */
public class GrpcCurlUtilsTest {

    public static void main(String[] args) {

        // 1. Test basic curl command
        String curl = GrpcCurlUtils.build("UserService", "GetUser", "{\"id\": 1}");
        checkNotNull("Basic curl", curl);
        checkContains("Basic curl has GRPC", curl, "curl -X GRPC");
        checkContains("Basic curl has URL", curl, "localhost:9090/UserService/GetUser");
        checkContains("Basic curl has body", curl, "-d '{\"id\": 1}'");

        // 2. Test with custom host
        curl = GrpcCurlUtils.build("myhost:8080", "UserService", "GetUser", "{\"id\": 1}");
        checkNotNull("Custom host curl", curl);
        checkContains("Custom host URL", curl, "myhost:8080/UserService/GetUser");

        // 3. Test with trailing slash in host
        curl = GrpcCurlUtils.build("localhost:9090/", "UserService", "GetUser", "{}");
        checkNotNull("Trailing slash curl", curl);
        checkContains("Trailing slash URL", curl, "localhost:9090/UserService/GetUser");
        checkNotContains("No double slash", curl, "localhost:9090//UserService");

        // 4. Test with empty service name
        curl = GrpcCurlUtils.build("", "GetUser", "{}");
        checkNull("Empty service name", curl);

        // 5. Test with empty method name
        curl = GrpcCurlUtils.build("UserService", "", "{}");
        checkNull("Empty method name", curl);

        // 6. Test with complex JSON body
        String jsonBody = "{\n  \"uid\": 3004082604,\n  \"name\": \"test\"\n}";
        curl = GrpcCurlUtils.build("BeetoLiveGrpcService", "listUserLivePlay", jsonBody);
        checkNotNull("Complex body curl", curl);
        checkContains("Complex body URL", curl, "BeetoLiveGrpcService/listUserLivePlay");

        // 7. Test with single quote in JSON
        curl = GrpcCurlUtils.build("UserService", "Test", "{\"name\": \"it's a test\"}");
        checkNotNull("Single quote curl", curl);
        checkContains("Single quote escaped", curl, "'\\''");

        // 8. Test line continuation
        curl = GrpcCurlUtils.build("UserService", "LongMethodName", "{\"key\": \"value\"}");
        checkNotNull("Line continuation curl", curl);
        checkContains("Has line continuation", curl, "\\\n");

        // 9. Test default host
        String host = GrpcCurlUtils.getDefaultHost();
        check("Default host", "localhost:9090", host);

        System.out.println("All GrpcCurlUtils tests passed ✓");
    }

    private static void check(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + " failed: expected[" + expected + "] actual[" + actual + "]");
        }
        System.out.println("[PASS] " + name);
    }

    private static void checkNotNull(String name, String actual) {
        if (actual == null) {
            throw new AssertionError(name + " failed: expected non-null");
        }
        System.out.println("[PASS] " + name);
    }

    private static void checkNull(String name, String actual) {
        if (actual != null) {
            throw new AssertionError(name + " failed: expected null, got[" + actual + "]");
        }
        System.out.println("[PASS] " + name);
    }

    private static void checkContains(String name, String actual, String fragment) {
        if (actual == null || !actual.contains(fragment)) {
            throw new AssertionError(name + " failed: did not contain [" + fragment + "] actual[" + actual + "]");
        }
        System.out.println("[PASS] " + name + " -> contains " + fragment);
    }

    private static void checkNotContains(String name, String actual, String fragment) {
        if (actual != null && actual.contains(fragment)) {
            throw new AssertionError(name + " failed: should not contain [" + fragment + "] actual[" + actual + "]");
        }
        System.out.println("[PASS] " + name + " -> does not contain " + fragment);
    }
}
