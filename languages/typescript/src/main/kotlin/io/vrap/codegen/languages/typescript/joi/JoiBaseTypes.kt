package io.vrap.codegen.languages.typescript.joi

import io.vrap.rmf.codegen.types.LanguageBaseTypes
import io.vrap.rmf.codegen.types.VrapScalarType

object JoiBaseTypes : LanguageBaseTypes(

    objectType = nativeTypeScriptType("any"),
    integerType = nativeTypeScriptType("number"),
    longType = nativeTypeScriptType("number"),
    doubleType = nativeTypeScriptType("number"),
    stringType = nativeTypeScriptType("string"),
    booleanType = nativeTypeScriptType("boolean"),
    dateTimeType = nativeTypeScriptType("date"),
    dateOnlyType = nativeTypeScriptType("date"),
    timeOnlyType = nativeTypeScriptType("string"),
    file = nativeTypeScriptType("string")

)

fun nativeTypeScriptType(typeName: String): VrapScalarType = VrapScalarType(typeName)
