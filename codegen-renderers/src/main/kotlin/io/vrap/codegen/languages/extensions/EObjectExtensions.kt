package io.vrap.codegen.languages.extensions

import io.vrap.rmf.codegen.types.VrapNilType
import org.eclipse.emf.ecore.EObject

interface EObjectExtensions : ExtensionsBase {
    fun EObject?.toVrapType() = if(this !=null ) vrapTypeProvider.doSwitch(this) else VrapNilType()

}
