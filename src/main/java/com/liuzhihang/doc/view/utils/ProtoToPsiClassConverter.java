package com.liuzhihang.doc.view.utils;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.compiler.PluginProtos;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;

public class ProtoToPsiClassConverter {

    @Nullable
    public static PsiClass convertProtoToPsiClass(@NotNull PsiFile protoFile, @NotNull Project project) {
        // Ensure the file is a .proto file
        if (!protoFile.getName().endsWith(".proto")) {
            return null;
        }

        // Parse the .proto file and generate Java code
        String javaCode = generateJavaCodeFromProto(protoFile);

        // Create a PsiFile from the generated Java code
        PsiFile javaPsiFile = PsiFileFactory.getInstance(project).createFileFromText("GeneratedClass.java", JavaLanguage.INSTANCE, javaCode);

        // Extract the PsiClass from the generated Java PsiFile
        return PsiTreeUtil.findChildOfType(javaPsiFile, PsiClass.class);
    }

    private static String generateJavaCodeFromProto(PsiFile protoFile) {
        try {
            FileInputStream fis = new FileInputStream(protoFile.getVirtualFile().getPath());
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto.parseFrom(fis);
            Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, new Descriptors.FileDescriptor[]{});

            StringBuilder javaCode = new StringBuilder();
            for (Descriptors.Descriptor descriptor : fileDescriptor.getMessageTypes()) {
                javaCode.append(generateJavaClassFromDescriptor(descriptor));
            }
            return javaCode.toString();
        } catch (IOException | Descriptors.DescriptorValidationException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String generateJavaClassFromDescriptor(Descriptors.Descriptor descriptor) {
        StringBuilder classCode = new StringBuilder();
        classCode.append("public class ").append(descriptor.getName()).append(" {\n");

        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            classCode.append("    private ").append(getJavaType(field)).append(" ").append(field.getName()).append(";\n");
        }

        classCode.append("}\n");
        return classCode.toString();
    }

    private static String getJavaType(Descriptors.FieldDescriptor field) {
        switch (field.getJavaType()) {
            case INT:
                return "int";
            case LONG:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case BOOLEAN:
                return "boolean";
            case STRING:
                return "String";
            case BYTE_STRING:
                return "com.google.protobuf.ByteString";
            case ENUM:
                return field.getEnumType().getName();
            case MESSAGE:
                return field.getMessageType().getName();
            default:
                return "Object";
        }
    }
}