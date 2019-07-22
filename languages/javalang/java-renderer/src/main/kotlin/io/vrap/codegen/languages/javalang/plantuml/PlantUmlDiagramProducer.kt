package io.vrap.codegen.languages.javalang.plantuml

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.java.base.extensions.JavaObjectTypeExtensions
import io.vrap.codegen.languages.java.base.extensions.simpleName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.util.StringCaseFormat

class PlantUmlDiagramProducer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : JavaObjectTypeExtensions, EObjectExtensions, FileProducer {

    @Inject
    lateinit var allObjectTypes: MutableList<ObjectType>

    @Inject
    lateinit var allStringTypes: MutableList<StringType>


    override fun produceFiles(): List<TemplateFile> = listOf(
            TemplateFile(relativePath = "diagram.puml",
                    content = """
                    |@startuml
                    |
                    |${allStringTypes.map { it.plantUmlEnumDef() }.joinToString(separator = "\n")}
                    |
                    |${allObjectTypes.map { it.plantUmlClassDef() }.joinToString(separator = "\n")}
                    |
                    |${allObjectTypes.map { it.modelInheritenceRelation() }.filter { it.isNotBlank() }.joinToString(separator = "\n\n")}
                    |
                    |${allObjectTypes.map { it.modelCompositionRelations() }.filter { it.isNotBlank() }.joinToString(separator = "\n\n")}
                    |
                    |@enduml
                    """.trimMargin()
            ))


    fun ObjectType.modelCompositionRelations(): String {

        val vrapType = this.toVrapType()
        return this.properties
                .map {
                    if(it.type is ObjectType){
                        "${vrapType.simpleName()} \"1\" ${PlantUmlRelations.COMPOSITION} \"${it.name}\" ${it.type.toVrapType().simpleName()}"
                    }else if( (it.type is ArrayType) && ((it.type as ArrayType).items is ObjectType) ){
                        "${vrapType.simpleName()} \"n\" ${PlantUmlRelations.COMPOSITION} \"${it.name}\" ${(it.type as ArrayType).items.toVrapType().simpleName()}"
                    }else{
                        ""
                    }
                }
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n\n")
    }

    fun ObjectType.modelInheritenceRelation(): String {

        return this.subTypes
                .filter { it.name != this.name }
                .map {
            """|
                |${this.toVrapType().simpleName()} ${PlantUmlRelations.INHERITS} ${it.toVrapType().simpleName()}
            """.trimMargin()
        }.joinToString(separator = "\n\n")

    }


    fun ObjectType.plantUmlClassDef(): String {
        val vrapType = this.toVrapType() as VrapObjectType

        return """
            |package ${vrapType.`package`}{
            |   class ${vrapType.simpleClassName} {
            |       <${this.plantUmlProperties().escapeAll()}>
            |   }
            |}
        """.trimMargin().keepIndentation()

    }

    fun ObjectType.plantUmlProperties(): String {
        return this.properties
                .filter { (it.type !is ObjectType) && !((it.type is ArrayType) && ((it.type as ArrayType).items is ObjectType )) }
                .map { it.plantUmlAttribute() }
                .joinToString(separator = "\n")
    }

    fun Property.plantUmlAttribute(): String {

        val vrapType = this.type.toVrapType()

        return if (this.isPatternProperty()) {
            "Map<String,${this.type.toVrapType().simpleName()}> values"
        } else {
            "${vrapType.simpleName()} ${this.name}"
        }

    }


    fun StringType.plantUmlEnumDef(): String {
        val vrapType = this.toVrapType() as VrapEnumType
        return """
            |package ${vrapType.`package`} {
            |   enum ${vrapType.simpleClassName}{
            |       <${this.enumFields()}>
            |   }
            |}
        """.trimMargin().keepIndentation()
    }

    fun StringType.enumFields(): String = this.enumJsonNames()
            .map {
                """
                |${it.enumValueName()}
            """.trimMargin()
            }
            .joinToString(separator = "\n", postfix = "")


    fun StringType.enumJsonNames() = this.enum?.filter { it is StringInstance }
            ?.map { it as StringInstance }
            ?.map { it.value }
            ?.filterNotNull() ?: listOf()

    fun String.enumValueName(): String {
        return StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this)
    }

    fun Property.isPatternProperty() = this.name.startsWith("/") && this.name.endsWith("/")

}
