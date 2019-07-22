package io.vrap.codegen.languages.typescript.joi

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.hasSubtypes
import io.vrap.codegen.languages.typescript.model.simpleTSName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.*

class JoiValidatorModuleRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : JoiObjectTypeExtensions, EObjectExtensions, FileProducer {
    @Inject
    lateinit var allAnyTypes: MutableList<AnyType>

    override fun produceFiles(): List<TemplateFile> {
        return allAnyTypes.groupBy { moduleName(it) }
            .map { entry -> buildModule("${entry.key}-types", entry.value) }
            .toList()
    }

    private fun moduleName(it: AnyType): String {
        val t = it.toVrapType()
        return when (t) {
            is VrapObjectType -> t.`package`
            is VrapEnumType -> t.`package`
            is UnionType -> ""
            else -> ""
        }
    }

    fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        val content = """
           |/* tslint:disable */
           |//Generated file, please do not change
           |
           |import * as Joi from 'joi'
           |${getImportsForJoiModule(moduleName, types)}
           |
           |const schema = {
           |  <${types.map { it.renderSchemaPlaceholder() }.joinToString(separator = ",\n")}>
           |}
           |
           |${types.map { it.renderJoiSchema() }.joinToString(separator = "\n\n")}
       """.trimMargin().keepIndentation()
        return TemplateFile(content, moduleName.replace(".", "/") + ".ts")

    }

    fun AnyType.renderSchemaPlaceholder() : String {
        return """<${toVrapType().simpleJoiName()}: null>"""
    }

    fun AnyType.renderJoiSchema(): String {
        return when (this) {
            is ObjectType -> this.renderJoiObjectSchema()
            is StringType -> this.renderJoiStringSchema()
            is UnionType -> this.renderJoiUnionSchema()
            else -> throw IllegalArgumentException("unhandled case ${this.javaClass}")
        }
    }

    fun UnionType.renderJoiUnionSchema():String {
        val arrayArg:String = this.getOneOf().map { "${it.toVrapType().simpleJoiName()}()"}
                .joinToString(prefix = "[",separator = ",",postfix = "]")
        val schemaDeclaration = """
            |schema.${toVrapType().simpleJoiName()} = Joi.lazy(() => Joi.alternatives($arrayArg))
            |
        """
        return (schemaDeclaration + toVrapType().renderSchemaExportFunction()).trimMargin()
    }

    fun VrapType.renderSchemaExportFunction(): String {
        return """
            |export function ${simpleJoiName()}() : Joi.AnySchema {
            |   return schema.${simpleJoiName()}
            |}
        """
    }

    fun ObjectType.renderJoiObjectSchema(): String {
        val schemaDeclaration : String
        if (this.hasSubtypes()) {
            val allSubsCases = this.subTypes
                .filterIsInstance(ObjectType::class.java)
                .filter { !it.discriminatorValue.isNullOrEmpty() }
                .map {
                    "${it.toVrapType().simpleJoiName()}()"
                }.joinToString(separator = ", ")

            schemaDeclaration = """schema.${toVrapType().simpleJoiName()} = Joi.lazy(() =\> Joi.alternatives([$allSubsCases]))"""
        } else {
            schemaDeclaration =  """schema.${toVrapType().simpleJoiName()} = <${renderPropertySchemas()}>"""
        }
        return (schemaDeclaration + toVrapType().renderSchemaExportFunction()).trimIndent()
    }

    fun ObjectType.renderPropertySchemas(): String {
        val nonPatternProperties = this.allProperties
                .filter { !it.isPatternProperty() }
                .sortedWith(PropertiesComparator)
                .map {it.renderPropertyTypeSchema() }
                .joinToString(separator = ",\n")
        val patternProperties = this.allProperties
                .filter { it.isPatternProperty() }
                .map { ".pattern(new RegExp('${it.name}'), ${it.type.toVrapType().simpleJoiName()}())" }
                .joinToString(separator = "\n")

        val additionalProperties = this.additionalProperties?:true
        val unknown = if (additionalProperties) ".unknown()" else ""
        return if (patternProperties.isNullOrEmpty())
            """ |Joi.lazy(() =\> Joi.object()${unknown}.keys({
                |   <$nonPatternProperties>
                |}))
            """.trimMargin()
        else {
            if (nonPatternProperties.isNotBlank())
                """
                |Joi.lazy(() =\> Joi.object().keys({
                |   <$nonPatternProperties>
                |}))
                |$patternProperties
            """.trimMargin()
            else
                "Joi.lazy(() =\\> Joi.object()<$patternProperties>)"
        }
    }

    fun Property.renderPropertyTypeSchema(): String {
        val owner: ObjectType = eContainer() as ObjectType
        val vrapType: VrapType = this.type.toVrapType()
        val constraint = if (this.required) "required()" else "optional()" +
                if (name == owner.discriminator()) ".only('${owner.discriminatorValue}')" else ""
        return "${name}: ${vrapType.renderTypeRef()}.${constraint}"
    }

    fun VrapType.renderTypeRef(): String {
        return when (this) {
            is VrapArrayType -> "Joi.array().items(${this.itemType.renderTypeRef()})"
            is VrapScalarType -> {
                val type: String = if (this.simpleTSName() == "date") "date().iso()" else "${this.simpleTSName()}()"
                return "Joi.$type"
            }
            else -> "${this.simpleJoiName()}()"
        }
    }

    fun StringType.renderJoiStringSchema(): String {
        val vrapType = this.toVrapType() as VrapEnumType

        return """
        |const ${vrapType.simpleJoiName()}Values = [
        |   <${this.enumFields()}>
        |]
        |
        |export function ${vrapType.simpleJoiName()}(): Joi.AnySchema {
        |   return Joi.string().only(${vrapType.simpleJoiName()}Values)
        |}
        """.trimMargin()
    }

    fun StringType.enumFields(): String = this.enumJsonNames()
        .map { "'$it'" }
        .joinToString(separator = ",\n")


    fun StringType.enumJsonNames() = this.enum?.filter { it is StringInstance }
        ?.map { it as StringInstance }
        ?.map { it.value }
        ?.filterNotNull() ?: listOf()

    fun Property.isPatternProperty() = this.name.startsWith("/") && this.name.endsWith("/")

    /**
     * in typescript optional properties should come after required ones
     */
    object PropertiesComparator : Comparator<Property> {
        override fun compare(o1: Property, o2: Property): Int {
            return if (o1.required && !o2.required) {
                -1
            } else if (!o1.required && o2.required) {
                +1
            } else if ((o1.type?.name ?: "").compareTo(o2.type?.name ?: "") != 0) {
                (o1.type?.name ?: "").compareTo(o2.type?.name ?: "")
            } else {
                o1.name.compareTo(o2.name)
            }
        }
    }
}
