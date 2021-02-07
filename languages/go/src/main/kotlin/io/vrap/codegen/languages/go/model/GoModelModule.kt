/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.model

import io.vrap.rmf.codegen.di.GeneratorModule
import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.rendring.CodeGenerator
import io.vrap.rmf.codegen.rendring.FileGenerator

object GoModelModule : Module {
    override fun configure(generatorModule: GeneratorModule) = setOf<CodeGenerator>(
        FileGenerator(
            setOf(
                GoFileProducer(generatorModule.vrapTypeProvider(), generatorModule.allAnyTypes(), generatorModule.providePackageName())
            )
        )
    )
}
