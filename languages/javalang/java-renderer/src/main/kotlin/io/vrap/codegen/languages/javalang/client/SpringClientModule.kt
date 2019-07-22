package io.vrap.codegen.languages.javalang.client

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.rendring.ResourceCollectionRenderer

class SpringClientModule : AbstractModule() {
    override fun configure() {
        val resourceCollectionBinder = Multibinder.newSetBinder(binder(), ResourceCollectionRenderer::class.java)
        resourceCollectionBinder.addBinding().to(SpringClientRenderer::class.java)
    }
}