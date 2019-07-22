package io.vrap.codegen.languages.javalang.client.builder.producers

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.java.base.extensions.JavaObjectTypeExtensions
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

class JavaModelDraftBuilderFileProducer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider, private val allObjectTypes: MutableList<ObjectType>) : JavaObjectTypeExtensions, EObjectExtensions, FileProducer {

    override fun produceFiles(): List<TemplateFile> {
        return allObjectTypes.filter { !it.isAbstract() }.map { render(it) }
    }

    fun render(type: ObjectType): TemplateFile {

        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
        
        val content : String = """
            |package ${vrapType.`package`};
            |
            |${type.imports()}
            |import ${vrapType.`package`}.${vrapType.simpleClassName};
            |import javax.annotation.Nullable;
            |import java.util.List;
            |import java.util.Map;
            |import java.time.ZonedDateTime;
            |
            |public final class ${vrapType.simpleClassName}Builder {
            |   
            |   <${type.fields().escapeAll()}>
            |   
            |   <${type.assignments().escapeAll()}>
            |   
            |   <${type.getters().escapeAll()}>
            |
            |   public ${vrapType.simpleClassName} build() {
            |       <${type.buildMethodBody().escapeAll()}>
            |   }
            |   
            |   <${type.staticOfMethod().escapeAll()}>
            |   
            |   <${type.templateMethod()}>
            |   
            |}
        """.trimMargin().keepIndentation()
        
        return TemplateFile(
                relativePath = "${vrapType.`package`}.${vrapType.simpleClassName}Builder".replace(".", "/") + ".java",
                content = content
        )
    }
    
    private fun ObjectType.fields() = this.allProperties
            .map { it.toField() }
            .joinToString(separator = "\n\n")
    
    private fun Property.toField() : String {
        return if(this.isPatternProperty()){
            """
                |${if(!this.required) "@Nullable" else ""}
                |private Map<String, ${this.packageName()}${this.type.toVrapType().simpleName()}> values;
            """.escapeAll().trimMargin().keepIndentation()
        }else{
            """
            |${if(!this.required) "@Nullable" else ""}
            |private ${this.packageName()}${this.type.toVrapType().simpleName()} ${this.name};
        """.escapeAll().trimMargin().keepIndentation()
        }
    }
    
    private fun ObjectType.assignments() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        
        return this.allProperties.map { assignment(it, vrapType) }.joinToString(separator = "\n\n")
    }
    
    private fun assignment(property: Property, type: VrapObjectType) : String {
        return if(property.isPatternProperty()){
            """
                |public ${type.simpleClassName}Builder values(${if(!property.required) "@Nullable" else ""} final Map<String, ${property.packageName()}${property.type.toVrapType().simpleName()}> values){
                |   this.values = values;
                |   return this;
                |}
            """.escapeAll().trimMargin().keepIndentation()
        }else { 
            """
            |public ${type.simpleClassName}Builder ${property.name}(${if(!property.required) "@Nullable" else ""} final ${property.packageName()}${property.type.toVrapType().simpleName()} ${property.name}) {
            |   this.${property.name} = ${property.name};
            |   return this;
            |}
        """.escapeAll().trimMargin().keepIndentation()
        }
    }
    
    private fun ObjectType.getters() : String {
        return this.allProperties.map { it.getter() }.joinToString(separator = "\n\n")
    }
    
    private fun Property.getter() : String {
        
        return if(this.isPatternProperty()){
            """
                |${if(!this.required) "@Nullable" else ""}
                |public Map<String, ${this.packageName()}${this.type.toVrapType().simpleName()}> getValues(){
                |   return this.values;
                |}
            """.escapeAll().trimMargin().keepIndentation()
        }else{
            """
                |${if(!this.required) "@Nullable" else ""}
                |public ${this.packageName()}${this.type.toVrapType().simpleName()} get${this.name.capitalize()}(){
                |   return this.${this.name};
                |}
            """.escapeAll().trimMargin().keepIndentation()
        }
    }
    
    private fun ObjectType.buildMethodBody() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        val constructorArguments = this.allProperties
                .filter { it.name != this.discriminator() }
                .map { if(it.isPatternProperty()) {"values"} else {it.name} }
                .joinToString(separator = ", ")
        return "return new ${vrapType.simpleClassName}Impl($constructorArguments);"
    }
    
    private fun ObjectType.staticOfMethod() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        
        return """
            |public static ${vrapType.simpleClassName}Builder of() {
            |   return new ${vrapType.simpleClassName}Builder();
            |}
        """.trimMargin().keepIndentation()
    }
    
    private fun ObjectType.templateMethod(): String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType
        val fieldsAssignment : String = this.allProperties
                .filter {it.name != this.discriminator()}
                .map {
                    if(!it.isPatternProperty()){
                        "builder.${it.name} = template.get${it.name.upperCamelCase()}();"
                    }else{
                        "builder.values = template.values();"
                    }
                }
                .joinToString(separator = "\n")
        
        return """
            |public static ${vrapType.simpleClassName}Builder of(final ${vrapType.simpleClassName} template) {
            |   ${vrapType.simpleClassName}Builder builder = new ${vrapType.simpleClassName}Builder();
            |   <$fieldsAssignment>
            |   return builder;
            |}
        """.escapeAll().trimMargin().keepIndentation()
    }

    private fun Property.packageName() : String {
        return if(this.type is ObjectType) {
            "${(this.type.toVrapType() as VrapObjectType).`package`}."
        } else {
            ""
        }
    }
}