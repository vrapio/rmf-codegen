
package io.vrap.codegen.languages.php.test

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.codegen.languages.php.model.PhpFileProducer
import io.vrap.codegen.languages.php.model.PhpInterfaceObjectTypeRenderer
import io.vrap.codegen.languages.php.test.PhpRequestTestRenderer
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.MethodRenderer
import io.vrap.rmf.codegen.rendring.ResourceRenderer

class PhpTestModule: AbstractModule() {

    override fun configure() {
        val resourceBinder = Multibinder.newSetBinder(binder(), ResourceRenderer::class.java)
        resourceBinder.addBinding().to(PhpRequestTestRenderer::class.java)

        val objectTypeBinder = Multibinder.newSetBinder(binder(), ObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(PhpModelBuilderTestRenderer::class.java)

        val fileBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        fileBinder.addBinding().to(PhpTestRenderer::class.java)
    }
}
