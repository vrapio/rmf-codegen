package io.vrap.codegen.languages.typescript.joi

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.rendring.FileProducer

object JoiModule : AbstractModule() {
    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        objectTypeBinder.addBinding().to(JoiValidatorModuleRenderer::class.java)
    }
}
