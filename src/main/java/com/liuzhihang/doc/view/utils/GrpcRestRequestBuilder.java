package com.liuzhihang.doc.view.utils;

import java.util.List;

public final class GrpcRestRequestBuilder {
    private GrpcRestRequestBuilder() {}
    public static String build(String host, String service, String method, List<String[]> fields, boolean descriptions) {
        String address = host.replaceFirst("/+$", "");
        if (address.startsWith("http://")) address = address.substring(7);
        else if (address.startsWith("https://")) address = "grpcs://" + address.substring(8);
        StringBuilder text = new StringBuilder("### ").append(service).append('/').append(method).append('\n');
        if (descriptions) for (String[] field : fields) {
            if (field.length > 2 && field[2] != null && !field[2].isBlank()) {
                text.append("# ").append(field[0]).append(": ").append(field[2].replace('\n', ' ').replace('\r', ' ')).append('\n');
            }
        }
        return text.append("GRPC ").append(address).append('/').append(service).append('/').append(method)
                .append("\n\n").append(ProtoGrpcUtils.generateJsonBody(fields)).append('\n').toString();
    }
}
