package io.vrap.codegen.languages.java.model;

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.*
import io.vrap.codegen.languages.java.JavaSubTemplates
import io.vrap.codegen.languages.java.extensions.JavaObjectTypeExtensions
import io.vrap.codegen.languages.java.extensions.enumValueName
import io.vrap.codegen.languages.java.extensions.simpleName
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
import java.util.*

class JavaObjectTypeRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : JavaObjectTypeExtensions, EObjectExtensions, ObjectTypeRenderer {

    override fun render(type: ObjectType): TemplateFile {

        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        val content = """
                |package ${vrapType.`package`};
                |
                |${type.imports()}
                |import com.fasterxml.jackson.annotation.*;
                |import javax.annotation.Generated;
                |import javax.validation.Valid;
                |import javax.validation.constraints.NotNull;
                |import java.util.*;
                |import org.apache.commons.lang3.builder.EqualsBuilder;
                |import org.apache.commons.lang3.builder.HashCodeBuilder;
                |import org.apache.commons.lang3.builder.ToStringBuilder;
                |import org.apache.commons.lang3.builder.ToStringStyle;
                |
                |<${type.toComment().escapeAll()}>
                |<${type.subTypesAnnotations()}>
                |<${JavaSubTemplates.generatedAnnotation}>
                |public class ${vrapType.simpleClassName} ${type.type?.toVrapType()?.simpleName()?.let { "extends $it" } ?: ""}{
                |
                |    <${type.toBeanFields().escapeAll()}>
                |
                |    <${type.renderConstructor()}>
                |
                |    <${type.setters().escapeAll()}>
                |
                |    <${type.getters().escapeAll()}>
                |
                |    @Override
                |    public String toString() {
                |        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
                |    }
                |
                |    @Override
                |    public boolean equals(Object o) {
                |        return EqualsBuilder.reflectionEquals(this, o);
                |    }
                |
                |    @Override
                |    public int hashCode() {
                |        return HashCodeBuilder.reflectionHashCode(this);
                |    }
                |}
        """.trimMargin().keepIndentation()


        return TemplateFile(
                relativePath = "${vrapType.`package`}.${vrapType.simpleClassName}".replace(".", "/") + ".java",
                content = content
        )
    }

    fun ObjectType.renderConstructor() : String {
        val constructorParams = constructorParams()
        return if (discriminator() != null) {
            if (discriminatorValue == null) {
            """
            |public ${toVrapType().simpleName()}(${constructorParams}) {
            |  ${constructorBody()}
            |}
            |
            |public ${toVrapType().simpleName()}() {
            |  this(null); // workaround needed for our groovy dsl
            |}
            """
            } else {
            """
            |public ${toVrapType().simpleName()}() {
            |  ${constructorBody()}
            |}
            """
            }
        } else {
            ""
        }
    }

    fun ObjectType.constructorParams() : String {
        return if (discriminator() != null && discriminatorValue == null) {
            "final ${discriminatorProperty()?.type?.toVrapType()?.simpleName()} ${discriminator()}"
        } else {
            ""
        }
    }


    fun ObjectType.constructorBody() : String {
        if (discriminator() != null && discriminatorValue == null) {
            return "this.${discriminator()} = ${discriminator()};"
        } else {
            if (discriminatorValue != null) {
                return """super(${renderDiscriminatorValue()});"""
            }
        }
        return ""
    }

    fun ObjectType.renderDiscriminatorValue() : String {
        val discriminatorProperty = discriminatorProperty()
        if (discriminatorProperty?.type?.enum?.size?:0 > 0) {
            return "${discriminatorProperty?.type?.toVrapType()?.simpleName()}.${discriminatorValue.enumValueName()}"
        } else {
            return """"${discriminatorValue}""""
        }
    }

    fun ObjectType.subTypesAnnotations(): String {
        return if (hasSubtypes())
            """
            |@JsonSubTypes({
            |   <${namedSubTypes().map { "@JsonSubTypes.Type(value = ${it.toVrapType().simpleName()}.class, name = \"${(it as ObjectType).discriminatorValue}\")" }.joinToString(separator = ",\n")}>
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

    fun Property.toJavaField(): String {

        return if (this.isPatternProperty()) {
            "private Map<String,${this.type.toVrapType().simpleName()}> values;"
        } else {
            "private ${this.type.toVrapType().simpleName()} ${if (this.isPatternProperty()) "values" else this.name};"
        }

    }

    fun ObjectType.toBeanFields() = this.properties
            .map { it.toJavaField() }.joinToString(separator = "\n\n")

    fun ObjectType.setters() = this.properties
            //Filter the discriminators because they don't make much sense the generated bean
            .filter { it.name != this.discriminator }
            .map { it.setter() }
            .joinToString(separator = "\n\n")


    fun ObjectType.getters() = this.properties
            //Filter the discriminators because they don't make much sense the generated bean
            .map { it.getter() }
            .joinToString(separator = "\n\n")

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
            |${this.validationAnnotations()}
            |${this.type.toComment()}
            |@JsonAnyGetter
            |public Map<String, ${this.type.toVrapType().simpleName()}> values() {
            |    return values;
            |}
            """.trimMargin()
        } else {
            """
            |${this.type.toComment()}
            |${this.validationAnnotations()}
            |@JsonProperty("${this.name}")
            |public ${this.type.toVrapType().simpleName()} get${this.name.capitalize()}(){
            |   return this.${this.name};
            |}
        """.trimMargin()
        }
    }

    fun Property.validationAnnotations(): String {
        val validationAnnotations = ArrayList<String>()
        if (this.required != null && this.required!!) {
            validationAnnotations.add("@NotNull")
        }
        if (CascadeValidationCheck.doSwitch(this.type)) {
            validationAnnotations.add("@Valid")
        }
        return validationAnnotations.joinToString(separator = "\n")
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
