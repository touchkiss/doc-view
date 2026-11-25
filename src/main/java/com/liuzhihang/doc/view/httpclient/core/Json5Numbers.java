package com.liuzhihang.doc.view.httpclient.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

/** Shields exact numeric tokens from the upstream lexer's exponent and trailing-dot bugs. */
final class Json5Numbers {
    record Prepared(String source, List<String> values) {}
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:(?:0|[1-9]\\d*)(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern HEX = Pattern.compile("[+-]?0[xX][0-9a-fA-F]+");
    private Json5Numbers() {}
    static Prepared prepare(String source) {
        var comments = Json5Comments.ranges(source);
        List<String> values = new ArrayList<>();
        StringBuilder safe = new StringBuilder(source.length());
        int commentIndex = 0;
        int offset = 0;
        while (offset < source.length()) {
            int start = offset;
            char current = source.charAt(offset);
            if (commentIndex < comments.size() && offset == comments.get(commentIndex).start()) {
                offset = comments.get(commentIndex++).end();
            } else if (current == '\'' || current == '"') {
                offset = Json5Comments.endOfQuotedString(source, start);
            } else if (Character.isUnicodeIdentifierStart(current) || current == '$' || current == '\\') {
                offset = endOfIdentifier(source, start);
            } else if (isNumberStart(current)) {
                offset = endOfNumber(source, start);
                values.add(canonical(source.substring(start, offset), start));
                safe.append('0');
                continue;
            } else {
                offset++;
            }
            safe.append(source, start, offset);
        }
        return new Prepared(safe.toString(), values);
    }

    private static int endOfIdentifier(String source, int start) {
        int offset = start + 1;
        while (offset < source.length()) {
            char c = source.charAt(offset);
            if (!Character.isUnicodeIdentifierPart(c) && c != '$' && c != '\\') {
                break;
            }
            offset++;
        }
        return offset;
    }

    private static boolean isNumberStart(char c) {
        return c == '+' || c == '-' || c == '.' || (c >= '0' && c <= '9');
    }

    private static int endOfNumber(String source, int start) {
        int offset = start + 1;
        while (offset < source.length()) {
            char c = source.charAt(offset);
            if (",]}:/".indexOf(c) >= 0 || Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\ufeff') {
                break;
            }
            offset++;
        }
        return offset;
    }

    private static String canonical(String text, int offset) {
        try {
            if (HEX.matcher(text).matches()) {
                int sign = text.charAt(0) == '-' ? -1 : 1;
                return new BigInteger(text.replaceFirst("^[+-]?0[xX]", ""), 16).multiply(BigInteger.valueOf(sign)).toString();
            }
            if (DECIMAL.matcher(text).matches()) {
                return new BigDecimal(text).toString();
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数字超出支持范围，位置 " + (offset + 1), e);
        }
        throw new IllegalArgumentException("无效 JSON5 数字，位置 " + (offset + 1) + "（标准 JSON 不支持 NaN/Infinity）");
    }
}
