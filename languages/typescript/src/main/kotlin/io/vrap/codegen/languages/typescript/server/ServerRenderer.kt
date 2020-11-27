package io.vrap.codegen.languages.typescript.server

import com.google.common.collect.Iterables
import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.*
import io.vrap.codegen.languages.typescript.*
import io.vrap.codegen.languages.typescript.joi.simpleJoiName
import io.vrap.rmf.codegen.di.ClientPackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.resources.ResourceContainer
import io.vrap.rmf.raml.model.responses.Response
import io.vrap.rmf.raml.model.types.ArrayInstance
import io.vrap.rmf.raml.model.types.StringInstance

class ServerRenderer @Inject constructor(
        val api: Api,
        override val vrapTypeProvider: VrapTypeProvider,
        val constantsProvider: ConstantsProvider,
        @ClientPackageName val client_package: String
) : FileProducer, EObjectExtensions {

    override fun produceFiles(): List<TemplateFile> = listOf(apiServer())


    val moduleFileName = "${constantsProvider.serverModule}.ts"


    fun apiServer(): TemplateFile {
        val moduleName = "$client_package/joi-server"

        val content = """
            |$tsGeneratedComment
            |
            |import * as Joi from 'joi'
            |<${handlerImports(moduleName)}>
            |<${joiValidatorImports(moduleName)}>
            |
            |const requiredString = Joi.string().required()
            |
            |export type ApiServer = {
            |    <${serverDef(api)}>
            |}
            |
            |export function toHapiServerRoutes(
            |   arg: {
            |       apiServer: ApiServer,
            |       failAction: Lifecycle.FailAction,
            |       handleError: ErrorHandler
            |   }
            |) : ServerRoute[] {
            |    const { apiServer, failAction, handleError } = arg
            |    return [
            |               <${resourceArray()}>
            |           ]
            |}
            """
                .trimMargin()
                .keepIndentation()


        return TemplateFile(
                relativePath = moduleFileName,
                content = content)


    }

    private fun serverDef(resourceContainer: ResourceContainer): String {
        val listOfString = listOf<String>()
                .plus(
                        if (resourceContainer is Resource) {
                            resourceContainer
                                    .methods
                                    .map {
                                        "${it.methodName.toLowerCase()}: ${it.toHandlerName()}"
                                    }
                        } else {
                            emptyList()
                        }
                )
                .plus(
                        resourceContainer
                                .resources
                                .map {
                                    """
                                    |"${it.relativePath()}": {
                                    |    <${serverDef(it)}>
                                    |}
                                    """.trimMargin()
                                }
                )

        return listOfString.joinToString(separator = ",\n")

    }

    fun resourceArray(): String {

        // the old code for handeler navigator ${it.handlerNavigator()
        return api.allMethods()
                .map {
                    """ |{
                        |   path: '${it.resource().fullUri.template}',
                        |   method: '${it.methodName.toUpperCase()}',
                        |   handler: async (request: Request, responseToolkit: ResponseToolkit, err?: Error) =\> {
                        |        const method =
                        |          ${it.handlerNavigator()}
                        |        try {
                        |          const result = await method({
                        |            headers: request.headers,
                        |            ${if(it.hasPathParams()) "pathParams: request.params as any," else ""}
                        |            ${if(it.hasQueryParams()) "queryParams: request.query as any," else ""}
                        |            <${if(it.hasBody()) "body: request.payload as any" else ""}>
                        |          });
                        |          ${it.removeUnknownProperties()}
                        |          const response = responseToolkit
                        |            .response(${if(it.hasReturnPayload()) "body" else ""})
                        |            .code(result.statusCode);
                        |          // Add headers to response
                        |          for (const header in result.headers) {
                        |            response.header(header, result.headers[header]);
                        |          }
                        |          return response;
                        |         } catch (error) {
                        |          return handleError({
                        |            request,
                        |            responseToolkit,
                        |            error
                        |          })
                        |         }
                        |   },
                        |   options: {
                        |      validate: {
                        |        <${if(it.hasBody()) "payload: ${it.bodies[0].type.toVrapType().simpleJoiName()}()," else ""}>
                        |        params: {
                        |          <${it.resource().fullUri.variables.map { "$it: requiredString" }.joinToString(separator = ",\n")}>
                        |        },
                        |        options: {
                        |          abortEarly: false
                        |        },
                        |        failAction,
                        |      },${it.auth()}
                        |    }
                        |}
                    """.trimMargin()
                }.joinToString(separator = ",\n")
    }

    private fun Method.auth(): String {
        if(this.hasScopes()) {
            return """
                        |        auth: {
                        |           scope: [${this.scopes()}],
                        |        },"""
        }
        return ""
    }

    private fun Method.hasScopes(): Boolean {
        return this.securedBy
                .map { it.parameters?.getValue("scopes") }
                .filterIsInstance<ArrayInstance>().isNotEmpty()
    }

    private fun Method.scopes(): String  {
        if (this.hasScopes()) {
            val scopes = this.securedBy
                    .map { it.parameters?.getValue("scopes") }
                    .filterIsInstance<ArrayInstance>()
                    .flatMap { it.value }.filterIsInstance<StringInstance>()
                    .map { """'${it.value.replace("{","{params.")}'""" }
            return scopes.joinToString(", ")
        }
        return ""
    }

    private fun Response.stripUnkownProperties(): String {
        if (this.bodies.size == 1) {
            val joiValidator = this.bodies[0].type.toVrapType().simpleJoiName()
            return """|              case ${this.statusCode}: {
                      |                 body = ${joiValidator}().validate(result.body, { stripUnknown: true })
                      |                 break
                      |              }"""
        }
        return ""
    }

    private fun Method.removeUnknownProperties(): String {
        if (this.responses.size > 0) {
            val cases = this.responses.joinToString(separator = "\n") { it.stripUnkownProperties() }
            return """|          let body  
                      |          switch (result.statusCode) {
                      $cases
                      |          }"""
        }
        return ""
    }

    private fun Method.handlerNavigator(): String = this.resource().fullUri
            .template
            .split("/")
            .filter { it.isNotBlank() }
            .map {
                "'$it'"
            }
            .joinToString(
                    separator = "][",
                    prefix = "apiServer[",
                    postfix = "].${this.methodName}"
            )


    private fun Resource.relativePath(): String = this.relativeUri.template.replaceFirst("/", "")

    private fun handlerImports(moduleName: String): String {
        return api.allMethods()
                .map {
                    VrapObjectType(constantsProvider.parametersModule, it.toHandlerName())
                }
                .plus(
                        listOf(
                                constantsProvider.Resource,
                                constantsProvider.ServerRoute,
                                constantsProvider.Lifecycle,
                                constantsProvider.Request,
                                constantsProvider.ResponseToolkit,
                                constantsProvider.ErrorHandler
                        )
                )
                .map {
                    it.toImportStatement()
                }
                .joinToString(separator = "\n")

    }

    private fun joiValidatorImports(moduleName: String): String {
        val requestTypes = api.allMethods()
                .filter { it.bodies.isNotEmpty() }
                .map { it.bodies[0] }
                .filter { it.type != null }
                .map { it.type.toVrapType() }
        val responseTypes = api.allMethods()
                .filter { it.responses.size > 0  }
                .flatMap {  it.responses }
                .filter { it.bodies.size == 1}
                .map { it.bodies[0].type }
                .filter { it != null }
                .map { it.toVrapType() }

        val allTypes = Iterables.concat(requestTypes, responseTypes).toMutableList()

        return allTypes
                .distinct()
                .map {
                    it.joiImportStatement()
                }
                .joinToString(separator = "\n")
    }

    private fun VrapType.joiImportStatement():String {
        return when (this) {
            is VrapObjectType -> {
                val joiType = this.toJoiVrapType()
                "import { ${joiType.simpleClassName} } from '${joiType.`package`}'"
            }
            is VrapArrayType -> {
                return this.itemType.joiImportStatement()
            }
            is VrapScalarType -> {
                return ""
            }
            else -> throw Error("not supposed to arrive here")
        }
    }

}