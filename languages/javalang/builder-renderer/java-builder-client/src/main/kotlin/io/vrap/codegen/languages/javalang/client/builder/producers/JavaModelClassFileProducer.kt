package io.vrap.codegen.languages.javalang.client.builder.producers

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.toComment
import io.vrap.codegen.languages.java.base.JavaSubTemplates
import io.vrap.codegen.languages.java.base.extensions.JavaObjectTypeExtensions
import io.vrap.codegen.languages.java.base.extensions.lowerCamelCase
import io.vrap.codegen.languages.java.base.extensions.simpleName
import io.vrap.codegen.languages.java.base.extensions.upperCamelCase
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property


class JavaModelClassFileProducer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider, private val allObjectTypes: MutableList<ObjectType>) : JavaObjectTypeExtensions, EObjectExtensions, FileProducer {
    
    override fun produceFiles(): List<TemplateFile> {
        return allObjectTypes.filter { !it.isAbstract() }.map { render(it) }
    }

    fun render(type: ObjectType): TemplateFile {

        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
        
        val content = """
                |package ${vrapType.`package`};
                |
                |${type.imports()}
                |import javax.annotation.Generated;
                |import javax.validation.Valid;
                |import javax.validation.constraints.NotNull;
                |import java.util.*;
                |import java.time.*;
                |import java.util.List;
                |import java.util.Map;
                |
                |import com.fasterxml.jackson.core.JsonProcessingException;
                |import com.fasterxml.jackson.databind.ObjectMapper;
                |import com.fasterxml.jackson.annotation.JsonInclude;
                |import com.fasterxml.jackson.annotation.JsonCreator;
                |import com.fasterxml.jackson.annotation.JsonProperty;
                |
                |import json.CommercetoolsJsonUtils;
                |
                |<${type.toComment().escapeAll()}>
                |<${JavaSubTemplates.generatedAnnotation}>
                |public class ${vrapType.simpleClassName}Impl implements ${vrapType.simpleClassName} {
                |
                |   <${type.beanFields().escapeAll()}>
                |
                |   <${type.constructors().escapeAll()}>
                |   
                |   <${type.getters().escapeAll()}>
                |
                |   <${type.setters().escapeAll()}>
                |   
                |   <${type.toJson()}>
                |
                |}
        """.trimMargin().keepIndentation()

        return TemplateFile(
                relativePath = "${vrapType.`package`}.${vrapType.simpleClassName}Impl".replace(".", "/") + ".java",
                content = content
        )
    }
    
    private fun Property.packageName() : String {
        return if(this.type is ObjectType) {
            "${(this.type.toVrapType() as VrapObjectType).`package`}."
        } else {
            ""
        }
    }
    
    private fun Property.toJavaField(): String {
        return if (this.isPatternProperty()) {
            "private Map<String, ${this.packageName()}${this.type.toVrapType().simpleName()}> values;"
        } else {
            "private ${this.packageName()}${this.type.toVrapType().simpleName()} ${if (this.isPatternProperty()) "values" else this.name.lowerCamelCase()};"
        }
    }
    
    private fun ObjectType.beanFields() = this.allProperties
            .filter { it.name != this.discriminator() }
            .map { it.toJavaField() }.joinToString(separator = "\n\n")

    private fun ObjectType.setters() = this.allProperties
            .filter { it.name != this.discriminator() }
            .map { it.setter() }
            .joinToString(separator = "\n\n")

    private fun ObjectType.getters() = this.allProperties
            .filter { it.name != this.discriminator() }
            .map { it.getter() }
            .joinToString(separator = "\n\n")

    private fun Property.setter(): String {
        return if (this.isPatternProperty()) {
            """
            |public void setValue(String key, ${this.packageName()}${this.type.toVrapType().simpleName()} value) {
            |    if (values == null) {
            |        values = new HashMap<>();
            |    }
            |    values.put(key, value);
            |}
            """.trimMargin()
        } else {
            """
            |public void set${this.name.upperCamelCase()}(final ${this.packageName()}${this.type.toVrapType().simpleName()} ${this.name.lowerCamelCase()}){
            |   this.${this.name.lowerCamelCase()} = ${this.name.lowerCamelCase()};
            |}
            """.trimMargin()
        }
    }

    private fun Property.getter(): String {
        return if (this.isPatternProperty()) {
            """
            |${this.type.toComment()}
            |public Map<String, ${this.packageName()}${this.type.toVrapType().simpleName()}> values() {
            |    return values;
            |}
            """.trimMargin()
        } else {
            """
            |${this.type.toComment()}
            |public ${this.packageName()}${this.type.toVrapType().simpleName()} get${this.name.upperCamelCase()}(){
            |   return this.${this.name.lowerCamelCase()};
            |}
        """.trimMargin()
        }
    }
    
    private fun ObjectType.constructors(): String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        val constructorArguments = this.allProperties
                .filter { it.name != this.discriminator() }
                .map { if(it.isPatternProperty()) "@JsonProperty(\"values\") final Map<String, ${it.packageName()}${it.type.toVrapType().simpleName()}> values" else "@JsonProperty(\"${it.name.lowerCamelCase()}\") final ${it.packageName()}${it.type.toVrapType().simpleName()} ${it.name.lowerCamelCase()}" }
                .joinToString(separator = ", ")

        val propertiesAssignment : String = this.allProperties
                .filter { it.name != this.discriminator() }
                .map { if(it.isPatternProperty()) "this.values = values;" else "this.${it.name.lowerCamelCase()} = ${it.name.lowerCamelCase()};" }
                .joinToString(separator = "\n")

        val emptyConstructor : String = """
            |public ${vrapType.simpleClassName}Impl() {
            |   
            |}
        """.trimMargin()
        
        return """
            |@JsonCreator
            |${vrapType.simpleClassName}Impl(${constructorArguments.escapeAll()}) {
            |   <$propertiesAssignment>
            |}
            |${if(constructorArguments.isEmpty()) "" else emptyConstructor }
        """.trimMargin().keepIndentation()
    }
    
    private fun ObjectType.toJson() : String {
        return """
            |public String toJson() {
            |   try {
            |       return CommercetoolsJsonUtils.toJsonString(this);
            |   }catch(JsonProcessingException e) {
            |       e.printStackTrace();
            |   }
            |   return null;
            |}
        """.trimMargin()
    }
}