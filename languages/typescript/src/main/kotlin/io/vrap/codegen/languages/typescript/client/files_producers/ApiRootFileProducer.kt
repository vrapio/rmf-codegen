package io.vrap.codegen.languages.typescript.client.files_producers

import com.google.inject.Inject
import io.vrap.codegen.languages.typescript.client.AbstractRequestBuilder
import io.vrap.codegen.languages.typescript.relativizePaths
import io.vrap.codegen.languages.typescript.tsRequestModuleName
import io.vrap.rmf.codegen.di.ClientPackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api

class ApiRootFileProducer @Inject constructor(
        @ClientPackageName val client_package: String,
        api: Api,
        vrapTypeProvider: VrapTypeProvider
) : FileProducer, AbstractRequestBuilder(api, vrapTypeProvider) {


    override fun produceFiles(): List<TemplateFile> {

        return listOf(produceApiRoot(api))
    }

    fun produceApiRoot(type: Api): TemplateFile {
        val moduleName = "$client_package.ApiRoot"
        return TemplateFile(
                relativePath = moduleName.replace(".", "/") + ".ts",
                content = """|
                |${type.imports(moduleName)}
                |import { ApiRequest } from '${relativizePaths(moduleName, "base.requests-utils")}'
                |
                |export class ApiRoot {
                |
                |  constructor(
                |    protected readonly args: {
                |      middlewares: Middleware[];
                |    }
                |  ) {}
                |
                |  <${type.subResources()}>
                |
                |}
                |
            """.trimMargin().keepIndentation()

        )
    }

    fun Api.imports(moduleName: String): String {
        return this.resources
                .map {
                    val relativePath = relativizePaths(moduleName, it.tsRequestModuleName((it.toVrapType() as VrapObjectType).`package`))
                    "import { ${it.toRequestBuilderName()} } from '$relativePath'"
                }
                .plus(
                        "import { ${middleware.simpleClassName} } from '${relativizePaths(moduleName, middleware.`package`)}'"
                )
                .distinct()
                .joinToString(separator = "\n")
    }

    override fun hasPathArgs(): Boolean = false
}