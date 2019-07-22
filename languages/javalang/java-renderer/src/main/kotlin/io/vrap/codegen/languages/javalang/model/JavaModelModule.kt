package io.vrap.codegen.languages.javalang.model

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.StringTypeRenderer

class JavaModelModule: AbstractModule() {
    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), ObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(JavaObjectTypeRenderer::class.java)

        val stringTypeBinder = Multibinder.newSetBinder(binder(), StringTypeRenderer::class.java)
        stringTypeBinder.addBinding().to(JavaStringTypeRenderer::class.java)

        val fileBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        //fileBinder.addBinding().to(JavaFileProducer::class.java)
    }
}