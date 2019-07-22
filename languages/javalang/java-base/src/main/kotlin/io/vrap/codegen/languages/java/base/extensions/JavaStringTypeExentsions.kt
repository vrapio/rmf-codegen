package io.vrap.codegen.languages.java.base.extensions

import io.vrap.rmf.raml.model.util.StringCaseFormat

/**
 * Returns this string as standard java enum name (upper underscore case).
 */
fun String.enumValueName(): String {
    return StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this)
}

fun String.upperCamelCase() : String {
    return StringCaseFormat.UPPER_CAMEL_CASE.apply(this)
}

fun String.lowerCamelCase() : String {
    return StringCaseFormat.LOWER_CAMEL_CASE.apply(this)
}