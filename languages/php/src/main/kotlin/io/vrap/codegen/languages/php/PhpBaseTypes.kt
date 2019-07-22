package io.vrap.codegen.languages.php

import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.LanguageBaseTypes
import io.vrap.rmf.codegen.types.VrapObjectType

object PhpBaseTypes : LanguageBaseTypes(
        objectType = fromPhpType("", "stdClass"),
        integerType = fromScalarPhpType("int"),
        longType = fromScalarPhpType("int"),
        doubleType = fromScalarPhpType("float"),
        stringType = fromScalarPhpType("string"),
        booleanType = fromScalarPhpType("bool"),
        dateTimeType = fromPhpType("", "DateTimeImmutable"),
        dateOnlyType = fromPhpType("", "DateTimeImmutable"),
        timeOnlyType = fromPhpType("", "DateTimeImmutable"),
        file = fromPhpType("","File")
)

fun  fromPhpType(`package`: String, simpleName: String):VrapObjectType{
   return VrapObjectType(`package`, simpleName)
}

fun  fromScalarPhpType(scalarType: String):VrapScalarType{
   return VrapScalarType(scalarType)
}
