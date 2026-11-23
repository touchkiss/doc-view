package com.star.easydoc.common.util;

import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 字符串工具类
 *
 * @author wangchao
 * @date 2022/01/01
 */
public class StringUtil {
    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[a-z0-9])(?=[A-Z])");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[_$]+");


    /** 私有构造方法 */
    private StringUtil() {}

    /**
     * 检查传入字符串是否以给定的任意前缀开始
     *
     * @param text 文本
     * @param prefixList 前缀列表
     * @return boolean
     */
    public static boolean anyStartWith(String text, List<String> prefixList) {
        for (String prefix : prefixList) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 英文单词分割
     *
     * @param word 名称
     * @return 分割后内容
     */
    public static List<String> split(String word) {
        String separated = WORD_BOUNDARY.matcher(word).replaceAll("_");
        return WORD_SEPARATOR.splitAsStream(separated)
            .filter(part -> !part.isEmpty())
            .map(part -> part.toLowerCase(Locale.ROOT))
            .collect(Collectors.toList());
    }

    /**
     * 统计字符串结尾为c的数量
     *
     * @param str 字符串
     * @param c 字符
     * @return int
     */
    public static int endCount(String str, char c) {
        // 计算空行数
        if (str == null || str.length() == 0) {
            return 0;
        }
        char[] chars = str.toCharArray();
        int count = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            if (chars[i] == c) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

}
