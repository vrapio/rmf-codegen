/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.client

import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.toResourceName
import io.vrap.codegen.languages.go.*
import io.vrap.codegen.languages.go.model.exportName
import io.vrap.codegen.languages.go.model.snakeCase
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.resources.Resource

fun Resource.toRequestBuilderName(): String = "${this.toResourceName()}RequestBuilder"

fun Resource.toStructName(): String {
    return this.toRequestBuilderName().exportName()
}

fun Method.toStructName(): String {
    return "${this.resource().toResourceName()}RequestMethod${this.methodName.exportName()}".exportName()
}

fun Resource.goClientFileName(): String {
    return listOf<String>(
        "client",
        resourcePathName.snakeCase(),
        this.toResourceName().snakeCase()

    ).joinToString(separator = "_")
}

fun Method.goClientFileName(): String {
    return listOf<String>(
        "client",
        resource().resourcePathName.snakeCase(),
        "${this.resource().toResourceName()}${this.methodName.exportName()}".snakeCase()

    ).joinToString(separator = "_")
}

fun Method.bodyType(): AnyType? {
    if (bodies.isNotEmpty()) {
        return bodies[0].type
    }
    return null
}
