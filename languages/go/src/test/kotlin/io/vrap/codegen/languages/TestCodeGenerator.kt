package io.vrap.codegen.languages

import io.vrap.codegen.languages.go.client.GoClientModule
import io.vrap.codegen.languages.go.model.GoBaseTypes
import io.vrap.codegen.languages.go.model.GoModelModule
import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.ApiProvider
import io.vrap.rmf.codegen.di.GeneratorComponent
import io.vrap.rmf.codegen.di.GeneratorModule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class TestPythonCodeGenerator {

    companion object {
        private val userProvidedPath = System.getenv("TEST_RAML_FILE")
        private val apiPath: Path = Paths.get(if (userProvidedPath == null) "../../api-spec/api.raml" else userProvidedPath)
        val apiProvider: ApiProvider = ApiProvider(apiPath)
        val generatorConfig = CodeGeneratorConfig(basePackageName = "")
    }

    @Test
    fun generatPyModels() {
        val generatorModule = GeneratorModule(apiProvider, generatorConfig, GoBaseTypes)
        val generatorComponent = GeneratorComponent(generatorModule, GoModelModule, GoClientModule)
        generatorComponent.generateFiles()
    }

    private fun cleanGenTestFolder() {
        cleanFolder("build/gensrc")
    }

    private fun cleanFolder(path: String) {
        Paths.get(path).toFile().deleteRecursively()
    }
}
