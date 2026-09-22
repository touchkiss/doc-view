package com.liuzhihang.doc.view.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, token-safe result values returned by the YApi MCP tool. */
public final class McpResult {

    private McpResult() {
    }

    public record Item(Status status, String reference, String yapiUrl, String errorCode, String message) {

        public Item {
            status = Objects.requireNonNull(status, "status");
            reference = safe(reference);
            yapiUrl = safe(yapiUrl);
            errorCode = safe(errorCode);
            message = safe(message);
        }

        public static Item created(String reference, String yapiUrl) {
            return new Item(Status.CREATED, reference, yapiUrl, null, null);
        }

        public static Item updated(String reference, String yapiUrl) {
            return new Item(Status.UPDATED, reference, yapiUrl, null, null);
        }

        public static Item skipped(String reference, String message) {
            return new Item(Status.SKIPPED, reference, null, null, message);
        }

        public static Item failed(String reference, String errorCode, String message) {
            return new Item(Status.FAILED, reference, null, errorCode, message);
        }

        private Map<String, Object> structuredContent() {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("reference", reference);
            if (errorCode != null) {
                content.put("errorCode", errorCode);
                content.put("message", message);
                return content;
            }
            if (yapiUrl != null) {
                content.put("yapiUrl", yapiUrl);
                return content;
            }
            content.put("message", message);
            return content;
        }
    }

    public enum Status {
        CREATED,
        UPDATED,
        SKIPPED,
        FAILED
    }

    public record UploadBatch(String projectPath, String reference, List<Item> created, List<Item> updated,
                              List<Item> skipped, List<Item> failed) {

        public UploadBatch {
            projectPath = safe(projectPath);
            reference = safe(reference);
            created = List.copyOf(created);
            updated = List.copyOf(updated);
            skipped = List.copyOf(skipped);
            failed = List.copyOf(failed);
        }

        public static UploadBatch of(String projectPath, String reference, List<Item> created, List<Item> updated,
                                     List<Item> skipped, List<Item> failed) {
            return new UploadBatch(projectPath, reference, created, updated, skipped, failed);
        }

        public static UploadBatch failure(String projectPath, String reference, McpException.Code code, String message) {
            return new UploadBatch(projectPath, reference, List.of(), List.of(), List.of(),
                    List.of(Item.failed(reference, code.name(), message)));
        }

        public boolean isError() {
            return !failed.isEmpty();
        }

        public Map<String, Object> structuredContent() {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("projectPath", projectPath);
            content.put("reference", reference);
            content.put("created", created.stream().map(Item::structuredContent).toList());
            content.put("updated", updated.stream().map(Item::structuredContent).toList());
            content.put("skipped", skipped.stream().map(Item::structuredContent).toList());
            content.put("failed", failed.stream().map(Item::structuredContent).toList());
            return content;
        }
    }

    static String safe(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)(token\\s*[=:]\\s*)[^&\\s,}\\]\\\"]+", "$1***")
                .replaceAll("(?i)(\\\"token\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
    }
}
