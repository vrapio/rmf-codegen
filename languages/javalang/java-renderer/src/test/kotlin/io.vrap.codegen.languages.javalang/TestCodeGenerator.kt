package io.vrap.codegen.languages.javalang


import io.vrap.codegen.languages.java.base.JavaBaseTypes
import io.vrap.codegen.languages.javalang.client.SpringClientModule
import io.vrap.codegen.languages.javalang.dsl.GroovyDslModule
import io.vrap.codegen.languages.javalang.model.JavaModelModule
import io.vrap.codegen.languages.javalang.plantuml.PlantUmlModule
import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.ApiProvider
import io.vrap.rmf.codegen.di.GeneratorComponent
import io.vrap.rmf.codegen.di.GeneratorModule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths


class TestCodeGenerator {

    companion object {
        private val userProvidedPath = System.getenv("TEST_RAML_FILE")
        private val apiPath : Path = Paths.get(if (userProvidedPath == null) "../../../api-spec/api.raml" else userProvidedPath)
        val apiProvider: ApiProvider = ApiProvider(apiPath)
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.test")
    }


    @Test
    fun generateJavaModels() {
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.importer")
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, JavaModelModule())
        generatorComponent.generateFiles()
    }
    
    
    @Test
    fun generateGroovyDsl() {
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.importer")
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, GroovyDslModule())
        generatorComponent.generateFiles()
    }

    @Test
    fun generateSpringClient() {
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.importer")
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, SpringClientModule())
        generatorComponent.generateFiles()
    }

    @Test
    fun generatePlantUmlDiagram() {
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.importer")
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, PlantUmlModule())
        generatorComponent.generateFiles()
    }
    
    private fun cleanFolder(path: String) {
        Paths.get(path).toFile().deleteRecursively()
    }
}