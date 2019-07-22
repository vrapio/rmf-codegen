package io.vrap.codegen.languages.javalang.client.builder.model

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.hasSubtypes
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.toComment
import io.vrap.codegen.languages.java.base.JavaSubTemplates
import io.vrap.codegen.languages.java.base.extensions.JavaObjectTypeExtensions
import io.vrap.codegen.languages.java.base.extensions.lowerCamelCase
import io.vrap.codegen.languages.java.base.extensions.simpleName
import io.vrap.codegen.languages.java.base.extensions.upperCamelCase
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
import org.eclipse.emf.ecore.EObject

class JavaModelInterfaceRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : JavaObjectTypeExtensions, EObjectExtensions, ObjectTypeRenderer {

    override fun render(type: ObjectType): TemplateFile {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        val content= """
            |package ${vrapType.`package`};
            |
            |${type.imports()}
            |${type.subclassImport()}
            |
            |import com.fasterxml.jackson.annotation.*;
            |import com.fasterxml.jackson.databind.annotation.*;
            |import javax.annotation.Generated;
            |import javax.validation.Valid;
            |import javax.validation.constraints.NotNull;
            |import java.util.List;
            |import java.util.Map;
            |import java.time.*;
            |
            |import json.CommercetoolsJsonUtils;
            |import java.io.IOException;
            |
            |<${type.toComment().escapeAll()}>
            |<${type.subTypesAnnotations()}>
            |<${JavaSubTemplates.generatedAnnotation}>
            |<${type.jsonDeserialize()}>
            |public interface ${vrapType.simpleClassName} ${type.type?.toVrapType()?.simpleName()?.let { "extends $it" } ?: ""} {
            |
            |   <${type.getters().escapeAll()}>
            |
            |   <${type.setters().escapeAll()}>
            |
            |   <${type.toJson()}>
            |   
            |   <${type.staticOfMethod()}>
            |   
            |   <${type.staticOfJsonMethod()}>
            |
            |}
        """.trimMargin().keepIndentation()
        
        return TemplateFile(
            relativePath = "${vrapType.`package`}.${vrapType.simpleClassName}".replace(".", "/") + ".java",
            content = content
        )
    }

    private fun ObjectType.subclassImport() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        return if(this.isAbstract()){
            ""
        }else {
            "import ${vrapType.`package`}.${vrapType.simpleClassName}Impl;"    
        }
    }
    
    private fun ObjectType.subTypesAnnotations(): String {
        return if (this.hasSubtypes())
            """
            |@JsonSubTypes({
            |   <${this.subTypes
                    .filter { (it as ObjectType).discriminatorValue != null }
                    .map {
                        val vrapType = vrapTypeProvider.doSwitch(it) as VrapObjectType
                        "@JsonSubTypes.Type(value = ${vrapType.`package`}.${vrapType.simpleClassName}Impl.class, name = \"${(it as ObjectType).discriminatorValue}\")" 
                    }
                    .joinToString(separator = ",\n")}>
            |})
            |@JsonTypeInfo(
            |   use = JsonTypeInfo.Id.NAME,
            |   include = JsonTypeInfo.As.PROPERTY,
            |   property = "${this.discriminator}"
            |)
            """.trimMargin()
        else
            ""
    }

    private fun ObjectType.staticOfMethod() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        return if(this.isAbstract()) {
            ""
        }else {
            """
                |public static ${vrapType.simpleClassName}Impl of(){
                |   return new ${vrapType.simpleClassName}Impl();
                |}
                |
             """.trimMargin()
        }
    }
    
    private fun ObjectType.staticOfJsonMethod() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        return if(this.isAbstract()) {
            ""
        }else {
            """
                |public static ${vrapType.simpleClassName}Impl of(final String json) {
                |   try{
                |       return CommercetoolsJsonUtils.fromJsonString(json, ${vrapType.simpleClassName}Impl.class);
                |   }catch (IOException e){
                |       e.printStackTrace();
                |   }
                |   return null;
                |}
             """.trimMargin()
        }
    }
    
    private fun ObjectType.getters() = this.properties
            .filter { it.name != this.discriminator() }
            .map { it.getter() }
            .joinToString(separator = "\n")

    private fun Property.getter(): String {
        return if(this.isPatternProperty()){
            """
            |${this.type.toComment()}
            |${this.validationAnnotations()}
            |@JsonAnyGetter
            |public Map<String, ${this.type.toVrapType().simpleName()}> values();
            """.trimMargin()
        }else {
            """
            |${this.type.toComment()}
            |${this.validationAnnotations()}
            |@JsonProperty("${this.name}")
            |public ${this.type.toVrapType().simpleName()} get${this.name.upperCamelCase()}();
        """.trimMargin()
        }
    }

    private fun ObjectType.setters() = this.properties
            .filter { it.name != this.discriminator() }
            .map { it.setter() }
            .joinToString(separator = "\n\n")

    private fun Property.setter(): String {
        return if (this.isPatternProperty()) {
            """
            |@JsonAnySetter
            |public void setValue(String key, ${this.type.toVrapType().simpleName()} value);
            """.trimMargin()
        } else {
            "public void set${this.name.upperCamelCase()}(final ${this.type.toVrapType().simpleName()} ${this.name.lowerCamelCase()});"
        }
    }

    private fun Property.validationAnnotations(): String {
        val validationAnnotations = ArrayList<String>()
        if (this.required != null && this.required!!) {
            validationAnnotations.add("@NotNull")
        }
        if (CascadeValidationCheck.doSwitch(this.type)) {
            validationAnnotations.add("@Valid")
        }
        return validationAnnotations.joinToString(separator = "\n")
    }
    
    
    private fun ObjectType.jsonDeserialize() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        return if(this.isAbstract()){
            ""
        }else{
            "@JsonDeserialize(as = ${vrapType.simpleClassName}Impl.class)"    
        }
    }
    
    private fun ObjectType.toJson() : String {
        return "public String toJson();"
    }
    
    private object CascadeValidationCheck : TypesSwitch<Boolean>() {
        override fun defaultCase(`object`: EObject?): Boolean? {
            return false
        }

        override fun caseObjectType(objectType: ObjectType?): Boolean? {
            return true
        }

        override fun caseArrayType(arrayType: ArrayType): Boolean? {
            return if (arrayType.items != null) {
                doSwitch(arrayType.items)
            } else {
                false
            }
        }
    }
}
