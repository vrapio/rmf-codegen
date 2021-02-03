/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go

import io.vrap.codegen.languages.go.model.snakeCase
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.DescriptionFacet
import io.vrap.rmf.raml.model.util.StringCaseFormat

fun DescriptionFacet.toDocString(): String {
    val description = this.description
    return if (description?.value.isNullOrBlank()) {
        ""
    } else description.value
        .lines()
        .joinToString(prefix = "/**\n*\t", postfix = "\n*/", separator = "\n*\t")
}

fun DescriptionFacet.toLineComment(): String {
    val description = this.description
    return if (description?.value.isNullOrBlank()) {
        ""
    } else description.value
        .lines()
        .joinToString(separator = "\n")
        .trimMargin()
        .prependIndent("// ")
}

fun Resource.pyServiceModuleName(): String {
    val parts = fullUri.template.split("/")
    if (parts.size > 2) {
        return parts[2].snakeCase()
    } else {
        return ""
    }
}

fun Resource.pyServiceClassName(): String {
    val name = this.pyServiceModuleName()
    return StringCaseFormat.UPPER_CAMEL_CASE.apply(name) + "Service"
}
