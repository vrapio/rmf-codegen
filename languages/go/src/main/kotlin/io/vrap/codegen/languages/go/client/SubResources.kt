/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.client
import io.vrap.codegen.languages.extensions.getMethodName
import io.vrap.codegen.languages.go.*
import io.vrap.codegen.languages.go.model.exportName
import io.vrap.codegen.languages.go.model.goName
import io.vrap.rmf.raml.model.resources.ResourceContainer

fun ResourceContainer.subResources(structName: String): String {
    val pName = if (structName == "Client") "c" else "rb"

    return this.resources
        .map {
            var args = if (!it.relativeUri.variables.isNullOrEmpty()) {
                it.relativeUri.variables
                    .map { it.goName() }
                    .map { "$it string" }
                    .joinToString(separator = ", ")
            } else { "" }

            val assignments =
                it.relativeUri.variables
                    .map { it.goName() }
                    .map { "$it: $it," }
                    .plus(
                        (it.fullUri.variables.asList() - it.relativeUri.variables.asList())
                            .map { it.goName() }
                            .map { "$it: $pName.$it," }
                    )
                    .joinToString(separator = "\n")


            """
            |<${it.toDocString()}>
            |func ($pName *$structName) ${it.getMethodName().exportName()}($args) *${it.toStructName()} {
            |    return &${it.toStructName()}{
            |        <$assignments>
            |        client: ${if (structName == "Client") pName else "$pName.client"},
            |    }
            |}
             """.trimMargin()
        }.joinToString(separator = "\n")
}
