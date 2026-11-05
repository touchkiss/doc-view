package com.liuzhihang.doc.view.contract;

import com.intellij.protobuf.lang.psi.PbFile;
import com.intellij.protobuf.lang.psi.PbMessageType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.ProtoToPsiClassConverter;
import com.liuzhihang.doc.view.utils.ProtoUtils;

import java.util.Map;

/** Real protobuf PSI, covering scalar keyword detection without protobuf internal API. */
public final class ForkProtoPsiCompatibilityTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testEveryScalarRetainsItsJavaWireMapping() {
        PsiClass converted = convert("""
                syntax = "proto3";
                package fork.proto;
                message Scalars {
                    double double_value = 1;
                    float float_value = 2;
                    int32 int32_value = 3;
                    int64 int64_value = 4;
                    uint32 uint32_value = 5;
                    uint64 uint64_value = 6;
                    sint32 sint32_value = 7;
                    sint64 sint64_value = 8;
                    fixed32 fixed32_value = 9;
                    fixed64 fixed64_value = 10;
                    sfixed32 sfixed32_value = 11;
                    sfixed64 sfixed64_value = 12;
                    bool bool_value = 13;
                    string string_value = 14;
                    bytes bytes_value = 15;
                }
                """, "Scalars");
        Map<String, String> expected = Map.ofEntries(
                Map.entry("double_value", "Double"), Map.entry("float_value", "Float"),
                Map.entry("int32_value", "Integer"), Map.entry("int64_value", "Long"),
                Map.entry("uint32_value", "Integer"), Map.entry("uint64_value", "Long"),
                Map.entry("sint32_value", "Integer"), Map.entry("sint64_value", "Long"),
                Map.entry("fixed32_value", "Integer"), Map.entry("fixed64_value", "Long"),
                Map.entry("sfixed32_value", "Integer"), Map.entry("sfixed64_value", "Long"),
                Map.entry("bool_value", "Boolean"), Map.entry("string_value", "String"),
                Map.entry("bytes_value", "String"));
        assertEquals(15, converted.getFields().length);
        expected.forEach((name, type) -> assertType(converted, name, type));
        assertTrue(DocViewUtils.fieldDesc(field(converted, "bytes_value")).contains("base64"));
        assertTrue(ProtoUtils.isSyntheticProtoClass(converted));
    }

    public void testCustomNamesQualifiedTypesMapEnumWellKnownAndRecursionRemainDistinct() {
        PsiClass converted = convert("""
                syntax = "proto3";
                package fork.proto;
                message Envelope {
                    message Int32 { string label = 1; }
                    enum Status { UNKNOWN = 0; READY = 1; }
                    int32 scalar = 1;
                    Int32 local = 2;
                    .fork.proto.Envelope.Int32 qualified = 3;
                    repeated Int32 many = 4;
                    map<string, Int32> indexed = 5;
                    Status status = 6;
                    google.protobuf.Timestamp created_at = 7;
                    Envelope next = 8;
                    oneof choice {
                        string text = 9;
                        Int32 custom_choice = 10;
                    }
                }
                """, "Envelope");
        assertType(converted, "scalar", "Integer");
        assertType(converted, "local", "Int32");
        assertType(converted, "qualified", "Int32");
        assertType(converted, "many", "List<Int32>");
        assertType(converted, "indexed", "Map<String, Int32>");
        assertType(converted, "status", "String");
        assertTrue(DocViewUtils.fieldDesc(field(converted, "status")).contains("READY(1)"));
        assertType(converted, "created_at", "String");
        assertType(converted, "next", "Envelope");
        assertType(converted, "text", "String");
        assertType(converted, "custom_choice", "Int32");
        PsiField oneofField = field(converted, "custom_choice");
        assertNotNull(oneofField.getDocComment());
        assertTrue(oneofField.getDocComment().getText().contains("[oneof choice]"));
        String oneofDescription = DocViewUtils.fieldDesc(oneofField);
        // The fork's existing Javadoc field parser removes spaces from DOC_COMMENT_DATA.
        // Keep that format while checking that the group identity reaches generated documentation.
        assertTrue("Actual oneof description: " + oneofDescription,
                oneofDescription.replaceAll("\\s+", "").contains("[oneofchoice]"));
        assertFalse("A oneof message alternative must remain optional", DocViewUtils.isRequired(oneofField));
        assertFalse("A oneof scalar alternative must remain optional", DocViewUtils.isRequired(field(converted, "text")));
        assertEquals("Recursive Envelope must not create duplicate nested classes", 1, converted.getInnerClasses().length);
        assertType(converted.getInnerClasses()[0], "label", "String");
    }

    public void testImportedMessageStillResolvesAfterPublicScalarDetection() {
        myFixture.addFileToProject("external.proto", """
                syntax = "proto3";
                package fork.proto.external;
                message Payload { string external_value = 1; }
                """);
        PsiClass converted = convert("""
                syntax = "proto3";
                package fork.proto;
                import "external.proto";
                message Imported {
                    fork.proto.external.Payload payload = 1;
                    int64 sequence = 2;
                }
                """, "Imported");
        assertType(converted, "payload", "Payload");
        assertType(converted, "sequence", "Long");
        assertEquals(1, converted.getInnerClasses().length);
        assertType(converted.getInnerClasses()[0], "external_value", "String");
    }

    private PsiClass convert(String text, String name) {
        PsiFile file = myFixture.configureByText("Types.proto", text);
        assertTrue("Must parse through the installed Protocol Buffers plugin", file instanceof PbFile);
        PbMessageType message = PsiTreeUtil.findChildrenOfType(file, PbMessageType.class).stream()
                .filter(candidate -> name.equals(candidate.getName())).findFirst().orElseThrow();
        PsiClass converted = ProtoToPsiClassConverter.convert(message, getProject());
        assertNotNull(converted);
        return converted;
    }

    private void assertType(PsiClass owner, String name, String type) {
        assertEquals(name, type, field(owner, name).getType().getPresentableText());
    }

    private PsiField field(PsiClass owner, String name) {
        PsiField field = owner.findFieldByName(name, false);
        assertNotNull("Missing converted field " + name + " in " + owner.getText(), field);
        return field;
    }
}
