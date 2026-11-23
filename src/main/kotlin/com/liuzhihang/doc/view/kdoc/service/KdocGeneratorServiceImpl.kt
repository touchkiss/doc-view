package com.liuzhihang.doc.view.kdoc.service

import com.intellij.psi.PsiElement
import com.liuzhihang.doc.view.javadoc.service.generator.JavaDocGenerator
import java.util.function.Supplier
import com.liuzhihang.doc.view.kdoc.service.generator.impl.KtClassDocGenerator
import com.liuzhihang.doc.view.kdoc.service.generator.impl.KtNamedFunctionDocGenerator
import com.liuzhihang.doc.view.kdoc.service.generator.impl.KtObjectDocGenerator
import com.liuzhihang.doc.view.kdoc.service.generator.impl.KtPropertyDocGenerator
import com.liuzhihang.doc.view.service.DocGeneratorService
import org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import java.util.*

/**
 * @author wangchao
 * @date 2019/08/25
 */
class KdocGeneratorServiceImpl : DocGeneratorService {
    private val docGeneratorMap = mapOf(
        KtClass::class to KtClassDocGenerator(),
        KtObjectDeclaration::class to KtObjectDocGenerator(),
        KtNamedFunction::class to KtNamedFunctionDocGenerator(),
        KtProperty::class to KtPropertyDocGenerator()
    )

    /**
     * 生成
     *
     * @param psiElement 元素
     * @return {@link String }
     */
    override fun generate(psiElement: PsiElement): String = prepare(psiElement).get()

    fun prepare(psiElement: PsiElement): Supplier<String> {
        val generator: JavaDocGenerator = docGeneratorMap[psiElement::class] ?: return Supplier { "" }
        val prepared = generator.prepare(psiElement)
        return Supplier {
            prepared.get().lineSequence()
                .filter { StringUtils.isNotBlank(StringUtils.strip(it.trim(), "*").trim()) }
                .joinToString("\n")
        }
    }
}
