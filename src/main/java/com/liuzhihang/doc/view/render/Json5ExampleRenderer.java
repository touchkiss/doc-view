package com.liuzhihang.doc.view.render;

import com.liuzhihang.doc.view.dto.DocViewParamData;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public final class Json5ExampleRenderer {
    private static final String INDENT = "    ";

    private Json5ExampleRenderer() {
    }

    public static String render(List<DocViewParamData> responseParamDataList) {
        if (responseParamDataList == null || responseParamDataList.isEmpty()) {
            return "";
        }
        StringBuilder json = new StringBuilder("{\n");
        appendProperties(json, responseParamDataList);
        return json.append('}').toString();
    }

    private static void appendProperties(StringBuilder json, List<DocViewParamData> rows) {
        List<DocViewParamData> properties = rows.stream().filter(row -> !row.isOmitFromExample()).toList();
        for (int index = 0; index < properties.size(); index++) {
            DocViewParamData row = properties.get(index);
            String indent = INDENT.repeat(Math.max(0, row.getTabCount()));
            json.append(indent).append('"').append(row.getName()).append("\": ");
            int dimensions = Math.max(1, arrayDimensions(row.getType()));
            boolean hasChildren = row.getChildList() != null && !row.getChildList().isEmpty();
            if (hasChildren) {
                appendObject(json, row, indent, dimensions);
            } else {
                appendLeaf(json, row, dimensions);
            }
            if (index + 1 < properties.size()) {
                json.append(',');
            }
            if (hasChildren) {
                json.append('\n');
            } else {
                appendDescription(json, row);
            }
        }
    }

    private static void appendObject(StringBuilder json, DocViewParamData row, String indent, int dimensions) {
        String elementIndent = INDENT.repeat(Math.max(0, row.getTabCount() + 1));
        if (row.isCollection()) {
            json.append("[".repeat(dimensions));
            appendDescription(json, row);
            json.append(elementIndent).append("{\n");
        } else {
            json.append('{');
            appendDescription(json, row);
        }

        List<DocViewParamData> children = row.getChildList();
        DocViewParamData first = children.getFirst();
        if (children.size() == 1 && first.isSyntheticContainerNode() && "element".equals(first.getName())) {
            children = first.getChildList();
        }
        appendProperties(json, children);

        if (row.isCollection()) {
            json.append(elementIndent).append("}\n").append(indent).append("]".repeat(dimensions));
        } else {
            json.append(indent).append('}');
        }
    }

    private static void appendLeaf(StringBuilder json, DocViewParamData row, int dimensions) {
        if (row.isCollection()) {
            json.append("[".repeat(dimensions));
            appendScalar(json, extractCollectionItemType(row.getType()), row.getExample());
            json.append("]".repeat(dimensions));
        } else if ("Map".equals(row.getType()) || row.getType().startsWith("Map<")) {
            json.append("{}");
        } else {
            appendScalar(json, row.getType(), row.getExample());
        }
    }

    private static void appendScalar(StringBuilder json, String type, String example) {
        if (doNotNeedQuote(type)) {
            json.append(example);
        } else {
            json.append('"').append(example).append('"');
        }
    }

    /**
     * 从 List<Integer> / Set<Long> / Collection<Boolean> 或 Java 数组提取标量元素类型名。
     * 解析失败时返回原 type，保持兼容。
     */
    private static String extractCollectionItemType(String type) {
        if (StringUtils.isBlank(type)) {
            return type;
        }
        int dimensions = arrayDimensions(type);
        if (dimensions > 0) return type.trim().substring(0, type.trim().length() - dimensions * 2).trim();
        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) {
            return type;
        }
        String inner = type.substring(lt + 1, gt).trim();
        // 处理 Map<K,V> 之类的情况：取第一个类型参数（K），但本方法主要用于 collection
        int comma = inner.indexOf(',');
        if (comma > -1) {
            inner = inner.substring(0, comma).trim();
        }
        return inner;
    }

    private static int arrayDimensions(String type) {
        if (type == null) return 0;
        String value = type.trim();
        int dimensions = 0;
        while (value.endsWith("[]")) {
            dimensions++;
            value = value.substring(0, value.length() - 2);
        }
        return dimensions;
    }

    private static boolean doNotNeedQuote(String type) {
        return "byte".equals(type) || "Byte".equals(type)
                || "short".equals(type) || "Short".equals(type)
                || "int".equals(type) || "Integer".equals(type)
                || "long".equals(type) || "Long".equals(type)
                || "float".equals(type) || "Float".equals(type)
                || "double".equals(type) || "Double".equals(type)
                || "boolean".equals(type) || "Boolean".equals(type);
    }

    private static void appendDescription(StringBuilder json, DocViewParamData row) {
        if (StringUtils.isNotBlank(row.getDesc())) {
            json.append(INDENT).append("// ").append(row.getDesc());
        }
        json.append('\n');
    }
}
