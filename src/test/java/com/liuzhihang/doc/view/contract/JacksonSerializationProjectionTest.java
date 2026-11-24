package com.liuzhihang.doc.view.contract;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.httpclient.core.JsonBodyNormalizer;
import com.liuzhihang.doc.view.service.DtoSchemaCacheService;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.testsupport.AnnotationStubs;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import com.liuzhihang.doc.view.utils.PojoUtils;

import java.util.Set;
import java.util.stream.Collectors;

public final class JacksonSerializationProjectionTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("""
                package com.fasterxml.jackson.annotation;
                public @interface JsonIgnoreProperties {
                    String[] value() default {};
                    boolean allowGetters() default false;
                    boolean allowSetters() default false;
                    boolean ignoreUnknown() default false;
                }
                """);
        myFixture.addClass("""
                package com.fasterxml.jackson.annotation;
                public @interface JsonIncludeProperties { String[] value() default {}; }
                """);
        myFixture.addClass("""
                package com.fasterxml.jackson.annotation;
                public @interface JsonInclude {
                    Include value() default Include.ALWAYS;
                    enum Include { ALWAYS, NON_NULL }
                }
                """);
        myFixture.addClass("""
                package com.fasterxml.jackson.annotation;
                public @interface JsonProperty { String value() default ""; }
                """);
    }

    public void testNestedGenericPageUsesSerializedPropertiesInAllResponseOutputs() {
        myFixture.addClass("""
                package fixture;
                public class Page<E> {
                    private java.util.List<E> list;
                    private long total;
                    private int pageNum;
                    private boolean hasMore;
                    public java.util.List<E> getList() { return list; }
                }
                """);
        PsiClass page = myFixture.addClass("""
                package fixture;
                import com.fasterxml.jackson.annotation.*;
                @JsonIncludeProperties({"list", SettlementPageDTO.PAGINATION})
                public class SettlementPageDTO<T> extends Page<T> {
                    static final String PAGINATION = "pagination";
                    /** Pagination metadata. */
                    public Metadata getPagination() { return new Metadata(); }
                    public String getDebug() { return ""; }
                    @JsonInclude(JsonInclude.Include.NON_NULL)
                    public static class Metadata {
                        private Integer pageNum;
                        private Integer pageSize;
                        private Long total;
                        @JsonProperty("total_pages")
                        private Long totalPages;
                        private Long optional = null;
                    }
                }
                """);
        PsiClass response = myFixture.addClass("""
                package fixture;
                public class Envelope {
                    private SettlementPageDTO<Row> data;
                    public static class Row { private long id; }
                }
                """);
        PsiType type = PsiTypesUtil.getClassType(response);
        Body body = ParamPsiUtils.buildRespBody(type);
        Body data = child(body, "data");
        assertEquals(Set.of("list", "pagination"), names(data));
        assertEquals(Set.of("id"), names(child(child(data, "list"), "element")));
        Body pagination = child(data, "pagination");
        assertTrue(pagination.getDesc().contains("Pagination metadata"));
        assertEquals(Set.of("pageNum", "pageSize", "total", "total_pages", "optional"), names(pagination));
        assertEquals(Set.of("list", "pagination"), names(PojoUtils.buildBody(page)));

        JsonObject json = JsonParser.parseString(ParamPsiUtils.getRespBodyJson(type)).getAsJsonObject();
        JsonObject pageJson = json.getAsJsonObject("data");
        assertEquals(Set.of("list", "pagination"), pageJson.keySet());
        assertEquals(Set.of("id"), pageJson.getAsJsonArray("list").get(0).getAsJsonObject().keySet());
        assertEquals(Set.of("pageNum", "pageSize", "total", "total_pages"),
                pageJson.getAsJsonObject("pagination").keySet());
        assertEquals(0, pageJson.getAsJsonObject("pagination").get("total_pages").getAsInt());

        DocView doc = new DocView();
        doc.setType(FrameworkEnum.NONE_POJO);
        doc.setRespBody(DtoSchemaCacheService.copyBody(body, null, false));
        DocViewData output = new DocViewData(getProject(), doc);
        assertTrue(output.getResponseParam().contains("total_pages"));
        assertFalse(output.getResponseParam().contains("has_more"));
        JsonObject json5Page = JsonParser.parseString(JsonBodyNormalizer.normalize(output.getResponseJson5()))
                .getAsJsonObject().getAsJsonObject("data");
        assertEquals(pageJson.keySet(), json5Page.keySet());
        assertEquals(pageJson.getAsJsonObject("pagination"), json5Page.getAsJsonObject("pagination"));
    }

    public void testAllowlistUsesJsonPropertyName() {
        PsiClass dto = myFixture.addClass("""
                package fixture;
                import com.fasterxml.jackson.annotation.*;
                @JsonIncludeProperties("total_pages")
                public class Metadata {
                    @JsonProperty("total_pages")
                    private Long totalPages;
                    private Long total;
                }
                """);
        assertEquals(Set.of("total_pages"), names(ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(dto))));
        assertEquals(Set.of("total_pages"), ParamPsiUtils.getFieldsAndDefaultValue(dto, null).keySet());
    }

    public void testRequestPaginationOmitsIgnoredInheritedJsonProperties() {
        AnnotationStubs.installSpringWeb(myFixture);
        myFixture.addClass("""
                package org.springframework.web.bind.annotation;
                public @interface PostMapping { String[] value() default {}; }
                """);
        myFixture.addClass("""
                package org.springframework.web.bind.annotation;
                public @interface RequestBody {}
                """);
        PsiClass base = myFixture.addClass("""
                package fixture;
                import com.fasterxml.jackson.annotation.JsonProperty;
                public class Pagination {
                    private long total;
                    @JsonProperty("page_num") private int pageNum;
                    @JsonProperty("page_size") private int pageSize;
                    @JsonProperty("has_more") private boolean more;
                    @JsonProperty("next_cursor") private String nextCursor;
                }
                """);
        PsiClass page = myFixture.addClass("""
                package fixture;
                import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
                @JsonIgnoreProperties({"total", "has_more", SettlementPageRequestDTO.CURSOR})
                public class SettlementPageRequestDTO extends Pagination {
                    static final String CURSOR = "next_cursor";
                }
                """);
        PsiClass controller = myFixture.addClass("""
                package fixture;
                import org.springframework.web.bind.annotation.*;
                @RestController
                public class SettlementController {
                    @PostMapping("/list")
                    public void list(@RequestBody ListRequest request) {}
                    @PostMapping("/page")
                    public void page(@RequestBody SettlementPageRequestDTO request) {}
                    static class ListRequest { private SettlementPageRequestDTO pagination; }
                }
                """);
        Set<String> expected = Set.of("page_num", "page_size");
        SpringDocViewServiceImpl service = new SpringDocViewServiceImpl();
        DocView nested = service.buildClassMethodDoc(controller, controller.findMethodsByName("list", false)[0]);
        assertEquals(expected, names(child(nested.getReqBody(), "pagination")));
        JsonObject json = JsonParser.parseString(nested.getReqBodyExample()).getAsJsonObject();
        assertEquals(expected, json.getAsJsonObject("pagination").keySet());
        String json5 = new DocViewData(getProject(), nested).getRequestJson5();
        assertEquals(expected, JsonParser.parseString(JsonBodyNormalizer.normalize(json5))
                .getAsJsonObject().getAsJsonObject("pagination").keySet());

        DocView topLevel = service.buildClassMethodDoc(controller, controller.findMethodsByName("page", false)[0]);
        assertEquals(expected, names(topLevel.getReqBody()));
        assertEquals(expected, JsonParser.parseString(topLevel.getReqBodyExample()).getAsJsonObject().keySet());
        assertEquals(expected, names(PojoUtils.buildBody(page)));
        assertEquals(Set.of("total", "page_num", "page_size", "has_more", "next_cursor"),
                names(PojoUtils.buildBody(base)));
    }

    private static Set<String> names(Body body) {
        return body.getChildList().stream().map(Body::getName).collect(Collectors.toSet());
    }

    private static Body child(Body parent, String name) {
        return parent.getChildList().stream().filter(body -> name.equals(body.getName())).findFirst().orElseThrow();
    }
}
