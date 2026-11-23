package com.liuzhihang.doc.view.contract;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.PsiConstantUtils;

import java.util.List;

public final class ValidationDescriptionsTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testNumericAndFormatConstraintsReadDeclaredValues() {
        annotation("jakarta", "DecimalMin", "String value(); boolean inclusive() default true;");
        annotation("javax", "DecimalMax", "String value(); boolean inclusive() default true;");
        annotation("jakarta", "Digits", "int integer(); int fraction();");
        annotation("jakarta", "Pattern", "String regexp(); Flag[] flags() default {}; enum Flag { CASE_INSENSITIVE }");
        annotation("javax", "Email", "String regexp() default \".*\";");
        var dto = myFixture.addClass("""
                import jakarta.validation.constraints.DecimalMin;
                import javax.validation.constraints.DecimalMax;
                import jakarta.validation.constraints.Digits;
                import jakarta.validation.constraints.Pattern;
                import javax.validation.constraints.Email;

                class Limits {
                    static final String FLOOR = "0.125";
                    /** 交易金额 */
                    @DecimalMin(value = FLOOR, inclusive = false)
                    @DecimalMax("1000.01")
                    @Digits(integer = 4, fraction = 2)
                    String amount;

                    @Email(regexp = ".+@example[.]com")
                    @Pattern(regexp = "a|b", flags = Pattern.Flag.CASE_INSENSITIVE)
                    String email;
                }
                """);

        var amountField = dto.findFieldByName("amount", false);
        var min = amountField.getAnnotation("jakarta.validation.constraints.DecimalMin");
        assertNotNull("The annotation must resolve", min);
        var value = min.findAttributeValue("value");
        assertNotNull("The annotation must expose its bound", value);
        assertEquals("Bound expression: " + value.getText(), "0.125",
                PsiConstantUtils.constantValue(value));
        var amount = DocViewUtils.fieldDescription(amountField);
        assertEquals("交易金额", amount.sourceComment());
        assertTrue(amount.supplementaryText(), amount.supplementaryText().contains("> 0.125"));
        assertTrue(amount.supplementaryText(), amount.supplementaryText().contains("≤ 1000.01"));
        assertTrue(amount.render(), amount.render().contains("整数最多 4 位，小数最多 2 位"));

        var email = DocViewUtils.fieldDescription(dto.findFieldByName("email", false));
        assertTrue(email.supplementaryText(), email.supplementaryText().contains("邮箱格式"));
        assertTrue(email.render(), email.render().contains(".+@example[.]com"));
        assertTrue(email.render(), email.render().contains("a|b（CASE_INSENSITIVE）"));
    }

    public void testSimpleConstraintsAreDocumentedWithoutChangingRequiredInference() {
        for (String name : List.of("AssertFalse", "AssertTrue", "Future", "FutureOrPresent", "Negative",
                "NegativeOrZero", "NotBlank", "NotEmpty", "NotNull", "Null", "Past", "PastOrPresent",
                "Positive", "PositiveOrZero")) {
            annotation("jakarta", name, "");
        }
        var dto = myFixture.addClass("""
                import jakarta.validation.constraints.*;
                class Conditions {
                    @AssertFalse Boolean disabled;
                    @AssertTrue Boolean enabled;
                    @Future java.time.Instant future;
                    @FutureOrPresent java.time.Instant futureOrPresent;
                    @Negative Integer negative;
                    @NegativeOrZero Integer negativeOrZero;
                    @NotBlank String notBlank;
                    @NotEmpty String notEmpty;
                    @NotNull String notNull;
                    @Null String mustBeNull;
                    @Past java.time.Instant past;
                    @PastOrPresent java.time.Instant pastOrPresent;
                    @Positive Integer positive;
                    @PositiveOrZero Integer positiveOrZero;
                }
                """);

        List<String> descriptions = List.of("必须为 false", "必须为 true", "晚于当前时间", "不早于当前时间",
                "< 0", "≤ 0", "不能为 null 或空白", "不能为 null 或空", "不能为 null",
                "必须为 null", "早于当前时间", "不晚于当前时间", "> 0", "≥ 0");
        var fields = dto.getFields();
        for (int i = 0; i < fields.length; i++) {
            var field = fields[i];
            var description = DocViewUtils.fieldDescription(field);
            assertTrue(field.getName() + ": " + description.supplementaryText(),
                    description.supplementaryText().contains(descriptions.get(i)));
        }
        assertFalse(DocViewUtils.isRequired(dto.findFieldByName("positive", false)));
        assertFalse(DocViewUtils.isRequired(dto.findFieldByName("mustBeNull", false)));
    }

    public void testRepeatedPatternWithExplicitGroupsAndMarkdownSpecialCharacters() {
        annotation("jakarta", "Pattern", """
                String regexp();
                Class<?>[] groups() default {};
                @interface List { Pattern[] value(); }
                """);
        var dto = myFixture.addClass("""
                import jakarta.validation.constraints.Pattern;
                class Regexes {
                    interface Create {}
                    @Pattern.List({
                        @Pattern(regexp = "a|b"),
                        @Pattern(regexp = "[c]\\\\|d", groups = Create.class)
                    })
                    String expression;
                }
                """);
        var description = DocViewUtils.fieldDescription(dto.getFields()[0]);
        assertTrue(description.render(), description.render().contains("匹配正则:a|b"));
        assertTrue(description.render(), description.render().contains("匹配正则:[c]\\|d（分组 Create.class）"));
        var row = new DocViewParamData();
        row.setName("expression");
        row.setRequired(false);
        row.setDescription(description);
        String markdown = DocViewData.bodyMarkdown(List.of(row), false);
        assertTrue(markdown, markdown.contains("匹配正则:a&#124;b"));
        assertTrue(markdown, markdown.contains("匹配正则:[c]&#92;&#124;d"));
    }

    private void annotation(String namespace, String name, String body) {
        myFixture.addClass("package " + namespace + ".validation.constraints; public @interface " + name + " { " + body + " }");
    }
}
