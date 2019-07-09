package io.vrap.codegen.languages.java.file.producers

import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation

class JavaStaticFilesProducer : FileProducer {

    override fun produceFiles(): List<TemplateFile> {
        return listOf(produceApiRequestInterface(), produceModelDraftBuilder())
    }

    private fun produceApiRequestInterface() : TemplateFile {
        val content = """
            |package com.commercetools.importer.commands;
            |
            |import javax.annotation.Nullable;
            |import io.sphere.sdk.http.HttpResponse;
            |import io.sphere.sdk.client.HttpRequestIntent;
            |
            |public interface ApiRequest<T> {
            |
            |   @Nullable
            |   T deserialize(final HttpResponse httpResponse);
            |
            |   HttpRequestIntent httpRequestIntent();
            |
            |   default boolean canDeserialize(final HttpResponse httpResponse) {
            |       return httpResponse.hasSuccessResponseCode() && httpResponse.getResponseBody() != null;
            |   }
            |}
        """.escapeAll().trimMargin().keepIndentation()

        return TemplateFile (
            content = content,
            relativePath = "com/commercetools/importer/commands/ApiRequest.java"
        )
    }
    
    private fun produceModelDraftBuilder() : TemplateFile {
        val content = """
            |package com.commercetools.importer.models;
            |
            |import java.util.function.Supplier;
            |
            |public interface Builder<T> extends Supplier<T> {
            |   
            |   T build();
            |   
            |   default T get() {
            |       return build();
            |   }
            |}
            |
        """.escapeAll().trimMargin().keepIndentation()

        return TemplateFile (
                content = content,
                relativePath = "com/commercetools/importer/models/Builder.java"
        )
    }
}