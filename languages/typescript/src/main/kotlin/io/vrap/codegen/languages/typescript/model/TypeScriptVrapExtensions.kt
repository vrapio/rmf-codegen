package io.vrap.codegen.languages.typescript.model

import io.vrap.rmf.codegen.types.*

fun VrapType.simpleTSName():String{
    return when(this){
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> this.simpleClassName
        is VrapObjectType -> this.simpleClassName
        is VrapArrayType -> "${this.itemType.simpleTSName()}[]"
        is VrapNilType -> this.name
    }
}

