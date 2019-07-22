
package io.vrap.codegen.languages.php.model

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.MethodRenderer
import io.vrap.rmf.codegen.rendring.ResourceRenderer

class PhpModelModule: AbstractModule() {

    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), ObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(PhpInterfaceObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(PhpObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(PhpCollectionRenderer::class.java)

        val fileBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        fileBinder.addBinding().to(PhpFileProducer::class.java)

        val methodBinder = Multibinder.newSetBinder(binder(), MethodRenderer::class.java)
        methodBinder.addBinding().to(PhpMethodRenderer::class.java)

        val resourceBinder = Multibinder.newSetBinder(binder(), ResourceRenderer::class.java)
        resourceBinder.addBinding().to(PhpMethodBuilderRenderer::class.java)
    }
}
