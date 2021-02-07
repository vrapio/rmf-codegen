/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.model

import io.vrap.rmf.codegen.types.LanguageBaseTypes
import io.vrap.rmf.codegen.types.VrapScalarType

object GoBaseTypes : LanguageBaseTypes(
    anyType = nativeGoType("interface{}"),
    objectType = nativeGoType("interface{}"),
    integerType = nativeGoType("int"),
    longType = nativeGoType("int"),
    doubleType = nativeGoType("float64"),
    stringType = nativeGoType("string"),
    booleanType = nativeGoType("bool"),
    dateTimeType = nativeGoType("time.Time"),
    dateOnlyType = nativeGoType("Date"),
    timeOnlyType = nativeGoType("time.Time"),
    file = nativeGoType("io.Reader")
)

fun nativeGoType(typeName: String): VrapScalarType = VrapScalarType(typeName)
