package io.vrap.codegen.languages.typescript.client

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.codegen.languages.typescript.client.files_producers.ApiRootFileProducer
import io.vrap.codegen.languages.typescript.client.files_producers.ClientFileProducer
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.ResourceRenderer

class TypescriptClientModule : AbstractModule() {
    override fun configure() {

        Multibinder.newSetBinder(binder(), ResourceRenderer::class.java).addBinding().to(RequestBuilder::class.java)
        Multibinder.newSetBinder(binder(), FileProducer::class.java).addBinding().to(ApiRootFileProducer::class.java)
        Multibinder.newSetBinder(binder(), FileProducer::class.java).addBinding().to(ClientFileProducer::class.java)

    }
}