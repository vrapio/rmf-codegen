/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.model
import io.vrap.rmf.raml.model.util.StringCaseFormat

fun String.toRelativePackageName(base: String): String {
    val partsTo = this.split(".")
    val partsFrom = base.split(".")

    var path = ""
    for (i in partsFrom.size - 1 downTo 0) {
        if (i < partsTo.size) {
            if (partsFrom.slice(0..i).joinToString(".") == partsTo.slice(0..i).joinToString(".")) {
                if (path == "") path = "."
                return path + partsTo.slice(i + 1..partsTo.size - 1).joinToString(separator = ".")
            }
        }
        path += "."
    }
    return path + this
}

fun String.goModelFileName(): String {
    return this.split("/")
        .map { StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(it) }
        .joinToString(separator = "/")
        .replace("models/", "types_")
}

fun String.goClientFileName(): String {
    return this.split("/")
        .map { StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(it) }
        .joinToString(separator = "/")
        .replace("client/", "client_")
}

fun String.exportName(): String {
    val name = StringCaseFormat.UPPER_CAMEL_CASE.apply(this.replace(".", "_"))
    return name
}

fun String.goName(): String {
    val name = StringCaseFormat.LOWER_CAMEL_CASE.apply(this.replace(".", "_"))
    return name
}

fun String.snakeCase(): String {
    val name = StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(this.replace(".", "_"))
    return name
}
