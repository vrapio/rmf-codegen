/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.model

import io.vrap.rmf.codegen.types.*

fun VrapType.pyTypeName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> "${this.scalarType}"
        is VrapEnumType -> this.simpleClassName
        is VrapObjectType -> this.simpleClassName
        is VrapArrayType -> "[]${this.itemType.simplePyName()}"
        is VrapNilType -> "nil"
    }
}

fun VrapType.goTypeName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> "${this.scalarType}"
        is VrapEnumType -> this.simpleClassName
        is VrapObjectType -> this.simpleClassName
        is VrapArrayType -> "[]${this.itemType.goTypeName()}"
        is VrapNilType -> "nil"
    }
}

fun VrapType.goExportedTypeName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> "${this.scalarType}"
        is VrapEnumType -> this.simpleClassName
        is VrapObjectType -> this.simpleClassName
        is VrapArrayType -> "[]${this.itemType.simplePyName()}"
        is VrapNilType -> "nil"
    }
}

fun VrapType.simplePyName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> this.simpleClassName
        is VrapObjectType -> this.simpleClassName
        is VrapArrayType -> this.itemType.simplePyName()
        is VrapNilType -> "nil"
    }
}

fun VrapType.flattenVrapType(): VrapType {
    return when (this) {
        is VrapArrayType -> {
            this.itemType.flattenVrapType()
        }
        else -> this
    }
}
