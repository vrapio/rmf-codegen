package io.vrap.codegen.languages.extensions

import com.damnhandy.uri.template.Expression
import com.damnhandy.uri.template.UriTemplate
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.responses.Response
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.types.impl.TypesFactoryImpl
import io.vrap.rmf.raml.model.util.StringCaseFormat
import java.util.stream.Collectors

fun Method.toRequestName(): String {
    return this.resource().fullUri.toParamName("By") + this.method.toString().capitalize()
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

fun Method.resource(): Resource = this.eContainer() as Resource


fun Method.returnType(): AnyType {
    return this.responses
            .filter { it.isSuccessfull() }
            .filter { it.bodies?.isNotEmpty() ?: false }
            .firstOrNull()
            ?.let { it.bodies[0].type }
            ?: TypesFactoryImpl.eINSTANCE.createNilType()
}

fun Response.isSuccessfull(): Boolean = this.statusCode.toInt() in (200..299)


