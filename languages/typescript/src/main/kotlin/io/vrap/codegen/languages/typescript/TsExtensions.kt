package io.vrap.codegen.languages.typescript


import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.toRequestName
import io.vrap.codegen.languages.extensions.toResourceName
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.StringType
import java.nio.file.Path
import java.nio.file.Paths


fun Method.tsRequestName():String = "${this.toRequestName()}Request"
fun Method.tsRequestModuleName(clientPackageName: String):String = "${if(clientPackageName.isEmpty()) "" else "$clientPackageName."}${this.resource().resourcePathName}.${this.toRequestName()}Request"

fun VrapObjectType.tsModuleName() : String = "${this.`package`.replace(".","/")}.ts"

fun Method.tsMediaType(): String {

    val  headers = mutableMapOf<String,List<String>>()
    if (!this.bodies.isNullOrEmpty() && !this.bodies[0].contentMediaType.type().isNullOrEmpty())
    {
        headers["Content-Type"] = listOf(this.bodies[0].contentMediaType.toString())
    }
    this.headers
            .map {
                it.name to (it.type as StringType).enum.map { it.value as String }
            }
            .toMap(headers)


    return headers
            .filter { it.value.size == 1 }
            .map {
        "'${it.key}': '${it.value[0]}'"
    }.joinToString(separator = "\n")

}

fun String.tsRemoveRegexp():String {
    if(this.startsWith("/")){
        val index = this.indexOf("\\")
        return this.substring(1,index)
    }
    if(this.contains(".")){
        val index = this.indexOf(".")
        return this.substring(index+1,this.length)
    }
    return this
}

fun Resource.tsRequestModuleName(clientPackageName: String):String = "$clientPackageName.${this.resourcePathName}.${this.toResourceName()}RequestBuilder"

fun relativizePaths(currentModule: String, targetModule: String): String {
    val currentRelative: Path = Paths.get(currentModule.replace(".", "/"))
    val targetRelative: Path = Paths.get(targetModule.replace(".", "/"))
    return "./" + currentRelative.relativize(targetRelative).toString().replaceFirst("../", "")
}