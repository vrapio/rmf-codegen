package io.vrap.rmf.codegen.cli

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.vrap.codegen.languages.csharp.CsharpBaseTypes
import io.vrap.codegen.languages.csharp.modules.CsharpClientBuilderModule
import io.vrap.codegen.languages.csharp.modules.CsharpModule
import io.vrap.codegen.languages.java.base.JavaBaseTypes
import io.vrap.codegen.languages.javalang.client.builder.module.JavaCompleteModule
import io.vrap.codegen.languages.oas.model.OasBaseTypes
import io.vrap.codegen.languages.oas.model.OasModelModule
import io.vrap.codegen.languages.php.PhpBaseTypes
import io.vrap.codegen.languages.php.base.PhpBaseModule
import io.vrap.codegen.languages.php.model.PhpModelModule
import io.vrap.codegen.languages.php.test.PhpTestModule
import io.vrap.codegen.languages.postman.model.PostmanBaseTypes
import io.vrap.codegen.languages.postman.model.PostmanModelModule
import io.vrap.codegen.languages.ramldoc.model.RamldocBaseTypes
import io.vrap.codegen.languages.ramldoc.model.RamldocModelModule
import io.vrap.codegen.languages.typescript.client.TypescriptClientModule
import io.vrap.codegen.languages.typescript.model.TypeScriptBaseTypes
import io.vrap.codegen.languages.typescript.model.TypescriptModelModule
import io.vrap.codegen.languages.go.model.GoBaseTypes
import io.vrap.codegen.languages.go.model.GoModelModule
import io.vrap.codegen.languages.go.client.GoClientModule
import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.ApiProvider
import io.vrap.rmf.codegen.di.GeneratorComponent
import io.vrap.rmf.codegen.di.GeneratorModule
import io.vrap.rmf.codegen.toSeconds
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis


/** Targets section */
enum class GenerationTarget {
    JAVA_CLIENT,
    TYPESCRIPT_CLIENT,
    PHP_CLIENT,
    PHP_BASE,
    PHP_TEST,
    GO_CLIENT,
    POSTMAN,
    RAML_DOC,
    CSHARP_CLIENT,
    OAS,
}
const val ValidTargets = "JAVA_CLIENT, TYPESCRIPT_CLIENT, CSHARP_CLIENT, PHP_CLIENT, PHP_BASE, PHP_TEST, POSTMAN, RAML_DOC, OAS"

@CommandLine.Command(name = "generate",description = ["Generate source code from a RAML specification."])
class GenerateSubcommand : Callable<Int> {

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["display this help message"])
    var usageHelpRequested = false

    @CommandLine.Option(names = ["-s", "--shared-package"], description = ["The shared package to be used for the generated code."])
    var sharedPackage: String? = null

    @CommandLine.Option(names = ["-b", "--base-package"], description = ["The base package, this package in case the model or client models aren't provided"],defaultValue = "")
    var basePackageName: String? = null

    @CommandLine.Option(names = ["-m", "--model-package"], description =[ "The models package, this will be used as the model package in the generated code."])
    var modelPackageName: String? = null

    @CommandLine.Option(names = ["-c", "--client-package"], description = ["The client package, This will be used as the package for the client stub."])
    var clientPackageName: String? = null

    @CommandLine.Option(names = ["-o", "--output-folder"], description = ["Output folder for generated files."], required = true)
    lateinit var outputFolder: Path

    @CommandLine.Option(names = ["-t", "--target"], description = ["Specifies the code generation target","Valid values: $ValidTargets"], required = true)
    lateinit var target: GenerationTarget

    @CommandLine.Option(names = ["-w", "--watch"], description = ["Watches the files for changes"], required = false)
    var watch: Boolean = false

    @CommandLine.Option(names = ["-v", "--verbose"], description = ["If set, this would move the verbosity level to debug."], required = false)
    var verbose: Boolean = false

    @CommandLine.Parameters(index = "0",description = ["Api file location"])
    lateinit var ramlFileLocation: Path

    override fun call(): Int {
        if(verbose){
            InternalLogger.logLevel = LogLevel.DEBUG
        }
        if(!(Files.exists(ramlFileLocation) && Files.isRegularFile(ramlFileLocation))){
            InternalLogger.error("File '$ramlFileLocation' does not exist, please provide an existing spec path.")
            return 1
        }
        val generatorConfig = CodeGeneratorConfig(
                sharedPackage = sharedPackage,
                basePackageName = basePackageName,
                modelPackage = modelPackageName,
                clientPackage = clientPackageName,
                outputFolder = outputFolder
        )

        val res = safeRun { generate(ramlFileLocation, target, generatorConfig) }
        if (watch) {
            val watchDir = ramlFileLocation.toAbsolutePath().parent

            val source = Observable.create<DirectoryChangeEvent> { emitter ->
                run {
                    val watcher = DirectoryWatcher.builder()
                            .path(watchDir)
                            .listener { event ->
                                when (event.eventType()) {
                                    DirectoryChangeEvent.EventType.CREATE,
                                    DirectoryChangeEvent.EventType.MODIFY,
                                    DirectoryChangeEvent.EventType.DELETE -> {
                                        val json = event.path().toString().endsWith("json")
                                        val raml = event.path().toString().endsWith("raml")
                                        if (json || raml) {
                                            emitter.onNext(event)
                                        }
                                    }
                                    else -> {
                                    }
                                }
                            }
                            .build()
                    watcher.watchAsync()
                }
            }

            source.subscribeOn(Schedulers.io())
                    .throttleLast(1, TimeUnit.SECONDS)
                    .blockingSubscribe(
                            {
                                InternalLogger.debug("Consume ${it.eventType().name.toLowerCase()}: ${it.path()}")
                                safeRun { generate(ramlFileLocation, target, generatorConfig) }
                            },
                            {
                                InternalLogger.error(it)
                            }
                    )
        }
        return res
    }

    private fun generate(ramlFileLocation: Path, target: GenerationTarget, generatorConfig: CodeGeneratorConfig): Int {
        val generateDuration = measureTimeMillis {
            val apiProvider = ApiProvider(ramlFileLocation)
            val generatorComponent: GeneratorComponent = when (target) {
                GenerationTarget.JAVA_CLIENT -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, JavaBaseTypes)
                    GeneratorComponent(generatorModule, JavaCompleteModule)
                }
                GenerationTarget.TYPESCRIPT_CLIENT -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, TypeScriptBaseTypes)
                    GeneratorComponent(generatorModule, TypescriptModelModule, TypescriptClientModule)
                }
                GenerationTarget.PHP_CLIENT -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, PhpBaseTypes)
                    GeneratorComponent(generatorModule, PhpModelModule)
                }
                GenerationTarget.PHP_BASE -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, PhpBaseTypes)
                    GeneratorComponent(generatorModule, PhpBaseModule)
                }
                GenerationTarget.PHP_TEST -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, PhpBaseTypes)
                    GeneratorComponent(generatorModule, PhpTestModule)
                }
                GenerationTarget.CSHARP_CLIENT -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, CsharpBaseTypes)
                    GeneratorComponent(generatorModule, CsharpModule, CsharpClientBuilderModule)
                }
                GenerationTarget.POSTMAN -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, PostmanBaseTypes)
                    GeneratorComponent(generatorModule, PostmanModelModule)
                }
                GenerationTarget.RAML_DOC -> {
                    val ramlConfig = CodeGeneratorConfig(
                            sharedPackage = generatorConfig.sharedPackage,
                            basePackageName = generatorConfig.basePackageName,
                            modelPackage = generatorConfig.modelPackage,
                            clientPackage = generatorConfig.clientPackage,
                            outputFolder = generatorConfig.outputFolder
                    )
                    val generatorModule = GeneratorModule(apiProvider, ramlConfig, RamldocBaseTypes)
                    GeneratorComponent(generatorModule, RamldocModelModule)
                }
                GenerationTarget.OAS -> {
                    val ramlConfig = CodeGeneratorConfig(
                            sharedPackage = generatorConfig.sharedPackage,
                            basePackageName = generatorConfig.basePackageName,
                            modelPackage = generatorConfig.modelPackage,
                            clientPackage = generatorConfig.clientPackage,
                            outputFolder = generatorConfig.outputFolder
                    )
                    val generatorModule = GeneratorModule(apiProvider, ramlConfig, OasBaseTypes)
                    GeneratorComponent(generatorModule, OasModelModule)
                }
                GenerationTarget.GO_CLIENT -> {
                    val generatorModule = GeneratorModule(apiProvider, generatorConfig, GoBaseTypes)
                    GeneratorComponent(generatorModule, GoModelModule, GoClientModule)
                }
                else -> throw Exception()
            }
            generatorComponent.generateFiles()
        }
        InternalLogger.info("Generation took: ${generateDuration.toSeconds(3)}s")
        return 0
    }
}
