package com.liuzhihang.doc.view;

import com.liuzhihang.doc.view.utils.ProtoGrpcUtils;

import java.util.List;

/**
 * ProtoGrpcUtils self-check tests.
 */
public class ProtoGrpcUtilsTest {

    public static void main(String[] args) {

        // 1. Test JSON generation with int fields
        List<String[]> intFields = List.of(
                new String[]{"uid", "int64"},
                new String[]{"begin_time_millis", "int64"},
                new String[]{"next_since_id", "int64"}
        );
        String json = ProtoGrpcUtils.generateJsonBody(intFields);
        checkContains("Int fields", json, "\"uid\": 0");
        checkContains("Int fields 2", json, "\"begin_time_millis\": 0");
        checkContains("Int fields 3", json, "\"next_since_id\": 0");

        // 2. Test JSON generation with bool field
        List<String[]> boolFields = List.of(
                new String[]{"is_visible", "bool"}
        );
        json = ProtoGrpcUtils.generateJsonBody(boolFields);
        checkContains("Bool field", json, "\"is_visible\": false");

        // 3. Test JSON generation with string field
        List<String[]> stringFields = List.of(
                new String[]{"name", "string"}
        );
        json = ProtoGrpcUtils.generateJsonBody(stringFields);
        checkContains("String field", json, "\"name\": \"\"");

        // 4. Test JSON generation with mixed types
        List<String[]> mixedFields = List.of(
                new String[]{"uid", "int64"},
                new String[]{"name", "string"},
                new String[]{"is_active", "bool"}
        );
        json = ProtoGrpcUtils.generateJsonBody(mixedFields);
        checkContains("Mixed uid", json, "\"uid\": 0");
        checkContains("Mixed name", json, "\"name\": \"\"");
        checkContains("Mixed active", json, "\"is_active\": false");

        // 5. Test empty fields
        List<String[]> emptyFields = List.of();
        json = ProtoGrpcUtils.generateJsonBody(emptyFields);
        check("Empty fields", "{}", json);

        // 6. Test message field
        List<String[]> messageFields = List.of(
                new String[]{"request", "SomeMessage"}
        );
        json = ProtoGrpcUtils.generateJsonBody(messageFields);
        checkContains("Message field", json, "\"request\": {}");

        // 7. Test float field
        List<String[]> floatFields = List.of(
                new String[]{"score", "float"}
        );
        json = ProtoGrpcUtils.generateJsonBody(floatFields);
        checkContains("Float field", json, "\"score\": 0");

        // 8. Test double field
        List<String[]> doubleFields = List.of(
                new String[]{"price", "double"}
        );
        json = ProtoGrpcUtils.generateJsonBody(doubleFields);
        checkContains("Double field", json, "\"price\": 0");

        // 9. Test bytes field
        List<String[]> bytesFields = List.of(
                new String[]{"data", "bytes"}
        );
        json = ProtoGrpcUtils.generateJsonBody(bytesFields);
        checkContains("Bytes field", json, "\"data\": \"\"");

        System.out.println("All ProtoGrpcUtils tests passed ✓");
    }

    private static void check(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + " failed: expected[" + expected + "] actual[" + actual + "]");
        }
        System.out.println("[PASS] " + name);
    }

    private static void checkContains(String name, String actual, String fragment) {
        if (actual == null || !actual.contains(fragment)) {
            throw new AssertionError(name + " failed: did not contain [" + fragment + "] actual[" + actual + "]");
        }
        System.out.println("[PASS] " + name + " -> contains " + fragment);
    }
}
