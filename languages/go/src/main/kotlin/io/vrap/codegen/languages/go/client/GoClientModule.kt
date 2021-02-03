/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.client

import io.vrap.rmf.codegen.di.GeneratorModule
import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.rendring.*

object GoClientModule : Module {

    override fun configure(generatorModule: GeneratorModule) = setOf<CodeGenerator> (
        ResourceGenerator(
            setOf(
                RequestBuilder(generatorModule.clientConstants(), generatorModule.provideRamlModel(), generatorModule.vrapTypeProvider(), generatorModule.providePackageName())
            ),
            generatorModule.allResources()
        ),
        MethodGenerator(
            setOf(
                GoMethodRenderer(generatorModule.clientConstants(), generatorModule.vrapTypeProvider(), generatorModule.providePackageName())
            ),
            generatorModule.allResourceMethods()
        ),
        FileGenerator(
            setOf(
                ClientFileProducer(generatorModule.clientConstants(), generatorModule.provideRamlModel(), generatorModule.providePackageName())
            )
        )
    )

    private fun GeneratorModule.clientConstants() =
        ClientConstants(this.provideSharedPackageName(), this.provideClientPackageName(), this.providePackageName())
}
