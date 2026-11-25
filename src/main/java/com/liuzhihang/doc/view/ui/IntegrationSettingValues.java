package com.liuzhihang.doc.view.ui;

final class IntegrationSettingValues {

    private static final String URL_PATH_SEPARATOR = "/";
    private static final String URL_SCHEME_SEPARATOR = "://";

    private IntegrationSettingValues() {
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String trimmedOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    static String normalizedUrl(String value) {
        String normalized = trimmedOrEmpty(value);
        while (normalized.endsWith(URL_PATH_SEPARATOR) && !normalized.endsWith(URL_SCHEME_SEPARATOR)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
