package io.vrap.rmf.codegen.di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import io.vrap.rmf.codegen.common.generator.core.ResourceCollection
import io.vrap.rmf.codegen.io.DataSink
import io.vrap.rmf.codegen.io.FileDataSink
import io.vrap.rmf.codegen.types.LanguageBaseTypes
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.elements.NamedElement
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.StringType
import io.vrap.rmf.raml.model.types.util.TypesSwitch
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.ComposedSwitch
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path

@SuppressWarnings("unused")
class GeneratorModule constructor(
        private val apiProvider: ApiProvider,
        @get:Provides @get:Singleton val generatorConfig: io.vrap.rmf.codegen.CodeGeneratorConfig,
        @get:Provides @get:Singleton val languageBaseTypes: LanguageBaseTypes,
        private val defaultPackage: String = "io.vrap.rmf"


) : AbstractModule() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(io.vrap.rmf.codegen.di.GeneratorModule::class.java)
    }

    private val filterSwitch = UserProvidedResourcesFilterSwitch()


    @Provides
    @Singleton
    @DefaultPackage
    fun defaultPackage(): String = defaultPackage


    @Provides
    @Singleton
    @OutputFolder
    fun outpuFolder(): Path = generatorConfig.outputFolder

    @Provides
    @Singleton
    fun dataSink(@OutputFolder path: Path): DataSink = FileDataSink(path)

    @Provides
    @Singleton
    fun customTypeMapping(): Map<String, VrapType> = generatorConfig.customTypeMapping

    /**
     * The prefix to add to the type names of all generated model types.
     */
    @Provides
    @Singleton
    @TypeNamePrefix
    fun typePrefix() = generatorConfig.typeNamePrefix

    @Provides
    @Singleton
    @BasePackageName
    fun providePackageName(
            @DefaultPackage defaultPackage: String,
            api: Api
    ): String {
        if (generatorConfig.basePackageName == null && api.baseUri == null) {
            io.vrap.rmf.codegen.di.GeneratorModule.Companion.LOGGER.warn("Could not find proper package name configuration. Using default $defaultPackage")
            return defaultPackage
        }
        return generatorConfig.basePackageName ?: URI(api.baseUri.expand()).host.split(".").reversed().joinToString(".")
    }

    @Provides
    @Singleton
    @ModelPackageName
    fun provideModelPackageName(@BasePackageName basePackageName: String): String = generatorConfig.modelPackage
            ?: if (basePackageName.isBlank()) "models" else "$basePackageName.models"

    @Provides
    @Singleton
    @ClientPackageName
    fun provideClientPackageName(@BasePackageName basePackageName: String): String = generatorConfig.clientPackage
            ?: if (basePackageName.isBlank()) "client" else "$basePackageName.client"

    @Provides
    @Singleton
    fun provideRamlModel(): Api = apiProvider.api

    @Provides
    @Singleton
    fun allAnyTypes(ramlApi: Api): List<AnyType> {
        val result = mutableListOf<AnyType>()
        ramlApi.types?.forEach { result.add(it) }
        ramlApi.uses?.flatMap { it.library.types }?.forEach { result.add(it) }
        return result.filter { filterSwitch.doSwitch(it) }
    }

    @Provides
    @Singleton
    fun allObjectTypes(anyTypeList: MutableList<AnyType>): List<ObjectType> = anyTypeList.filter { it is ObjectType }.map { it as ObjectType }

    @Provides
    @Singleton
    fun allStringTypes(anyTypeList: MutableList<AnyType>): List<StringType> = anyTypeList.filter { it is StringType }.map { it as StringType }

    @Provides
    @Singleton
    fun allResources(ramlApi: Api): List<Resource> = ramlApi.allContainedResources

    @Provides
    @Singleton
    fun allResourceMethods(ramlApi: Api): List<Method> = ramlApi.allContainedResources.flatMap { it.methods }

    @Provides
    @Singleton
    fun resourceCollection(resources: MutableList<Resource>, vrapTypeProvider: VrapTypeProvider): List<ResourceCollection> {
        return resources.groupBy { (vrapTypeProvider.doSwitch(it) as VrapObjectType).simpleClassName }
                .map { entry: Map.Entry<String, List<Resource>> ->
                    ResourceCollection(vrapTypeProvider.doSwitch(entry.value[0]), entry.value)
                }
                .toList()
    }


    /**
     * This filter is used to filter files that are explicitly provided by the sdk developer
     */
    private inner class UserProvidedResourcesFilterSwitch : ComposedSwitch<Boolean>() {

        init {
            addSwitch(FilterTypeSwitch())
        }

        private inner class FilterTypeSwitch : TypesSwitch<Boolean>() {
            override fun caseNamedElement(`object`: NamedElement): Boolean = generatorConfig.customTypeMapping[`object`.name]?.let { false }
                    ?: true

            override fun caseStringType(stringType: StringType): Boolean = stringType.enum?.isNotEmpty() ?: false
            override fun defaultCase(`object`: EObject?): Boolean? = false
        }
    }

}
