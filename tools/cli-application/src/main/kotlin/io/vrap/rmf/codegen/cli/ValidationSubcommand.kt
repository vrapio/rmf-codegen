package io.vrap.rmf.codegen.cli

import io.vrap.codegen.languages.extensions.singularize
import io.vrap.codegen.languages.php.extensions.isScalar
import io.vrap.rmf.raml.model.RamlModelBuilder
import io.vrap.rmf.raml.model.elements.ElementsPackage
import io.vrap.rmf.raml.model.modules.ModulesPackage
import io.vrap.rmf.raml.model.resources.ResourcesPackage
import io.vrap.rmf.raml.model.responses.ResponsesPackage
import io.vrap.rmf.raml.model.security.SecurityPackage
import io.vrap.rmf.raml.model.types.ArrayType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property
import io.vrap.rmf.raml.model.types.TypesPackage
import io.vrap.rmf.raml.model.types.util.TypesSwitch
import io.vrap.rmf.raml.model.values.ValuesPackage
import io.vrap.rmf.raml.validation.DiagnosticsCreator
import org.eclipse.emf.common.util.Diagnostic
import org.eclipse.emf.common.util.DiagnosticChain
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.util.Diagnostician
import picocli.CommandLine
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Consumer

interface RamlValidationSetup {
    companion object {

        /**
         * Registers validators.
         */
        fun diag() : Diagnostician {
            val registry = EValidator.Registry.INSTANCE
            val propertyValidator = PropertyValidator()
            for (ePackage in PACKAGES) {
                val compositeValidator = CompositeValidator()
                compositeValidator.add(propertyValidator)
                val validator = registry.getEValidator(ePackage)
                if (validator != null) {
                    compositeValidator.add(validator)
                }
                registry[ePackage] = compositeValidator
            }

            return Diagnostician(registry)
        }

        val PACKAGES = Arrays.asList(
                ElementsPackage.eINSTANCE, ValuesPackage.eINSTANCE, ModulesPackage.eINSTANCE, ResourcesPackage.eINSTANCE,
                ResponsesPackage.eINSTANCE, SecurityPackage.eINSTANCE, TypesPackage.eINSTANCE)
    }
}

internal class CompositeValidator : EValidator {
    private val validators: MutableList<EValidator> = ArrayList()
    fun add(validator: EValidator) {
        validators.add(validator)
    }

    override fun validate(eObject: EObject, diagnostics: DiagnosticChain, context: Map<Any, Any>): Boolean {
        return validators.stream()
                .map { eValidator: EValidator -> eValidator.validate(eObject, diagnostics, context) }
                .reduce { r1: Boolean, r2: Boolean -> r1 && r2 }
                .orElse(true)
    }

    override fun validate(eClass: EClass, eObject: EObject, diagnostics: DiagnosticChain, context: Map<Any, Any>): Boolean {
        return validators.stream()
                .map { eValidator: EValidator -> eValidator.validate(eClass, eObject, diagnostics, context) }
                .reduce { r1: Boolean, r2: Boolean -> r1 && r2 }
                .orElse(true)
    }

    override fun validate(eDataType: EDataType, value: Any, diagnostics: DiagnosticChain, context: Map<Any, Any>): Boolean {
        return validators.stream()
                .map { eValidator: EValidator -> eValidator.validate(eDataType, value, diagnostics, context) }
                .reduce { r1: Boolean, r2: Boolean -> r1 && r2 }
                .orElse(true)
    }
}
internal abstract class AbstractRamlValidator : EValidator, DiagnosticsCreator {
    override fun validate(eObject: EObject, diagnostics: DiagnosticChain, context: Map<Any, Any>): Boolean {
        return validate(eObject.eClass(), eObject, diagnostics, context)
    }

    override fun validate(eDataType: EDataType, value: Any,
                          diagnostics: DiagnosticChain, context: Map<Any, Any>): Boolean {
        return true
    }

    /**
     * Extracts the name from the given proxy (proxy.eIsProxy() == true)
     * @param proxy the proxy EObject
     * @return the name extracted from the proxy or null
     */
    protected fun getNameFromProxy(proxy: EObject): String? {
        val uriFragment = (proxy as InternalEObject).eProxyURI().fragment()
        val path = uriFragment.split("/").toTypedArray()
        return if (path.size == 3) {
            path[2]
        } else null
    }
}

internal class PropertyValidator : AbstractRamlValidator() {
    override fun validate(eClass: EClass, eObject: EObject, diagnostics: DiagnosticChain, context: Map<Any, Any>): Boolean {
        val validationErrors: List<Diagnostic?>? = PropertyValidatingVisitor().doSwitch(eObject)
        validationErrors!!.forEach(Consumer { diagnostic: Diagnostic? -> diagnostics.add(diagnostic) })
        return validationErrors.isEmpty()
    }

    private inner class PropertyValidatingVisitor : TypesSwitch<List<Diagnostic>>() {
        override fun defaultCase(`object`: EObject): List<Diagnostic> {
            return emptyList()
        }

        override fun caseProperty(property: Property?): List<Diagnostic> {
            return arrayMustBePlural(property)
        }

        private fun arrayMustBePlural(property: Property?): List<Diagnostic> {
            val validationErrors: MutableList<Diagnostic> = ArrayList()
            if (property == null)
                return validationErrors;

            val propType = property.type;
            when (propType) {
                is ArrayType -> if (!propType.items.isScalar() && property.name.singularize() == property.name)
                    validationErrors.add(error(property, "${(property.eContainer() as ObjectType).name}: ${property.name} is not pluralized"))
            }
            return validationErrors
        }
    }
}

@CommandLine.Command(name = "validate", description = ["Allows to validate according to API design guidelines"])
class ValidationSubcommand: Callable<Int> {
    @CommandLine.Parameters(index = "0", description = ["Api file location"])
    lateinit var ramlFileLocation: Path

    override fun call(): Int {

        val fileURI = URI.createURI(ramlFileLocation.toUri().toString())
        val modelResult = RamlModelBuilder().buildApi(fileURI)

        val result = RamlValidationSetup.diag().validate(modelResult.rootObject)

        if (result.children.size > 0) {
            val res = result.children.map { it.message }.joinToString( "\n" );
            InternalLogger.error("Error(s) found validating ${fileURI.toFileString()}:\n$res")
            return 1
        }
        InternalLogger.info("Specification at ${fileURI.toFileString()} is valid.")
        return 0
    }
}
