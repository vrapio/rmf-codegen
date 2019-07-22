package io.vrap.codegen.languages.typescript.model

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.rendring.FileProducer

class TypeScriptModelModule : AbstractModule() {
    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        objectTypeBinder.addBinding().to(TypeScriptModuleRenderer::class.java)
    }
}
