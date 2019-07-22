package io.vrap.codegen.languages.typescript.client

import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.getMethodName
import io.vrap.codegen.languages.extensions.toResourceName

import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.resources.ResourceContainer

abstract class AbstractRequestBuilder constructor(
        val api: Api,
        override val vrapTypeProvider: VrapTypeProvider
) : EObjectExtensions {

    protected fun ResourceContainer.subResources(): String {
        return this.resources
                .map {

                    val args = if (it.relativeUri.variables.isNullOrEmpty()) "" else """|
                        |   childPathArgs: {
                        |       <${it.relativeUri.variables.map { "$it: string" }.joinToString(separator = "\n")}>
                        |   }
                        |
                    """.trimMargin()

                    """|
                    |${it.getMethodName()}($args): ${it.toRequestBuilderName()} {
                    |   return new ${it.toRequestBuilderName()}(
                    |         {
                    |            pathArgs: {
                    |               <${if(hasPathArgs()) "...this.args.pathArgs," else ""}>
                    |               <${if (it.relativeUri.variables.isNotEmpty()) "...childPathArgs" else ""}>
                    |            },
                    |            middlewares: this.args.middlewares
                    |         }
                    |   )
                    |}
                    |
                 """.trimMargin()
                }.joinToString(separator = "")
    }

    protected fun Resource.toRequestBuilderName(): String = "${this.toResourceName()}RequestBuilder"


    abstract fun hasPathArgs(): Boolean
}



