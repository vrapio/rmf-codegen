package io.vrap.codegen.languages.typescript.client

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.returnType
import io.vrap.codegen.languages.typescript.client.files_producers.apiRequest
import io.vrap.codegen.languages.typescript.client.files_producers.middleware
import io.vrap.codegen.languages.typescript.model.simpleTSName
import io.vrap.codegen.languages.typescript.relativizePaths
import io.vrap.codegen.languages.typescript.tsMediaType
import io.vrap.codegen.languages.typescript.tsRemoveRegexp
import io.vrap.codegen.languages.typescript.tsRequestModuleName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ResourceRenderer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapLibraryType
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.StringType


class RequestBuilder @Inject constructor(
        api:Api,
        vrapTypeProvider: VrapTypeProvider
) : ResourceRenderer, AbstractRequestBuilder(api,vrapTypeProvider) {

    override fun render(type: Resource): TemplateFile {

        val pakage = (type.toVrapType() as VrapObjectType).`package`

        return TemplateFile(
                relativePath = type.tsRequestModuleName(pakage).replace(".", "/") + ".ts",
                content = """|
                |${type.imports(type.tsRequestModuleName(pakage))}
                |import { ${apiRequest.simpleClassName} } from '${relativizePaths(type.tsRequestModuleName(pakage), apiRequest.`package`)}'
                |
                |export class ${type.toRequestBuilderName()} {
                |
                |    <${type.constructor()}>
                |    <${type.subResources()}>
                |    <${type.methods()}>
                |
                |}
                |
            """.trimMargin().keepIndentation()

        )
    }


    protected fun Resource.constructor(): String {

        val pathArgs = if (this.fullUri.variables.isEmpty())
            ""
        else
            this
                    .fullUri
                    .variables
                    .map { "      $it: string" }
                    .joinToString(
                            separator = ",\n",
                            prefix = "pathArgs: {\n",
                            postfix = "\n }")

        return """|
                    |  constructor(
                    |    protected readonly args: {
                    |      <$pathArgs>,
                    |      middlewares: Middleware[];
                    |    }
                    |  ) {}
                    """.trimMargin()
    }

    protected fun Resource.methods(): String {

        return this.methods
                .map {

                        var queryParamsArg = ""
                        var bodies = ""
                        if (!it.queryParameters.isEmpty()) {
                            val allQueryParamsOptional = it.queryParameters.map { !it.required }.reduce(Boolean::and)
                            queryParamsArg =
                                    """|queryArgs${if (allQueryParamsOptional) "?" else ""}: {
                            |   <${it.queryParameters.map { "${it.name.tsRemoveRegexp()}${if (it.required) "" else "?"}: ${it.type.toVrapType().simpleTSName()}" }.joinToString(separator = "\n")}>
                            |},""".trimMargin()
                        }
                        if (!it.bodies.isEmpty()) {
                            bodies = "body: ${it.bodies.map {
                                it.type.toVrapType().simpleTSName()
                            }.joinToString(separator = " | ")},"
                        }

                        val argsAreOptional =
                                it.bodies.isNullOrEmpty()
                                        && (
                                        it.queryParameters.isNullOrEmpty() ||
                                                it.queryParameters.map { !it.required }.reduce(Boolean::and)
                                        )

                    val methodArgs = """ |
                            |methodArgs${if (argsAreOptional) "?" else ""}:{
                            |   
                            |   <$queryParamsArg>
                            |   <$bodies>
                            |   <${it.headersPartInMethodSigniture()}>
                            |}
                        """.trimMargin()

                    val methodReturn = "ApiRequest\\<${it.returnType().toVrapType().simpleTSName()}\\>"

                    val bodyLiteral = """|{
                        |   baseURL: '${api.baseUri.template}',
                        |   method: '${it.methodName.toUpperCase()}',
                        |   uriTemplate: '${it.resource().fullUri.template}',
                        |   pathVariables: this.args.pathArgs,
                        |   headers: {
                        |       <${if(it.tsMediaType().isNotEmpty()) "${it.tsMediaType()}," else ""}>
                        |       ...(methodArgs || {} as any).headers
                        |   },
                        |   <${if(it.queryParameters.isNullOrEmpty()) "" else "queryParams: (methodArgs || {} as any).queryArgs,"}>
                        |   <${if(it.bodies.isNullOrEmpty()) "" else "body: (methodArgs || {} as any).body,"}>
                        |}
                    """.trimMargin()


                    """|
                    |${it.methodName}(<$methodArgs>): $methodReturn {
                    |   return new $methodReturn(
                    |       <$bodyLiteral>,
                    |       this.args.middlewares
                    |   )
                    |}
                    |
                 """.trimMargin()
                }.joinToString(separator = "")
    }

    fun Method.headersPartInMethodSigniture(): String {
        return """
            |headers${if (this.headerIsRequired()) "" else "?"}: {
            |   <${this
                .headers
                .map { "'${it.name}': ${(it.type as StringType).enum.map { "'${it.value}'" }.joinToString(separator = " | ")}" }
                .joinToString(separator = "\n")
        }>
            |   [key:string]:string
            |},
        """.trimMargin()
    }

    fun Method.headerIsRequired(): Boolean{
        if(this.headers.isEmpty()){
            return false
        }
        return this.headers.map { (it.type as StringType).enum.size>1 }.reduce{acc, b -> acc || b}
    }



    fun Resource.imports(moduleName: String): String {
        return this.resources
                .map {
                    val relativePath = relativizePaths(moduleName, it.tsRequestModuleName((this.toVrapType() as VrapObjectType).`package`))
                    "import { ${it.toRequestBuilderName()} } from '$relativePath'"
                }.plus(
                        this.methods
                                .flatMap { it.bodies }
                                .filter { it.type != null }
                                .map { it.type.toVrapType() }
                                .filter { it is VrapObjectType }
                                .map { it as VrapObjectType }
                                .map {
                                    when(it){
                                        is VrapLibraryType -> "import { ${it.simpleClassName} } from '${it.`package`}'"
                                        else -> {
                                            val relativePath = relativizePaths(moduleName, it.`package`)
                                            "import { ${it.simpleTSName()} } from '$relativePath'"

                                        }
                                    }
                                }
                )
                .plus(
                        this.methods
                                .map { it.returnType().toVrapType() }
                                .filter { it is VrapObjectType }
                                .map { it as VrapObjectType }
                                .map {
                                    val relativePath = relativizePaths(moduleName, it.`package`)
                                    "import { ${it.simpleTSName()} } from '$relativePath'"
                                }
                )
                .plus(
                        "import { ${middleware.simpleClassName} } from '${relativizePaths(moduleName, middleware.`package`)}'"
                )
                .distinct()
                .joinToString(separator = "\n")
    }

    override fun hasPathArgs(): Boolean = true

}



