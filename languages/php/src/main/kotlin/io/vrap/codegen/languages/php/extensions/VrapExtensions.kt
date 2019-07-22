package io.vrap.codegen.languages.php.extensions

import com.damnhandy.uri.template.Expression
import com.damnhandy.uri.template.UriTemplate
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.responses.Body
import io.vrap.rmf.raml.model.security.OAuth20Settings
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.util.StringCaseFormat
import java.util.stream.Collectors

fun String.toNamespaceName():String{
    val `package` = this.split(".")
    return `package`.takeLast(maxOf(`package`.size - 1, 1)).map { s -> s.capitalize() }.joinToString("\\")
}

fun String.toNamespaceDir():String{
    val `package` = this.split(".")
    return `package`.takeLast(maxOf(`package`.size - 1, 1)).map { s -> s.capitalize() }.joinToString("/")
}

fun VrapType.namespaceName():String{
    return when(this){
        is VrapObjectType -> this.`package`.toNamespaceName()
        is VrapArrayType -> this.itemType.namespaceName()
        else -> throw IllegalStateException("$this has no simple class name.")
    }
}

fun VrapType.simpleName():String{
    return when(this){
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> "string"
        is VrapObjectType -> this.simpleClassName
        is VrapArrayType -> if (this.itemType.isScalar()) "array" else "${this.itemType.simpleName()}Collection"
        is VrapNilType -> throw IllegalStateException("$this has no simple class name.")
    }
}

fun VrapType.fullClassName():String{
    return when(this){
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> "string"
        is VrapObjectType -> "${this.namespaceName()}\\${this.simpleClassName}"
        is VrapArrayType -> "${this.itemType.fullClassName()}${if (!this.itemType.isScalar()) "Collection" else "[]"}"
        is VrapNilType -> throw IllegalStateException("$this has no full class name.")
    }
}

fun AnyType.isScalar(): Boolean {
    return when(this) {
        is StringType -> true
        is IntegerType -> true
        is NumberType -> true
        is BooleanType -> true
        is ArrayType -> this.items == null || this.items.isScalar()
        else -> false
    }
}

fun VrapType.isScalar(): Boolean {
    return when(this){
        is VrapObjectType -> when(this.simpleClassName) {
            in scalarTypes() -> true
            else -> false
        }
        is VrapArrayType -> when(this.itemType.simpleName()) {
            in scalarTypes() -> true
            else -> false
        }
        is VrapScalarType -> true
        is VrapEnumType -> true
        else -> false
    }
}


fun Api.authUri(): String {
    return this.getSecuritySchemes().stream()
            .filter { securityScheme -> securityScheme.getSettings() is OAuth20Settings }
            .map { securityScheme -> (securityScheme.getSettings() as OAuth20Settings).accessTokenUri }
            .findFirst().orElse("")
}

fun Method.resource(): Resource = this.eContainer() as Resource

fun Method.toRequestName(): String {
    return this.resource().fullUri.toParamName("By") + this.method.toString().capitalize()
}

fun Resource.toResourceName(): String {
    return this.fullUri.toParamName("By")
}

fun UriTemplate.toParamName(delimiter: String): String {
    return this.toParamName(delimiter, "")
}

fun UriTemplate.toParamName(delimiter: String, suffix: String): String {
    return this.components.stream().map { uriTemplatePart ->
        if (uriTemplatePart is Expression) {
            return@map uriTemplatePart.varSpecs.stream()
                    .map { s -> delimiter + s.variableName.capitalize() + suffix }.collect(Collectors.joining())
        }
        StringCaseFormat.UPPER_CAMEL_CASE.apply(uriTemplatePart.toString().replace("/", "-"))
    }.collect(Collectors.joining()).replace("[^\\p{L}\\p{Nd}]+".toRegex(), "").capitalize()
}

fun Method.allParams(): List<String>? {
    return this.resource().fullUri.components.stream()
            .filter { uriTemplatePart -> uriTemplatePart is Expression }
            .flatMap { uriTemplatePart -> (uriTemplatePart as Expression).varSpecs.stream().map { it.variableName } }
            .collect(Collectors.toList())
}

fun Method.firstBody(): Body? = this.bodies.stream().findFirst().orElse(null)

fun scalarTypes():Array<String> { return arrayOf("string", "int", "float", "bool", "array", "stdClass") }
