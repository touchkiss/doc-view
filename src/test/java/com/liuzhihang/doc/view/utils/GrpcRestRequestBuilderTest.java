package com.liuzhihang.doc.view.utils;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class GrpcRestRequestBuilderTest {
    @Test public void formatsGrpcRequestAndPlacesDescriptionsOutsideJsonBody() {
        String result = GrpcRestRequestBuilder.build("http://localhost:9090/", "demo.UserService", "Find", List.<String[]>of(new String[]{"id", "string", "用户编号"}), true);
        assertTrue(result.contains("GRPC localhost:9090/demo.UserService/Find"));
        assertTrue(result.contains("# id: 用户编号"));
        assertTrue(result.contains("\n\n{\n  \"id\": \"\"\n}"));
        assertFalse(GrpcRestRequestBuilder.build("https://host", "S", "M", List.of(), false).contains("# id"));
        assertTrue(GrpcRestRequestBuilder.build("https://host", "S", "M", List.of(), false).contains("GRPC grpcs://host/S/M"));
    }
}
