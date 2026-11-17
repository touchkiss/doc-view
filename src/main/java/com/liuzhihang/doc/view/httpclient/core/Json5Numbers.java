package com.liuzhihang.doc.view.httpclient.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

/** Shields exact numeric tokens from the upstream lexer's exponent and trailing-dot bugs. */
final class Json5Numbers {
    record Prepared(String source, List<String> values) {}
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:(?:0|[1-9][0-9]*)(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern HEX = Pattern.compile("[+-]?0[xX][0-9a-fA-F]+");
    private Json5Numbers() {}
    static Prepared prepare(String source) {
        var comments = Json5Comments.ranges(source);
        List<String> values = new ArrayList<>();
        StringBuilder safe = new StringBuilder(source.length());
        int commentIndex = 0;
        for (int i = 0; i < source.length();) {
            int start = i;
            char c = source.charAt(i);
            if (commentIndex < comments.size() && i == comments.get(commentIndex).start()) {
                i = comments.get(commentIndex++).end();
            } else if (c == '\'' || c == '"') {
                char quote = c; i++;
                while (i < source.length()) {
                    char n = source.charAt(i++);
                    if (n == '\\' && i < source.length()) i++;
                    else if (n == quote) break;
                }
            } else if (Character.isUnicodeIdentifierStart(c) || c == '$' || c == '\\') {
                // Include escaped identifier parts; their validity is checked by the JSON5 parser.
                i++;
                while (i < source.length() && (Character.isUnicodeIdentifierPart(source.charAt(i)) || source.charAt(i) == '$' || source.charAt(i) == '\\')) i++;
            } else if (c == '+' || c == '-' || c == '.' || (c >= '0' && c <= '9')) {
                i++;
                while (i < source.length()) {
                    char n = source.charAt(i);
                    if (",]}:/".indexOf(n) >= 0 || Character.isWhitespace(n) || Character.isSpaceChar(n) || n == '\ufeff') break;
                    i++;
                }
                String token = source.substring(start, i);
                values.add(canonical(token, start));
                safe.append('0');
                continue;
            } else i++;
            safe.append(source, start, i);
        }
        return new Prepared(safe.toString(), values);
    }
    private static String canonical(String text, int offset) {
        try {
            if (HEX.matcher(text).matches()) {
                int sign = text.charAt(0) == '-' ? -1 : 1;
                return new BigInteger(text.replaceFirst("^[+-]?0[xX]", ""), 16).multiply(BigInteger.valueOf(sign)).toString();
            }
            if (DECIMAL.matcher(text).matches()) return new BigDecimal(text).toString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数字超出支持范围，位置 " + (offset + 1), e);
        }
        throw new IllegalArgumentException("无效 JSON5 数字，位置 " + (offset + 1) + "（标准 JSON 不支持 NaN/Infinity）");
    }
}
