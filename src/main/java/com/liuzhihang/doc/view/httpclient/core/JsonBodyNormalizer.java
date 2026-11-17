package com.liuzhihang.doc.view.httpclient.core;

import de.marhali.json5.*;
import de.marhali.json5.config.Json5Options;
import de.marhali.json5.stream.Json5Lexer;
import java.io.StringReader;
import java.util.Locale;
import java.util.regex.Pattern;

/** Stateless, lossless-number JSON5 to strict JSON conversion shared by every output path. */
public final class JsonBodyNormalizer {
    private static final Json5Options OPTIONS = Json5Options.builder().build();
    private static final Pattern JSON_TOKEN = Pattern.compile(
        "\"(?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*\"|" +
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|true|false|null|[{}\\[\\]:,]");
    private JsonBodyNormalizer() {}

    public static boolean isJsonMediaType(String type) {
        if (type == null) return false;
        String mime = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mime.equals("application/json") || (mime.startsWith("application/") && mime.endsWith("+json"));
    }

    public static String normalize(String body) {
        if (body.isBlank()) return body;
        try {
            var numbers = Json5Numbers.prepare(body);
            Json5Lexer lexer = new Json5Lexer(new StringReader(numbers.source() + "\n]"), OPTIONS);
            Json5Element value = lexer.nextValue();
            if (value == null || lexer.nextClean() != ']' || lexer.nextClean() != 0) throw new IllegalArgumentException("请求体含有多余内容");
            // Parsing first also validates JSON and rejects duplicate keys. Keep valid strict JSON byte-for-byte.
            if (isStrictJson(body)) return body;
            StringBuilder out = new StringBuilder(body.length());
            var numericValues = numbers.values().iterator();
            write(value, out, 0, numericValues);
            if (numericValues.hasNext()) throw new IllegalArgumentException("JSON5 数字只能出现在值的位置");
            return out.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON5 请求体无效：" + e.getMessage(), e);
        } catch (StackOverflowError e) {
            throw new IllegalArgumentException("JSON5 请求体嵌套过深", e);
        }
    }

    private static boolean isStrictJson(String text) {
        var matcher = JSON_TOKEN.matcher(text);
        int offset = 0;
        String previous = "";
        while (offset < text.length()) {
            char c = text.charAt(offset);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') { offset++; continue; }
            matcher.region(offset, text.length());
            if (!matcher.lookingAt()) return false;
            String token = matcher.group();
            if ((token.equals("}") || token.equals("]")) && previous.equals(",")) return false;
            if (token.equals(":") && !previous.startsWith("\"")) return false;
            previous = token;
            offset = matcher.end();
        }
        return true;
    }

    private static void write(Json5Element value, StringBuilder out, int depth, java.util.Iterator<String> numbers) {
        if (depth > 512) throw new IllegalArgumentException("JSON5 请求体嵌套超过 512 层");
        if (value.isJson5Null()) { out.append("null"); return; }
        if (value.isJson5Object()) {
            out.append('{');
            boolean first = true;
            for (var entry : value.getAsJson5Object().entrySet()) {
                if (!first) out.append(',');
                quote(entry.getKey(), out); out.append(':');
                write(entry.getValue(), out, depth + 1, numbers); first = false;
            }
            out.append('}');
        } else if (value.isJson5Array()) {
            out.append('['); boolean first = true;
            for (var child : value.getAsJson5Array()) {
                if (!first) out.append(',');
                write(child, out, depth + 1, numbers); first = false;
            }
            out.append(']');
        } else {
            Json5Primitive p = value.getAsJson5Primitive();
            if (p.isString()) quote(p.getAsString(), out);
            else if (p.isBoolean()) out.append(p.getAsBoolean());
            else {
                if (!numbers.hasNext()) throw new IllegalArgumentException("JSON5 数字映射不一致");
                out.append(numbers.next());
            }
        }
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
