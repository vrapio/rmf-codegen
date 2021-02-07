/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.model

import io.vrap.codegen.languages.extensions.ExtensionsBase
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.types.StringType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property
import io.vrap.rmf.raml.model.types.UnionType
import org.eclipse.emf.ecore.EObject
import java.util.*

interface PyObjectTypeExtensions : ExtensionsBase {

    fun AnyType.moduleName(): String {
        val type = this.toVrapType()
        return when (type) {
            is VrapObjectType -> type.`package`
            is VrapEnumType -> type.`package`
            else -> ""
        }
    }

    private fun ObjectType.getTypeDependencies(): List<VrapType> {
        return this.allProperties
            .map { it.type }
            .flatMap { if (it is UnionType) it.oneOf else Collections.singletonList(it) }
            .filterNotNull()
            .map { it.toVrapType() }
            .map { it.flattenVrapType() }
            .filterNotNull()
            .filter { it !is VrapScalarType }
    }

    fun EObject?.toVrapType(): VrapType {
        val vrapType = if (this != null) vrapTypeProvider.doSwitch(this) else VrapNilType()
        return vrapType.createGoVrapType()
    }

    fun EObject?.toPythonVrapType(): VrapType {
        val vrapType = if (this != null) vrapTypeProvider.doSwitch(this) else VrapNilType()
        return vrapType.createGoVrapType()
    }

    fun VrapType.createGoVrapType(): VrapType {
        return when (this) {
            is VrapObjectType -> {
                VrapObjectType(`package` = this.`package`.goModelFileName(), simpleClassName = this.simpleClassName)
            }
            is VrapEnumType -> {
                VrapEnumType(`package` = this.`package`.goModelFileName(), simpleClassName = this.simpleClassName)
            }
            is VrapArrayType -> {
                VrapArrayType(itemType = this.itemType.createGoVrapType())
            }
            else -> this
        }
    }

    fun List<AnyType>.getEnumVrapTypes(): List<VrapType> {
        return this
            .filterIsInstance<ObjectType>()
            .flatMap { it.allProperties }
            .map { it.type.toVrapType() }
            .map {
                when (it) {
                    is VrapEnumType -> it
                    is VrapArrayType ->
                        when (it.itemType) {
                            is VrapEnumType -> it
                            else -> null
                        }
                    else -> null
                }
            }
            .filterNotNull()
    }

    fun List<VrapType>.getImportsForModelVrapTypes(moduleName: String): List<String> {
        return this
            .map { it.flattenVrapType() }
            .distinct()
            .filter {
                when (it) {
                    is VrapObjectType -> it.`package` != moduleName
                    is VrapEnumType -> it.`package` != moduleName
                    else -> false
                }
            }
            .groupBy {
                when (it) {
                    is VrapObjectType -> it.`package`
                    is VrapEnumType -> it.`package`
                    else -> throw IllegalStateException("this case should have been filtered")
                }
            }
            .toSortedMap()
            .map {
                val allImportedClasses = it.value.map { it.simplePyName() }.sorted().joinToString(", ")
                "from ${it.key.toRelativePackageName(moduleName)} import $allImportedClasses"
            }
    }

    fun List<AnyType>.getTypeInheritance(type: AnyType): List<AnyType> {
        return this
            .filter { it.type != null && it.type.name == type.name }
        // TODO: Shouldn't this be necessary?
        // .plus(
        //     this
        //     .filter { it.type != null && it.type.name == type.name }
        //     .flatMap { this.getTypeInheritance(it.type) }
        // )
    }

    fun ObjectType.PyClassProperties(all: Boolean): List<Property> {
        var props: List<Property> = allProperties

        if (!all) {
            val parentProps = getSuperProperties().map { it.name }
            props = allProperties.filter { !parentProps.contains(it.name) }
        }
        return props.filter {
            (
                (discriminator() == null || it.name != discriminator()) ||
                    (it.name == discriminator() && discriminatorValue == null)
                )
        }
    }

    fun ObjectType.getSuperProperties(): List<Property> {
        return when (this.type) {
            is ObjectType -> (this.type as ObjectType).allProperties
            else -> emptyList<Property>()
        }
    }

    fun AnyType.isDiscriminated(): Boolean {
        if (this !is ObjectType) {
            return false
        }
        if (this.discriminator() != null && this.discriminatorValue.isNullOrEmpty()) {
            val parentType = this.type
            if (parentType is ObjectType && !parentType.discriminatorValue.isNullOrEmpty()) {
                return false
            }
            return true
        }
        return false
    }

    fun ObjectType.isErrorObject(): Boolean {
         if (!name.toLowerCase().contains("error")) {
             return false
         }

        return PyClassProperties(true)
            .any {
                it.type is StringType && it.name.toLowerCase() == "message"
            }
    }

    fun ObjectType.isDict(): Boolean {

        if (this.type != null && this.type.getAnnotation("asMap") != null) {
            return true
        }
        if (this.getAnnotation("asMap") != null) {
            return true
        }
        return false
    }
}
