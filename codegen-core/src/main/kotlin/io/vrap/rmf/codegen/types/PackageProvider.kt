package io.vrap.rmf.codegen.types

import com.google.inject.Inject
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.di.ClientPackageName
import io.vrap.rmf.codegen.di.ModelPackageName
import io.vrap.rmf.raml.model.modules.Library
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.resources.util.ResourcesSwitch
import io.vrap.rmf.raml.model.types.AnnotationsFacet
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.types.util.TypesSwitch
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.ComposedSwitch


class PackageProvider @Inject constructor(
        @BasePackageName val basePackage: String,
        @ModelPackageName val modelPackage: String,
        @ClientPackageName val clientPackage: String
) : ComposedSwitch<String>() {

    init {
        addSwitch(TypePackageSwitch())
        addSwitch(ResourcePackageSwitch())
    }

    override fun defaultCase(eObject: EObject): String {
        return basePackage
    }

    private inner class TypePackageSwitch : TypesSwitch<String>() {
        override fun defaultCase(`object`: EObject?): String = modelPackage

        override fun caseAnyType(type: AnyType?): String {
            var currentType = type
            while (currentType != null) {

                val annotation = currentType.getAnnotation("package")
                if (annotation != null) {
                    return annotation.let { it.value }
                            ?.let { it.value }
                            ?.let { "$modelPackage.$it" }
                            ?: modelPackage
                }
                if (currentType.eContainer() is Library && (currentType.eContainer() as Library).getAnnotation("package") != null) {

                    var eContainer: EObject? = currentType.eContainer()
                    while (eContainer != null) {
                        if (eContainer is AnnotationsFacet) {
                            return eContainer.let { it.getAnnotation("package") }
                                    ?.let { it.value?.value }
                                    ?.let { "$modelPackage.$it" }
                                    ?: modelPackage
                        }
                        eContainer = eContainer.eContainer()

                    }
                }
                currentType = currentType.type
            }

            return modelPackage
        }

    }

    private inner class ResourcePackageSwitch : ResourcesSwitch<String>() {
        override fun caseMethod(`object`: Method): String {
            return "$clientPackage"
        }

        override fun caseResource(`object`: Resource?): String {
            return "$clientPackage"
        }

        override fun defaultCase(`object`: EObject): String {
            return clientPackage
        }
    }
}
