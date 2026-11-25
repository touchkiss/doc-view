package com.liuzhihang.doc.view.httpclient.core;

import java.util.ArrayList;
import java.util.List;

/** Lexes comments without treating comment delimiters inside strings as comments. */
public final class Json5Comments {
    public record Range(int start, int end) {}
    private Json5Comments() {}
    public static List<Range> ranges(String text) {
        List<Range> result = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            char current = text.charAt(offset);
            if (current == '\'' || current == '"') {
                offset = endOfQuotedString(text, offset);
            } else if (text.startsWith("//", offset) || text.startsWith("/*", offset)) {
                int end = endOfComment(text, offset);
                result.add(new Range(offset, end));
                offset = end;
            } else {
                offset++;
            }
        }
        return result;
    }

    static int endOfQuotedString(String text, int start) {
        char quote = text.charAt(start);
        int offset = start + 1;
        while (offset < text.length()) {
            char next = text.charAt(offset++);
            if (next == quote) {
                break;
            }
            if (next == '\\' && offset < text.length()) {
                offset++;
            }
        }
        return offset;
    }

    private static int endOfComment(String text, int start) {
        int offset = start + 2;
        if (text.charAt(start + 1) == '*') {
            int end = text.indexOf("*/", offset);
            return end < 0 ? text.length() : end + 2;
        }
        while (offset < text.length() && "\r\n\u2028\u2029".indexOf(text.charAt(offset)) < 0) {
            offset++;
        }
        return offset;
    }
}
