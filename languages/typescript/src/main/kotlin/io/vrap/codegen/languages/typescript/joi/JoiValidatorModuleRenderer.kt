package io.vrap.codegen.languages.typescript.joi

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.discriminatorProperty
import io.vrap.codegen.languages.extensions.hasSubtypes
import io.vrap.codegen.languages.typescript.model.TsObjectTypeExtensions
import io.vrap.codegen.languages.typescript.model.simpleTSName
import io.vrap.codegen.languages.typescript.toJoiPackageName
import io.vrap.codegen.languages.typescript.tsGeneratedComment
import io.vrap.rmf.codegen.di.AllAnyTypes
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.*

class JoiValidatorModuleRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : JoiObjectTypeExtensions, TsObjectTypeExtensions, FileProducer {
    @Inject
    @AllAnyTypes
    lateinit var allAnyTypes: MutableList<AnyType>

    override fun produceFiles(): List<TemplateFile> {
        return allAnyTypes.groupBy { it.moduleName() }
                .filter { it.key.isNotBlank() }
                .map { entry -> buildModule(entry.key.toJoiPackageName(), entry.value) }
                .toList()
    }


    fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        val content = """
           |$tsGeneratedComment
           |
           |import * as Joi from 'joi'
           |${getImportsForJoiModule(moduleName, types)}
           |
           |const schema = {
           |  <${types.map { it.renderSchemaPlaceholder() }.joinToString(separator = ",\n")}>
           |}
           |
           |${types.filterNot { it is StringType && it.pattern != null }.map { it.renderJoiSchema() }.joinToString(separator = "\n\n")}
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
            |schema.${toVrapType().simpleJoiName()} = Joi.lazy(() =\> Joi.alternatives($arrayArg))
            |
        """
        return toVrapType().renderSchemaExportFunction(schemaDeclaration).trimMargin()
    }

    fun VrapType.renderSchemaExportFunction(schemaDeclaration: String): String {
        return """
            |export function ${simpleJoiName()}() : Joi.AnySchema {
            |   if (!schema.${simpleJoiName()}) {
            |     $schemaDeclaration
            |   }
            |   return schema.${simpleJoiName()}
            |}
        """.trimMargin()
    }

    fun ObjectType.renderJoiObjectSchema(): String {
        val schemaDeclaration : String
        if (this.hasSubtypes()) {
            val allSubsCases = this.subTypes
                .filterIsInstance(ObjectType::class.java)
                .filter { !it.discriminatorValue.isNullOrEmpty() }
                .map {
                    "when(Joi.object({ '${this.discriminator}': '${it.discriminatorValue}' }).unknown(), {then: ${it.toVrapType().simpleJoiName()}()} )"
                }.joinToString(separator = ".")

            schemaDeclaration = """schema.${toVrapType().simpleJoiName()} = Joi.lazy(() =\> Joi.alternatives().$allSubsCases)"""
        } else {
            schemaDeclaration =  """schema.${toVrapType().simpleJoiName()} = <${renderPropertySchemas()}>"""
        }
        return toVrapType().renderSchemaExportFunction(schemaDeclaration).trimIndent()
    }

    fun ObjectType.renderPropertySchemas(): String {
        val nonPatternProperties = this.allProperties
                .filter { !it.isPatternProperty() }
                .sortedWith(PropertiesComparator)
                .map { it.renderPropertyTypeSchema(this) }
                .joinToString(separator = ",\n")
        val patternProperties = this.allProperties
                .filter { it.isPatternProperty() }
                .map { ".pattern(${it.asRegExp()}, ${it.type.toVrapType().simpleJoiName()}())" }
                .joinToString(separator = "\n")

        val additionalProperties = this.additionalProperties?:true
        val unknown = if (additionalProperties) ".unknown()" else ""
        return if (patternProperties.isNullOrEmpty())
            """ |Joi.object()${unknown}.keys({
                |   <$nonPatternProperties>
                |})
            """.trimMargin()
        else {
            if (nonPatternProperties.isNotBlank())
                """
                |Joi.object().keys({
                |   <$nonPatternProperties>
                |})
                |$patternProperties
            """.trimMargin()
            else
                "Joi.object()<$patternProperties>"
        }
    }

    fun Property.renderPropertyTypeSchema(current : ObjectType): String {
        val vrapType: VrapType = this.type.toVrapType()
        val discriminatorConstraint = if (this == current.discriminatorProperty()) ".only('${current.discriminatorValue}')" else ""
        val requiredConstraint = if (this.required) ".required()" else ".optional()"
        val maxConstraint = if (this.type is ArrayType && (this.type as ArrayType).maxItems != null) ".max(${(this.type as ArrayType).maxItems})" else ""
        val typeRef = if (this == current.discriminatorProperty()) "Joi.string()" else vrapType.renderTypeRef()
        return "${name}: ${typeRef}${maxConstraint}${discriminatorConstraint}${requiredConstraint}"
    }

    fun VrapType.renderTypeRef(): String {
        return when (this) {
            is VrapArrayType -> {
                "Joi.array().items(${this.itemType.renderTypeRef()})"
            }
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

    fun Property.asRegExp() = if (this.name.equals("//")) "/.*/" else this.name

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
