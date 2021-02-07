/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.client

import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.returnType
import io.vrap.codegen.languages.go.*
import io.vrap.codegen.languages.go.model.PyObjectTypeExtensions
import io.vrap.codegen.languages.go.model.goName
import io.vrap.codegen.languages.go.model.goTypeName
import io.vrap.codegen.languages.go.model.simplePyName
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.MethodRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.VrapType
import io.vrap.rmf.raml.model.types.FileType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.resources.Method

class GoMethodRenderer constructor(
    private val clientConstants: ClientConstants,
    override val vrapTypeProvider: VrapTypeProvider,
    @BasePackageName val basePackageName: String
) : MethodRenderer, PyObjectTypeExtensions {

    override fun render(type: Method): TemplateFile {

        val filename = type.goClientFileName()
        return TemplateFile(
            relativePath = "$basePackageName/$filename.go",
            content = """|
                |$pyGeneratedComment
                |package $basePackageName
                |
                |<${type.importStatement()}>
                |
                |<${type.constructor()}>
                |<${type.renderFuncExecute()}>
            """.trimMargin().keepIndentation()
        )
    }

    private fun Method.importStatement(): String {
        val modules = mutableListOf<String>(
            "context",
            "encoding/json",
            "fmt",
            "io/ioutil",
            "net/url"
        )

        if (this.bodyType() is FileType) {
            modules.add("io")
        }

        return modules
        .map {"    \"$it\""}
        .joinToString(prefix = "import(\n", separator = "\n", postfix = "\n)")
    }


    protected fun Method.renderFuncQueryParams(): String {
        val methodKwargs = listOf<String>()
        .plus(
            this.queryParameters
                .map {
                    if (it.required)
                        if (it.isPatternProperty())
                            "${it.paramName()} map[string]${it.type.toVrapType().goTypeName()}"
                        else
                            "${it.name} ${it.type.toVrapType().goTypeName()}"
                    else if (it.isPatternProperty())
                        "${it.paramName()} map[string]${it.type.toVrapType().goTypeName()}"
                    else
                        "${it.name.goName()} ${it.type.toVrapType().goTypeName()}"
                }
        )
        .filter {
            it != ""
        }
        .joinToString(separator = ", ")

        val queryParamsExpr = this.queryParameters
        .filter { !it.isPatternProperty() }
        .map {
            """"${it.name}": ${it.name.goName()}"""
        }
        .joinToString(", ", prefix = "params := url.Values{", postfix = "}")

        return ""


    }

    protected fun Method.constructor(): String {
        val bodyVrapType = this.vrapType()
        return """
            |type ${toStructName()} struct {
            |   ${if (bodyVrapType != null) "body    ${this.vrapType()?.goTypeName()}" else ""}
            |   url     string
            |   query   url.Values
            |   client  *Client
            |}
            """.trimMargin()
    }

    private fun Method.renderFuncExecute(): String {
        var methodReturn = if (this.returnType().toVrapType().goTypeName() != "nil")
            "(result *${this.returnType().toVrapType().goTypeName()}, err error)"
        else
            "error"

        var bodyExpr = ""
        val bodyVrapType = this.vrapType()
        if (this.methodName.toLowerCase() != "get") {
            if (bodyVrapType is VrapScalarType && bodyVrapType.scalarType == "io.Reader") {
                bodyExpr = "data := rb.body"
            } else if (bodyVrapType is VrapObjectType) {
                bodyExpr = """
                |data, err := serializeInput(rb.body)
                |if err != nil {
                |    return ${if (hasReturnValue()) "nil, " else ""}err
                |}
                """.trimMargin()
            }
        }

        val methodKwargs = ""

        var bodyArg = ""
        if (this.methodName.toLowerCase() != "get") {
            bodyArg = if (bodyExpr != "") "data," else "nil,"
        }

        return """
        |<${this.toDocString().escapeAll()}>
        |func (rb *${this.toStructName()}) Execute(<$methodKwargs>) $methodReturn {
        |    <${bodyExpr}>
        |    resp, err := rb.client.${this.methodName.toLowerCase()}(
        |        context.Background(),
        |        rb.url,
        |        rb.query,
        |        <${bodyArg}>
        |    )
        |    <${this.responseHandler()}>
        |}
        """.trimMargin()
    }

    fun Method.hasReturnValue(): Boolean {
        return this.returnType().toVrapType().goTypeName() != "nil"
    }

    fun Method.responseHandler(): String {
        data class Key(val className: String, val success: Boolean)

        val returnValue = if (this.hasReturnValue()) "nil, " else ""
        val switchStatements = this.responses
            .map {
                val statusCode = it.statusCode
                if (it.bodies.isNotEmpty()) {
                    val vrap = it.bodies[0].type.toVrapType()
                    vrap.simplePyName() to statusCode
                } else {
                    "nil" to statusCode
                }
            }
            .groupBy {
                Key(it.first, (it.second.toInt() in (200..299)))
            }
            .mapValues {
                entry ->
                entry.value.map { it.second.toInt() }
            }
            .map {

                val statusCodes = mutableListOf<Int>()
                statusCodes.addAll(it.value)

                // Hack to work around incorrect importapi raml vs implementation
                // if (statusCodes.contains(201) && !statusCodes.contains(200)) {
                //     statusCodes.add(200)
                // }

                if (it.key.className == "nil") {
                    if (it.key.success) {
                        """
                        |case ${statusCodes.joinToString(", ")}:
                        |    return ${returnValue}nil
                        """.trimMargin()
                    } else {
                        """
                        |case ${statusCodes.joinToString(", ")}:
                        |    return ${returnValue}fmt.Errorf("StatusCode %d returend", resp.StatusCode)
                        """.trimMargin()
                    }
                } else {
                    if (it.key.success) {
                        """
                        |case ${statusCodes.joinToString(", ")}:
                        |    err = json.Unmarshal(content, &result)
                        |    return result, nil
                        """.trimMargin()
                    } else {
                        """
                        |case ${statusCodes.joinToString(", ")}:
                        |     errorObj := ${it.key.className}{}
                        |     err = json.Unmarshal(content, &errorObj)
                        |     if (err != nil) {
                        |         return ${returnValue}err
                        |     }
                        |     return ${returnValue}errorObj
                        """.trimMargin()
                    }
                }
            }.joinToString("\n")

        return """
        |if (err != nil) {
        |    return nil, err
        |}
    	|content, err := ioutil.ReadAll(resp.Body)
	    |defer resp.Body.Close()
        |switch resp.StatusCode {
        |    <$switchStatements>
        |    default:
        |        return ${returnValue}fmt.Errorf("Unhandled StatusCode: %d", resp.StatusCode)
        |}
        """
    }

    fun Method.vrapType(): VrapType? {
        val bodyType = this.bodyType()
        if (bodyType != null) {
            return bodyType.toVrapType()
        }
        return null
    }


}
