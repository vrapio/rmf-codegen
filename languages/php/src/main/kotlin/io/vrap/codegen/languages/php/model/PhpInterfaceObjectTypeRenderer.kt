package io.vrap.codegen.languages.php.model;

import com.google.inject.Inject
import com.google.inject.name.Named
import io.vrap.codegen.languages.php.PhpSubTemplates
import io.vrap.codegen.languages.php.extensions.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.ArrayType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property
import io.vrap.rmf.raml.model.types.util.TypesSwitch
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.eclipse.emf.ecore.EObject
import java.util.*

class PhpInterfaceObjectTypeRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : ObjectTypeExtensions, EObjectTypeExtensions, ObjectTypeRenderer {

    @Inject
    @BasePackageName
    lateinit var packagePrefix:String

    override fun render(type: ObjectType): TemplateFile {

        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        val content = """
            |<?php
            |${PhpSubTemplates.generatorInfo}
            |namespace ${vrapType.namespaceName().escapeAll()};
            |
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Base\\JsonObject;
            |<<${type.imports()}>>
            |
            |interface ${vrapType.simpleClassName} ${type.type?.toVrapType()?.simpleName()?.let { "extends $it" } ?: ""}
            |{
            |    ${if (type.discriminator != null) {"const DISCRIMINATOR_FIELD = '${type.discriminator}';"} else ""}
            |    <<${type.toBeanConstant()}>>
            |
            |    <<${type.getters()}>>
            |}
        """.trimMargin().keepIndentation("<<", ">>").forcedLiteralEscape()


        return TemplateFile(
                relativePath = "src/" + vrapType.fullClassName().replace(packagePrefix.toNamespaceName(), "").replace("\\", "/") + ".php",
                content = content
        )
    }

    fun ObjectType.imports() = this.getImports().map { "use ${it.escapeAll()};" }.joinToString(separator = "\n")

    fun Property.toPhpConstant(): String {

        return """
            |const FIELD_${if (this.isPatternProperty()) "VALUES" else StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this.name)} = '${if (this.isPatternProperty()) "values" else this.name}';
        """.trimMargin();
    }

    fun ObjectType.toBeanConstant(): String {
        val superTypeAllProperties = when(this.type) {
            is ObjectType -> (this.type as ObjectType).allProperties
            else -> emptyList<Property>()
        };
        return this.properties
                .asSequence()
                .filter { it -> superTypeAllProperties.none { property -> it.name == property.name } }
                .map { it.toPhpConstant() }.joinToString(separator = "\n")
    }

    fun ObjectType.setters() = this.properties
            //Filter the discriminators because they don't make much sense the generated bean
            .filter { it.name != this.discriminator }
            .map { it.setter() }
            .joinToString(separator = "\n\n")


    fun ObjectType.getters() = this.properties
            //Filter the discriminators because they don't make much sense the generated bean
//            .filter { it.name != this.discriminator }
            .map { it.getter() }
            .joinToString(separator = "\n\n")

    fun Property.isPatternProperty() = this.name.startsWith("/") && this.name.endsWith("/")

    fun Property.setter(): String {
        return if (this.isPatternProperty()) {

            """
        |@JsonAnySetter
        |public void setValue(String key, ${this.type.toVrapType().simpleName()} value) {
        |    if (values == null) {
        |        values = new HashMap<>();
        |    }
        |    values.put(key, value);
        |}
        """.trimMargin()
        } else {
            """
        |public void set${this.name.capitalize()}(final ${this.type.toVrapType().simpleName()} ${this.name}){
        |   this.${this.name} = ${this.name};
        |}
        """.trimMargin()
        }
    }

    fun Property.getter(): String {
        return if (this.isPatternProperty()) {

            """
            |/**
            | ${this.type.toPhpComment()}
            | */
            |public function values();
        """.trimMargin()
        } else {
            """
            |/**
            | ${this.type.toPhpComment()}
            | * @return ?${if (this.type.toVrapType().simpleName() != "stdClass") this.type.toVrapType().simpleName() else "JsonObject" }
            | */
            |public function get${this.name.capitalize()}();
    """.trimMargin()
        }
    }
}
