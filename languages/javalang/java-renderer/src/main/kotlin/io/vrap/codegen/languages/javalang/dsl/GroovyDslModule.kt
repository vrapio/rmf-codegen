package io.vrap.codegen.languages.javalang.dsl

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer

class GroovyDslModule: AbstractModule() {
    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), ObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(GroovyDslRenderer::class.java)
    }
}