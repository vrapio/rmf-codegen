package io.vrap.codegen.languages.php.extensions

import io.vrap.codegen.languages.extensions.ExtensionsBase
import io.vrap.rmf.codegen.types.VrapNilType
import org.eclipse.emf.ecore.EObject

interface EObjectTypeExtensions : ExtensionsBase {
    fun EObject?.toVrapType() = if(this !=null ) vrapTypeProvider.doSwitch(this) else VrapNilType()

}
