package com.liuzhihang.doc.view.httpclient.core;

import java.util.ArrayList;
import java.util.List;

/** Lexes comments without treating comment delimiters inside strings as comments. */
public final class Json5Comments {
    public record Range(int start, int end) {}
    private Json5Comments() {}
    public static List<Range> ranges(String text) {
        List<Range> result = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                while (i < text.length()) {
                    char next = text.charAt(i++);
                    if (next == '\\' && i < text.length()) i++;
                    else if (next == quote) break;
                }
            } else if (c == '/' && i + 1 < text.length() && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                int start = i;
                boolean line = text.charAt(i + 1) == '/';
                i += 2;
                if (line) {
                    while (i < text.length() && "\r\n\u2028\u2029".indexOf(text.charAt(i)) < 0) i++;
                } else {
                    int end = text.indexOf("*/", i);
                    i = end < 0 ? text.length() : end + 2;
                }
                result.add(new Range(start, i));
            } else i++;
        }
        return result;
    }
}
