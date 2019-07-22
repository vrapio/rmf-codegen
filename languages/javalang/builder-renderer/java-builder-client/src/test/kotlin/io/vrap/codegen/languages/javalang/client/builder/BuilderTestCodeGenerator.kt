package io.vrap.codegen.languages.javalang.client.builder

import io.vrap.codegen.languages.java.base.JavaBaseTypes
import io.vrap.codegen.languages.javalang.client.builder.module.JavaCompleteModule

import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.ApiProvider
import io.vrap.rmf.codegen.di.GeneratorComponent
import io.vrap.rmf.codegen.di.GeneratorModule
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.tools.StandardLocation
import javax.tools.ToolProvider

class BuilderTestCodeGenerator {

    companion object {
        private val userProvidedPath = System.getenv("TEST_RAML_FILE")
        private val apiPath : Path = Paths.get(if (userProvidedPath == null) "../api-spec/api.raml" else userProvidedPath)
        val apiProvider: ApiProvider = ApiProvider(apiPath)
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.test")
    }
    
    @Ignore
    @Test
    fun generateJavaModelsWithInterfacesModule() {
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.importer", outputFolder = Paths.get("build/gensrc/java"))
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, JavaCompleteModule())
        generatorComponent.generateFiles()

        File("build/compiled").deleteRecursively()
        File("build/compiled").mkdirs()

        val filesList =  File("build/gensrc")
                .walkTopDown()
                .filter( File::isFile )
                .filter { it.name.endsWith(".java") }
                .toList()

        val compiler = ToolProvider.getSystemJavaCompiler()
        val fileManager = compiler.getStandardFileManager(null, null, null)

        fileManager.use {
            it.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(File("build/compiled")))
            val result = compiler.getTask(null,
                    fileManager, null, null, null,
                    fileManager.getJavaFileObjectsFromFiles(filesList)).call()

            Assert.assertTrue(result)
        }
    }

    @Ignore
    @Test
    fun generateJavaCompleteModule() {
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools", outputFolder = Paths.get("../generated-code/src/main/java"))
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, JavaCompleteModule())
        generatorComponent.generateFiles()
    }

    /**
     * This test method uses code generator to generate interface and a class for simple-type.raml which is a part of the test-api.raml located in the resources
     * folder. After the classes are generated, it checks if they are the same as the ones specified in SimpleType.txt and SimpleTypeImpl.txt.
     */
    @Ignore
    @Test
    fun generateFromCustomRamlAndCompareToAlreadyGeneratedFiles() {
        val testApiProvider = ApiProvider(Paths.get("src/test/resources/java/ramlTestFiles/test-api.raml"))
        val generatorConfig = CodeGeneratorConfig(basePackageName = "com.commercetools.test", outputFolder = Paths.get("build/gensrc/java"))
        val generatorModule = GeneratorModule(testApiProvider, generatorConfig, JavaBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, JavaCompleteModule())
        generatorComponent.generateFiles()

        val generatedSimpleTypeInterface = String(Files.readAllBytes(Paths.get("build/gensrc/java/com/commercetools/test/models/simpleTypes/SimpleType.java")))
        val generatedSimleTypeClass = String(Files.readAllBytes(Paths.get("build/gensrc/java/com/commercetools/test/models/simpleTypes/SimpleTypeImpl.java")))

        val correctSimpleTypeInterface = String(Files.readAllBytes(Paths.get("src/test/resources/java/ramlTestFiles/generated-files/SimpleType.txt")))
        val correctSimpleTypeClass = String(Files.readAllBytes(Paths.get("src/test/resources/java/ramlTestFiles/generated-files/SimpleTypeImpl.txt")))

        Assert.assertEquals(correctSimpleTypeClass, generatedSimleTypeClass)
        Assert.assertEquals(correctSimpleTypeInterface, generatedSimpleTypeInterface)
    }
}