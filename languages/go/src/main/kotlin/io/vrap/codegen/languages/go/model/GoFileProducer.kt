/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.model

import io.vrap.codegen.languages.extensions.getSuperTypes
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.sortedByTopology
import io.vrap.codegen.languages.go.pyGeneratedComment
import io.vrap.codegen.languages.go.toDocString
import io.vrap.codegen.languages.go.toLineComment
import io.vrap.rmf.codegen.di.AllAnyTypes
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.*

class GoFileProducer constructor(
    override val vrapTypeProvider: VrapTypeProvider,
    @AllAnyTypes val allAnyTypes: List<AnyType>,
    @BasePackageName val basePackageName: String
) : PyObjectTypeExtensions, FileProducer {

    override fun produceFiles(): List<TemplateFile> {
        return allAnyTypes.filter { it is ObjectType || (it is StringType && it.pattern == null) }
            .groupBy {
                it.moduleName()
            }
            .map { entry: Map.Entry<String, List<AnyType>> ->
                buildModule(entry.key, entry.value)
            }
            .toList()
    }

    private fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        var sortedTypes = types.sortedByTopology(AnyType::getSuperTypes)
        val importExpr = getImports(sortedTypes)
            .map {
                "    \"$it\""
            }
            .joinToString(
                prefix = "import(\n", separator = "\n", postfix = "\n)"
            )

        val content = """
           |$pyGeneratedComment
           |package $basePackageName
           |
           |<$importExpr>
           |
           |${sortedTypes.map { it.renderAnyType() }.joinToString(separator = "\n")}
       """.trimMargin().keepIndentation()

        var filename = moduleName.goModelFileName()
        return TemplateFile(content, filename + ".go")
    }

    private fun AnyType.renderAnyType(): String {
        return when (this) {
            is ObjectType -> this.renderObjectType()
            is StringType -> this.renderStringType()
            else -> throw IllegalArgumentException("unhandled case ${this.javaClass}")
        }
    }

    private fun getImports(types: List<AnyType>): List<String> {
        val objects = types.filterIsInstance<ObjectType>()

        // return listOf<String>("encoding/json", "errors", "time")
        return objects
            .flatMap {
                it.allProperties.map {
                    when (it.type) {
                        is DateTimeType, is TimeOnlyType -> "time"
                        is FileType -> "io"
                        else -> null
                    }
                }
            }
            .plus(
                objects
                    .map {
                        if (it.discriminatorValue != null) {
                            "encoding/json"
                        } else {
                            null
                        }
                    }
            )
            .plus(
                objects
                    .map {
                        if (it.isDiscriminated()) {
                            "errors"
                        } else {
                            null
                        }
                    }
            )
            .plus(
                objects
                    .flatMap {
                        it.allProperties
                            .map {
                                it.type
                            }
                            .filterIsInstance<ObjectType>()
                            .map {
                                if (it.discriminatorValue != null) {
                                    "encoding/json"
                                } else {
                                    null
                                }
                            }
                    }
            )
            .filterNotNull()
            .distinct()
    }

    private fun ObjectType.renderObjectType(): String {

        val isDict = allProperties.all { it.isPatternProperty() }
        if (isDict) {
            val valueType = allProperties[0].type.renderTypeExpr()

            return """
            |// $name is something
            |type $name map[string]$valueType
            """.trimMargin()
        } else if (this.isDiscriminated()) {
            val interfaceExpr = """
                |<${toDocString().escapeAll()}>
                |type $name interface{}
            """.trimMargin()

            val discriminatorMapFunc = if (this.isDiscriminated()) this.renderDiscriminatorMapper() else ""

            return """
            |$interfaceExpr
            |
            |$discriminatorMapFunc
            """.trimMargin()
        } else {
            val structField = """
                |<${toDocString().escapeAll()}>
                |type $name struct {
                |    <${renderStructFields()}>
                |}
            """.trimMargin()

            val unmarshalFunc = if (
                allProperties
                    .map { it.type }
                    .any {
                        when (it) {
                            is ArrayType -> it.items.isDiscriminated()
                            is ObjectType -> it.isDiscriminated()
                            else -> false
                        }
                    }
            ) this.renderUnmarshalFunc() else ""

            val marshalFunc = if (this.discriminatorValue != null) this.renderMarshalFunc() else ""
            val errorFunc = this.renderErrorFunc()

            return """
            |$structField
            |
            |$unmarshalFunc
            |$marshalFunc
            |$errorFunc
            """.trimMargin()
        }
    }

    fun ObjectType.renderErrorFunc(): String {
        if (!this.isErrorObject()) return ""

        val messageField = PyClassProperties(true)
            .filter{
                it.type is StringType && it.name.toLowerCase() == "message"
            }
            .firstOrNull()

        // Shouldn't be possible
        if (messageField == null) return ""

        return """
        |func (obj ${name}) Error() string {
        |    return obj.${messageField.name.exportName()}
        |}
        """.trimMargin()
    }


    fun ObjectType.renderMarshalFunc(): String {
        return """
        |// MarshalJSON override to set the discriminator value
        |func (obj $name) MarshalJSON() ([]byte, error) {
        |    type Alias $name
        |    return json.Marshal(struct {
        |        Action string `json:"${this.discriminator()}"`
        |        *Alias
        |    }{Action: "$discriminatorValue", Alias: (*Alias)(&obj)})
        |}
        """.trimMargin()
    }

    fun ObjectType.renderUnmarshalFunc(): String {
        val fieldHandlers = allProperties
            .filter {
                it.type.isDiscriminated()
            }
            .map {
                val attrName = it.name.exportName()
                val typeName = it.type.name.exportName()
                when (it.type) {
                    is ArrayType -> {
                        """
                    |for i := range obj.$attrName {
                    |    var err error
                    |    obj.$attrName[i], err = mapDiscriminator$typeName(obj.$attrName[i])
                    |    if err != nil {
                    |        return err
                    |    }
                    |}
                    """.trimMargin()
                    }
                    is ObjectType -> {
                        """
                    |if obj.$attrName != nil {
                    |    var err error
                    |    obj.$attrName, err = mapDiscriminator$typeName(obj.$attrName)
                    |    if err != nil {
                    |        return err
                    |    }
                    |}
                    """.trimMargin()
                    }
                    else -> ""
                }
            }
            .joinToString("\n")

        return """
        |// UnmarshalJSON override to deserialize correct attribute types based
        |// on the discriminator value
        |func (obj *${name.exportName()}) UnmarshalJSON(data []byte) error {
        |    type Alias ${name.exportName()}
        |    if err := json.Unmarshal(data, (*Alias)(obj)); err != nil {
        |        return err
        |    }
        |    $fieldHandlers
        |    return nil
        |}
        """.trimMargin()
    }

    fun ObjectType.renderDiscriminatorMapper(): String {
        val caseStatements = allAnyTypes.getTypeInheritance(this)
            .filterIsInstance<ObjectType>()
            .filter { !it.discriminatorValue.isNullOrEmpty() }
            .map {
                """
            |   case "${it.discriminatorValue}":
            |       new := ${it.name}{}
            |       err := decodeStruct(input, &new)
            |       if err != nil {
            |           return nil, err
            |       }
            |       return new, nil
            """.trimMargin()
            }
            .joinToString("\n")

        return """
            |func mapDiscriminator$name(input interface{}) ($name, error) {
            |
            |    var discriminator string
            |    if data, ok := input.(map[string]interface{}); ok {
            |        discriminator, ok = data["${this.discriminator()}"].(string)
            |        if !ok {
            |            return nil, errors.New("Error processing discriminator field '${this.discriminator()}'")
            |        }
            |    } else {
            |        return nil, errors.New("Invalid data")
            |    }
            |
            |    switch discriminator {
            |    $caseStatements
            |    }
            |    return nil, nil
            |}
        """.trimMargin()
    }

    // Renders the attribute of this model as type annotations.
    fun ObjectType.renderStructFields(): String {
        return PyClassProperties(true)
            .map {
                val comment: String = it.type.toLineComment().escapeAll()
                var name = it.name

                // Make sure we don't have an Error field and Method
                if (name.toLowerCase() == "error" && this.isErrorObject()) {
                    name = "${name}Message"
                }

                if (it.required) {
                    """
                    |<$comment>
                    |${name.exportName()} ${it.type.renderTypeExpr()} `json:"${it.name}"`
                    """.trimMargin()
                } else {
                    val type = it.type
                    val pointer = if (type is ObjectType && type.discriminator() == null) "*" else ""
                    """
                    |<$comment>
                    |${name.exportName()} ${pointer}${type.renderTypeExpr()} `json:"${it.name},omitEmpty"`
                    """.trimMargin()
                }
            }
            .joinToString("\n")
    }

    fun AnyType.renderTypeExpr(): String {
        return when (this) {
            is UnionType -> {
                // Go has no concept of unions so we look for the shared common
                // type. If none found we should use interface{}
                // TODO
                if (oneOf[0].type != null)
                    return oneOf[0].type.renderTypeExpr()
                return oneOf[0].renderTypeExpr()
            }
            is IntersectionType -> allOf.map { it.renderTypeExpr() }.joinToString(" & ")
            is NilType -> "None"
            else -> toVrapType().pyTypeName()
        }
    }

    private fun StringType.renderStringType(): String {
        val vrapType = this.toVrapType() as VrapEnumType

        return """
        |<${toDocString().escapeAll()}>
        |type ${vrapType.simpleClassName} string
        |
        |<${this.renderEnumValues(vrapType.simpleClassName)}>
        |
        """.trimMargin()
    }

    private fun StringType.renderEnumValues(enumName: String): String {
        return this.enumValues()
            .map { "${enumName}${it.exportName()} $enumName = \"$it\"" }
            .joinToString(prefix = "const (", separator = "\n", postfix = ")")
    }

    private fun StringType.enumValues() = enum?.filter { it is StringInstance }
        ?.map { (it as StringInstance).value }
        ?.filterNotNull() ?: listOf()
}
