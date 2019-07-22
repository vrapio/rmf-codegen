package io.vrap.codegen.languages.typescript.joi

import io.vrap.codegen.languages.extensions.ExtensionsBase
import io.vrap.codegen.languages.typescript.model.simpleTSName
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.UnionType
import java.nio.file.Path
import java.nio.file.Paths

interface JoiObjectTypeExtensions : ExtensionsBase {

    fun getImportsForJoiModule(moduleName: String, types: List<AnyType>): String {

        return types
                .filter { it is ObjectType || it is UnionType}
                .flatMap {
                    when(it){
                        is ObjectType -> it.getDependencies()
                        is UnionType -> it.getDependencies()
                        else -> throw IllegalStateException("should not arrive here")
                    }

                }
                .distinct()
                .filter {
                    when (it) {
                        is VrapObjectType -> "${it.`package`}-types" != moduleName
                        is VrapEnumType -> "${it.`package`}-types" != moduleName
                        else -> false
                    }
                }
                .map {
                    when (it) {
                        is VrapObjectType -> "import { ${it.simpleJoiName()} } from '${relativisePaths(moduleName,"${it.`package`}-types")}'"
                        is VrapEnumType -> "import { ${it.simpleJoiName()} } from '${relativisePaths(moduleName,"${it.`package`}-types")}'"
                        else -> throw IllegalStateException("Unhandled case $it")
                    }
                }
                .joinToString(separator = "\n")
    }


    fun relativisePaths(currentModule:String, targetModule : String) : String {
        val currentRelative :Path = Paths.get(currentModule.replace(".","/"))
        val targetRelative : Path = Paths.get(targetModule.replace(".","/"))
        return currentRelative.relativize(targetRelative).toString().replaceFirst("../","./")
    }

    private fun UnionType.getDependencies(): List<VrapType> {

        return this
                .getOneOf()
                .map { vrapTypeProvider.doSwitch(it) }
                .map { toVrapType(it) }
                .filterNotNull()
                .filter { it !is VrapScalarType }

    }

    private fun ObjectType.getDependencies(): List<VrapType> {

        return this
                .allProperties
                .map { it.type }
                //If the subtipes are in the same package they should be imported
                .plus(this.subTypes)
                .plus(this.type)
                .filterNotNull()
                .map { vrapTypeProvider.doSwitch(it) }
                .map { toVrapType(it) }
                .filterNotNull()
                .filter { it !is VrapScalarType }

    }

}


private fun toVrapType(vrapType: VrapType): VrapType? {
    return when (vrapType) {
        is VrapObjectType -> vrapType
        is VrapEnumType -> vrapType
        is VrapArrayType -> {
            toVrapType(vrapType.itemType)
        }
        else -> null
    }
}

fun VrapType.simpleJoiName():String{
    return when(this){
        is VrapScalarType -> "Joi.${this.scalarType}"
        is VrapEnumType -> "${this.simpleClassName.decapitalize()}Type"
        is VrapObjectType -> "${this.simpleClassName.decapitalize()}Type"
        is VrapArrayType -> "${this.itemType.simpleTSName().decapitalize()}Type"
        is VrapNilType -> throw IllegalStateException("$this has no simple class name.")
    }
}
