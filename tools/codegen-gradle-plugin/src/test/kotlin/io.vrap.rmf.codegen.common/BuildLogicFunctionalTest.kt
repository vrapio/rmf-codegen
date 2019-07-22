package io.vrap.rmf.codegen.common

import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import java.nio.file.Paths

class BuildLogicFunctionalTest {

    @get:Rule
    val testProjectDir = TemporaryFolder()

    private lateinit var buildFile: File
    private val logger = LoggerFactory.getLogger(javaClass)

    @Before
    fun setup() {
        buildFile = testProjectDir.newFile("build.gradle")
    }

    @Test
    fun testGenerateEntitiesTask() {

        val userProvidedPath = System.getenv("TEST_RAML_FILE")
        val apiFile : Path = Paths.get(if (userProvidedPath == null) "../../api-spec/api.raml" else userProvidedPath).toAbsolutePath()

        Assertions.assertThat(apiFile).exists()

        val buildFileContent = """
        |plugins {
        |    id 'io.vrap.rmf.codegen-plugin'
        |}
        |
        |RamlGenerator {
        |    apiSpec {
        |      uri = file('$apiFile')
        |      targets {
        |          importApi {
        |               path = file('import-api/build/generated-classes')
        |               models_package = 'com.commercetools.models'
        |               client_package = 'com.commercetools.client'
        |               target = 'typescriptModel'
        |          }
        |          importApi_groovy_dsl {
        |               path = file('import-api/build/generated-classes')
        |               models_package = 'com.commercetools.models'
        |               client_package = 'com.commercetools.client'
        |               target = 'groovyDsl'
        |          }
        |          importStorageService {
        |               path = file('import-api2/build/generated-classes')
        |               models_package = 'com.commercetools.models2'
        |               client_package = 'com.commercetools.client2'
        |               target = 'javaModel'
        |          }
        |          importResolveableModels {
        |               path = file('import-api-resolveable-model/build/generated-classes')
        |               base_package = 'com.commercetools.importapi.resolveable'
        |               target = 'javaModel'
        |               customTypeMapping = [
        |                       'CategoryKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'CartDiscountKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'ResolvableReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'ChannelKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'CustomerKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'CustomerGroupKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'PriceKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'ProductKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'ProductTypeKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'ProductVariantKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'ShippingMethodKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'StateKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference',
        |                       'TaxCategoryKeyReference': 'com.commercetools.importstorage.resolvable.models.common.ResolvableReference'
        |               ]
        |               typeNamePrefix = 'Resol'
        |          }
        |          importStorageService {
        |               path = file('import-api2/build/generated-classes')
        |               models_package = 'com.commercetools.models2'
        |               client_package = 'com.commercetools.client2'
        |               target = 'joiValidator'
        |          }
        |          springClient {
        |               path = file('import-api3/build/generated-classes')
        |               models_package = 'com.commercetools.models2'
        |               client_package = 'com.commercetools.client2'
        |               target = 'javaSpringClient'
        |          }
        |       }
        |    }
        |}
        """.trimMargin()

        writeFile(buildFile, buildFileContent)
        val result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("generateRamlStub", "--stacktrace")
                .withPluginClasspath()
                .build()

        logger.warn(result.output)

        Assertions.assertThat(result.task(":generateRamlStub")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        Assertions.assertThat(testProjectDir.root.list { _, name -> name == "import-api" }).isNotEmpty
    }

    private fun writeFile(destination: File?, content: String) {
        var output: BufferedWriter? = null
        try {
            output = BufferedWriter(FileWriter(destination!!))
            output.write(content)
        } finally {
            output?.close()
        }
    }
}
