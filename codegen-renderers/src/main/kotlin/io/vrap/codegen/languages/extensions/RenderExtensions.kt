package io.vrap.codegen.languages.extensions

import com.hypertino.inflector.English

fun String.singularize(): String {
    return English.singular(this)
}
