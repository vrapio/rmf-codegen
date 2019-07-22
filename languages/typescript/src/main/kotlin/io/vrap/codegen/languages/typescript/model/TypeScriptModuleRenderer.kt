package io.vrap.codegen.languages.typescript.model

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.*
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.*

class TypeScriptModuleRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : TsObjectTypeExtensions, EObjectExtensions, FileProducer {

    @Inject
    lateinit var allAnyTypes: MutableList<AnyType>

    override fun produceFiles(): List<TemplateFile> {
        return allAnyTypes.filter { it is ObjectType || it is StringType }.groupBy { moduleName(it) }
            .map { entry: Map.Entry<String, List<AnyType>> -> buildModule(entry.key, entry.value) }
            .toList()
    }

    private fun moduleName(it: AnyType): String {
        val t = it.toVrapType()
        return when (t) {
            is VrapObjectType -> t.`package`
            is VrapEnumType -> t.`package`
            else -> throw IllegalArgumentException("Unsupported type ${t}")
        }
    }

    private fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        var sortedTypes = types.filter { it !is UnionType }.sortedByTopology(AnyType::getSuperTypes)
        val content = """
           |/* tslint:disable */
           |//Generated file, please do not change
           |
           |${getImportsForModule(moduleName, sortedTypes)}
           |
           |${sortedTypes.map { it.renderAnyType() }.joinToString(separator = "\n")}
       """.trimMargin().keepIndentation()

        return TemplateFile(content, moduleName.replace(".", "/") + ".ts")

    }

    private fun AnyType.renderAnyType(): String {
        return when (this) {
            is ObjectType -> this.renderObjectType()
            is StringType -> this.renderStringType()
            else -> throw IllegalArgumentException("unhandled case ${this.javaClass}")
        }
    }

    private fun ObjectType.renderObjectType(): String {
        return if (discriminator() != null) {
            if (discriminatorValue === null) {
                """
                |${toComment().escapeAll()}
                |export type ${name} =
                |  <${subTypes.filter { !it.isInlineType }.map { it.renderTypeExpr() }.joinToString(" |\n")}>
                |;
                """.trimMargin()
            } else {
                """
                |${toComment().escapeAll()}
                |export interface ${name} {
                |  readonly ${discriminator()}: "${discriminatorValue}";
                |  <${renderPatternSpec()}>
                |  <${renderPropertyDecls(true)}>
                |}
                """.trimMargin()
            }
        } else {
            """
                |${this.toComment().escapeAll()}
                |export interface ${name} ${renderExtendsExpr()}{
                |  <${renderPatternSpec()}>
                |  <${renderPropertyDecls(false)}>
                |}
            """.trimMargin()
        }
    }

    /**
     * Renders the optional typescript extends expression of this types type.
     *
     * @return the rendered extends expression
     */
    fun ObjectType.renderExtendsExpr(): String {
        return type?.toVrapType()?.simpleTSName()?.let { "extends $it " } ?: ""
    }

    /**
     * Renders the properties of this object type as typescript property declarations.
     * Excludes pattern proprties and discriminator properties.
     *
     * @param all if true renders all inherited properties, if false renders only direct properties
     * @return the rendered property type declarations
     */
    fun ObjectType.renderPropertyDecls(all: Boolean): String {
        val renderProperties = if (all) allProperties else properties
        return renderProperties
            .filter { !it.isPatternProperty() && it.name != discriminator() }
            .map {
                val comment: String = it.type.toComment().escapeAll()
                val optional = if (it.required) "" else "?"
                """
                    |${comment}
                    |readonly ${it.name}${optional}: ${it.type.renderTypeExpr()}
                    """.trimMargin()
            }
            .joinToString(";\n")
    }

    /**
     * Renders the typescript type expression for this types type.
     *
     * @param the typescript expression
     */
    fun AnyType.renderTypeExpr(): String {
        return when (this) {
            is UnionType -> oneOf.map { it.renderTypeExpr() }.joinToString(" | ")
            is IntersectionType -> allOf.map { it.renderTypeExpr() }.joinToString(" & ")
            is NilType -> "null"
            else -> toVrapType().simpleTSName()
        }
    }

    private fun StringType.renderStringType(): String {
        val vrapType = this.toVrapType() as VrapEnumType

        return """
        |${this.toComment().escapeAll()}
        |export type ${vrapType.simpleClassName} =
        |   <${this.renderEnumValues()}>;
        """.trimMargin()
    }

    private fun StringType.renderEnumValues(): String = enumValues()
        .map { "'${it}'" }
        .joinToString(" |\n")


    private fun StringType.enumValues() = enum?.filter { it is StringInstance }
        ?.map { (it as StringInstance).value }
        ?.filterNotNull() ?: listOf()

    /**
     * If the handled is a map type then the map args should be specified
     */
    private fun ObjectType.renderPatternSpec(): String? {
        return allProperties
            .filter { it.isPatternProperty() }
            .firstOrNull()
            .let {
                if (it != null) "[key:string]: ${it.type.renderTypeExpr()}" else ""
            }
    }
}
