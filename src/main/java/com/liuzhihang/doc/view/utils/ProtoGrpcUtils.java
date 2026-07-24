package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing proto file gRPC service definitions.
 * Extracts service names, method names, and request message fields
 * for generating gRPC curl commands.
 *
 * @author liuzhihang
 */
public final class ProtoGrpcUtils {

    private ProtoGrpcUtils() {
    }

    /**
     * Pattern to match rpc method definition: rpc MethodName(RequestType) returns (ResponseType)
     */
    private static final Pattern RPC_METHOD_PATTERN = Pattern.compile(
            "\\s*rpc\\s+(\\w+)\\s*\\((\\w+)\\)\\s*returns\\s*\\((\\w+)\\)");

    /**
     * Pattern to match service definition: service ServiceName {
     */
    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "\\s*service\\s+(\\w+)\\s*\\{");

    /**
     * Pattern to match message definition: message MessageName {
     */
    private static final Pattern MESSAGE_PATTERN = Pattern.compile(
            "\\s*message\\s+(\\w+)\\s*\\{");

    /**
     * Pattern to match field definition: type fieldName = number;
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "\\s*(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*=\\s*\\d+");

    /**
     * Check if the cursor is on a proto gRPC method definition.
     *
     * @param editor the editor
     * @param psiFile the psi file
     * @return true if cursor is on an rpc method definition
     */
    public static boolean isGrpcMethod(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        if (!isProtoFile(psiFile)) {
            return false;
        }

        String line = getCurrentLine(editor);
        if (line == null) {
            return false;
        }

        return RPC_METHOD_PATTERN.matcher(line).find();
    }

    /**
     * Check if the file is a proto file.
     *
     * @param psiFile the psi file
     * @return true if file is a proto file
     */
    public static boolean isProtoFile(@NotNull PsiFile psiFile) {
        FileType fileType = psiFile.getFileType();
        return fileType.getName().equalsIgnoreCase("PROTO") ||
                psiFile.getName().endsWith(".proto");
    }

    /**
     * Extract the current line from editor.
     *
     * @param editor the editor
     * @return current line text or null
     */
    @Nullable
    public static String getCurrentLine(@NotNull Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        int lineStart = editor.getDocument().getLineStartOffset(
                editor.getDocument().getLineNumber(offset));
        int lineEnd = editor.getDocument().getLineEndOffset(
                editor.getDocument().getLineNumber(offset));
        return editor.getDocument().getText(new com.intellij.openapi.util.TextRange(lineStart, lineEnd));
    }

    /**
     * Extract service name and method name from cursor position.
     *
     * @param editor the editor
     * @param psiFile the psi file
     * @return array of [serviceName, methodName, requestType, responseType] or null
     */
    @Nullable
    public static String[] extractMethodSignature(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        String line = getCurrentLine(editor);
        if (line == null) {
            return null;
        }

        Matcher matcher = RPC_METHOD_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        String methodName = matcher.group(1);
        String requestType = matcher.group(2);
        String responseType = matcher.group(3);

        String serviceName = findServiceName(editor, psiFile);
        if (serviceName == null) {
            return null;
        }

        return new String[]{serviceName, methodName, requestType, responseType};
    }

    /**
     * Find the service name containing the current cursor position.
     *
     * @param editor the editor
     * @param psiFile the psi file
     * @return service name or null
     */
    @Nullable
    private static String findServiceName(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        String text = psiFile.getText();
        int offset = editor.getCaretModel().getOffset();

        // Find the last service definition before cursor
        Matcher serviceMatcher = SERVICE_PATTERN.matcher(text);
        String lastServiceName = null;
        int lastServiceEnd = -1;

        while (serviceMatcher.find()) {
            if (serviceMatcher.start() < offset) {
                lastServiceName = serviceMatcher.group(1);
                lastServiceEnd = findMatchingBrace(text, serviceMatcher.end());
            }
        }

        // Check if cursor is within the service block
        if (lastServiceName != null && offset <= lastServiceEnd) {
            return lastServiceName;
        }

        return null;
    }

    /**
     * Find matching closing brace for an opening brace position.
     *
     * @param text the text
     * @param startPos position after opening brace
     * @return position of matching closing brace
     */
    private static int findMatchingBrace(String text, int startPos) {
        int depth = 1;
        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return text.length();
    }

    /**
     * Parse request message type and extract field names with types.
     *
     * @param psiFile the proto file
     * @param messageTypeName the message type name to parse
     * @return list of [fieldName, fieldType] pairs
     */
    @NotNull
    public static List<String[]> parseMessageFields(@NotNull PsiFile psiFile, @NotNull String messageTypeName) {
        List<String[]> fields = new ArrayList<>();
        String text = psiFile.getText();

        // Find the message definition
        Pattern messageDefPattern = Pattern.compile(
                "\\s*message\\s+" + Pattern.quote(messageTypeName) + "\\s*\\{");
        Matcher messageMatcher = messageDefPattern.matcher(text);

        if (!messageMatcher.find()) {
            return fields;
        }

        int messageStart = messageMatcher.end();
        int messageEnd = findMatchingBrace(text, messageStart);
        String messageBody = text.substring(messageStart, messageEnd);

        // Parse fields
        String[] lines = messageBody.split("\n");
        for (String line : lines) {
            Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
            if (fieldMatcher.find()) {
                String fieldType = fieldMatcher.group(1);
                String fieldName = fieldMatcher.group(2);
                fields.add(new String[]{fieldName, fieldType});
            }
        }

        return fields;
    }

    /**
     * Generate JSON body with default values based on proto field types.
     *
     * @param fields list of [fieldName, fieldType] pairs
     * @return JSON string
     */
    @NotNull
    public static String generateJsonBody(@NotNull List<String[]> fields) {
        if (fields.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{\n");
        for (int i = 0; i < fields.size(); i++) {
            String[] field = fields.get(i);
            String fieldName = field[0];
            String fieldType = field[1];
            String defaultValue = getDefaultValueForType(fieldType);

            json.append("  \"").append(fieldName).append("\": ").append(defaultValue);
            if (i < fields.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("}");

        return json.toString();
    }

    /**
     * Get default value for a proto field type.
     *
     * @param fieldType the proto field type
     * @return default value as string
     */
    @NotNull
    private static String getDefaultValueForType(@NotNull String fieldType) {
        String lowerType = fieldType.toLowerCase();

        // Numeric types
        if (lowerType.contains("int") || lowerType.contains("uint") ||
                lowerType.contains("sint") || lowerType.contains("fixed") ||
                lowerType.contains("sfixed") || lowerType.equals("float") ||
                lowerType.equals("double")) {
            return "0";
        }

        // Boolean
        if (lowerType.equals("bool")) {
            return "false";
        }

        // String
        if (lowerType.equals("string")) {
            return "\"\"";
        }

        // Bytes
        if (lowerType.equals("bytes")) {
            return "\"\"";
        }

        // Enum (default is first value, typically 0)
        if (lowerType.contains("enum")) {
            return "0";
        }

        // Message types (nested objects)
        if (Character.isUpperCase(fieldType.charAt(0))) {
            return "{}";
        }

        // Default
        return "null";
    }
}
