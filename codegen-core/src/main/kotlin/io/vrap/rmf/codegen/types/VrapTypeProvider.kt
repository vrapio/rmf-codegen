package io.vrap.rmf.codegen.types

import com.google.inject.Inject
import io.vrap.rmf.codegen.di.TypeNamePrefix
import io.vrap.rmf.raml.model.elements.NamedElement
import io.vrap.rmf.raml.model.types.ArrayType
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.ComposedSwitch
import org.slf4j.LoggerFactory

class VrapTypeProvider @Inject constructor(packageProvider: PackageProvider,
                                           val languageBaseTypes: LanguageBaseTypes,
                                           @TypeNamePrefix val typeNamePrefix: String,
                                           val customTypeMapping: MutableMap<String, VrapType>
) : ComposedSwitch<VrapType>() {


    init {
        addSwitch(AnyTypeProvider(packageProvider, languageBaseTypes, typeNamePrefix))
        addSwitch(ResourcesTypeProvider(packageProvider))
    }

    override fun doSwitch(eObject: EObject): VrapType {
        if (eObject is ArrayType && eObject.items is NamedElement) {
            var itemsType = customTypeMapping[eObject.items.name]
            if (itemsType == null) {
                itemsType = super.doSwitch(eObject.items)
            }
            if (itemsType != null) {
                return VrapArrayType(itemsType)
            }
        }
        if (eObject is NamedElement) {
            val className = customTypeMapping[eObject.name]
            if (className != null) {
                return className
            }
        }

        val result = super.doSwitch(eObject)

        if (result == null) {
            LOGGER.warn("No typeName was associated with {}", eObject)
            return languageBaseTypes.objectType
        }
        return result
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(VrapTypeProvider::class.java)
    }
}
