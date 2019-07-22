package io.vrap.codegen.languages.javalang.client.builder.requests

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.getMethodName
import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.toParamName
import io.vrap.codegen.languages.extensions.toRequestName
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ResourceRenderer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.resources.ResourceContainer

class JavaRequestBuilderResourceRenderer @Inject constructor(val api: Api, override val vrapTypeProvider: VrapTypeProvider) : ResourceRenderer, EObjectExtensions {

    override fun render(type: Resource): TemplateFile {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
        val resourceName : String = type.toResourceName()
        val className : String = "${resourceName}RequestBuilder"
        
        val content : String = """
            |package ${vrapType.`package`};
            |
            |public class $className {
            |   
            |   <${type.fields()}>
            |   
            |   <${type.constructor()}>
            |   
            |   <${type.methods()}>
            |   
            |   <${type.subResources()}>
            |}
            |
        """.trimMargin().keepIndentation()
        
        return TemplateFile(
                relativePath = "${vrapType.`package`}.$className".replace(".", "/") + ".java",
                content = content
        )
    }
    
    private fun Resource.fields() : String {
        return this.pathArguments().map { "private String $it;" }.joinToString(separator = "\n")
    }
    
    private fun Resource.constructor() : String {
        val resourceName : String = this.toResourceName()
        val className : String = "${resourceName}RequestBuilder"
        
        val constructorArguments : String = this.pathArguments().map { "String $it" }.joinToString(separator = ",")
        val constructorAssignment : String = this.pathArguments().map { "this.$it = $it;" }.joinToString(separator = "\n")
        return """
            |public $className ($constructorArguments) {
            |   $constructorAssignment
            |}
        """.trimMargin()
        
    }
    
    private fun Resource.toResourceName(): String {
        return this.fullUri.toParamName("By")
    }
    
    private fun Resource.methods() : String {
        return this.methods.map { it.method() }.joinToString(separator = "\n\n")
    }
    
    private fun Method.method() : String {
        return """
            |public ${this.toRequestName()} ${this.method.name.toLowerCase()}(${this.constructorArguments()}) {
            |   return new ${this.toRequestName()}(${this.requestArguments()});
            |}
        """.trimMargin()
    }

    private fun Method.constructorArguments(): String? {
        return if(this.bodies != null && this.bodies.isNotEmpty()){
            val methodBodyVrapType = this.bodies[0].type.toVrapType() as VrapObjectType
            val methodBodyArgument = "${methodBodyVrapType.`package`}.${methodBodyVrapType.simpleClassName} ${methodBodyVrapType.simpleClassName.decapitalize()}"
            methodBodyArgument
        }else {
            ""
        }
    }
    
    private fun Method.requestArguments() : String {
        val requestArguments = mutableListOf<String>()
        this.pathArguments().forEach { requestArguments.add(it) }

        if(this.bodies != null && this.bodies.isNotEmpty()){
            val methodBodyVrapType = this.bodies[0].type.toVrapType() as VrapObjectType
            requestArguments.add(methodBodyVrapType.simpleClassName.decapitalize())
        }

        return requestArguments.joinToString(separator = ", ")
    }

    private fun Method.pathArguments() : List<String> {
        val urlPathParts = this.resource().fullUri.template.split("/").filter { it.isNotEmpty() }
        return urlPathParts.filter { it.contains("{") && it.contains("}")}.map { it.substring(it.indexOf("{") + 1, it.indexOf("}")) }
    }
    
    private fun Resource.pathArguments(): List<String> {
        val urlPathParts = this.fullUri.template.split("/").filter { it.isNotEmpty() }
        return urlPathParts.filter { it.contains("{") && it.contains("}")}.map { it.substring(it.indexOf("{") + 1, it.indexOf("}")) }
    }
    
    private fun ResourceContainer.subResources() : String {
        return this.resources.map {
            val args = if (it.relativeUri.variables.isNullOrEmpty()){
                ""
            }else {
                it.relativeUri.variables.map { "String $it" }.joinToString(separator = " ,")
            }
            val subResourceArgs : String = it.pathArguments().joinToString(separator = ", ")
            """
            |public ${it.toResourceName()}RequestBuilder ${it.getMethodName()}($args) {
            |   return new ${it.toResourceName()}RequestBuilder($subResourceArgs);
            |}
        """.trimMargin() 
        }.joinToString(separator = "\n")
    }
}