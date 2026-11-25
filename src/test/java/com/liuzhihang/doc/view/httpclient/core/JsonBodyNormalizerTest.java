package com.liuzhihang.doc.view.httpclient.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonBodyNormalizerTest {
    @Test public void convertsJson5WithoutTouchingStrings() {
        assertEquals("{\"name\":\"张三\",\"url\":\"https://a/b/*x*/\",\"tags\":[\"a\"]}",
            JsonBodyNormalizer.normalize("{ // name\nname:'张三', url:'https://a/b/*x*/', /* off */ tags:['a',],}"));
    }
    @Test public void preservesLargeNumbersAndConvertsHex() {
        assertEquals("{\"id\":900719925474099312345,\"price\":0.1234567890123456789,\"hex\":255}",
            JsonBodyNormalizer.normalize("{id:900719925474099312345,price:.1234567890123456789,hex:0xFF}"));
    }
    @Test public void preservesStrictJsonExactly() {
        String input = "{\n  \"id\": -0, \"x\": 1e+3, \"text\": \"\\u4e2d\"\n}";
        assertEquals(input, JsonBodyNormalizer.normalize(input));
    }
    @Test public void preservesLongStrictJsonString() {
        String input = "{\"notes\":\"" + "field description ".repeat(2048) + "\"}";
        assertEquals(input, JsonBodyNormalizer.normalize(input));
    }
    @Test public void supportsPrimitiveRootAndEmptyBody() {
        assertEquals("\"hello\"", JsonBodyNormalizer.normalize("'hello'"));
        assertEquals("true", JsonBodyNormalizer.normalize("true"));
        assertEquals("", JsonBodyNormalizer.normalize(""));
    }
    @Test public void rejectsInvalidAndAmbiguousInput() {
        for (String input : new String[]{"{a:}", "{a:1} garbage", "{a:NaN}", "{a:Infinity}", "{a:1,a:2}", "/* unfinished", "{a:01}", "{a:0b11}"}) {
            assertThrows(input, IllegalArgumentException.class, () -> JsonBodyNormalizer.normalize(input));
        }
    }
    @Test public void handlesJson5EscapesAndContinuations() {
        assertEquals("{\"text\":\"it's\\tAB\"}",
            JsonBodyNormalizer.normalize("{text:'it\\'s\\t\\x41\\\nB'}"));
    }
    @Test public void recognizesOnlyJsonMediaTypes() {
        assertTrue(JsonBodyNormalizer.isJsonMediaType("Application/JSON; charset=UTF-8"));
        assertTrue(JsonBodyNormalizer.isJsonMediaType("application/problem+json"));
        assertFalse(JsonBodyNormalizer.isJsonMediaType("text/plain"));
        assertFalse(JsonBodyNormalizer.isJsonMediaType(null));
    }
    @Test public void preservesScientificNotationValuesAndTrailingDecimalPoints() {
        assertEquals("{\"a\":0.001,\"b\":1E+12,\"c\":1}", JsonBodyNormalizer.normalize("{a:1e-3,b:1e12,c:1.,}"));
        assertEquals("{\"a\":1.2e3}", JsonBodyNormalizer.normalize("{\"a\":1.2e3}"));
        assertEquals("{\"a\":1E+3}", JsonBodyNormalizer.normalize("{a:1e3}"));
    }
    @Test public void handlesNestedNumbersUnicodeKeysAndControlEscapes() {
        assertEquals("{\"中文\":[-255,{\"n\":0.02,\"v\":\"\\u000b\"}],\"ok\":null}",
            JsonBodyNormalizer.normalize("{中文:[-0xFF,{n:+.02,v:'\\v'},],ok:null,}"));
    }
}
