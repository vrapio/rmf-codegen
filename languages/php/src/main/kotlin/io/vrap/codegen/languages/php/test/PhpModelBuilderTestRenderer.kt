package io.vrap.codegen.languages.php.test

import com.google.inject.Inject
import io.vrap.codegen.languages.extensions.discriminatorProperty
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.php.AbstractRequestBuilder
import io.vrap.codegen.languages.php.PhpSubTemplates
import io.vrap.codegen.languages.php.extensions.*
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.ResourceRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepAngleIndent
import io.vrap.rmf.codegen.types.VrapArrayType
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.ArrayType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property

class PhpModelBuilderTestRenderer @Inject constructor(api: Api, vrapTypeProvider: VrapTypeProvider) : ObjectTypeRenderer, AbstractRequestBuilder(api, vrapTypeProvider), ObjectTypeExtensions {

    override fun render(type: ObjectType): TemplateFile {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
        val discriminator = type.discriminatorProperty()

        val builderTestPackageName = vrapType.namespaceName().replace(basePackagePrefix.toNamespaceName(), "${basePackagePrefix.toNamespaceName()}\\Test")
        val properties = type.allProperties
                .filter { property -> property != discriminator }
                .filterNot { it.isPatternProperty() }
        val content = """
            |<?php
            |${PhpSubTemplates.generatorInfo}
            |namespace ${builderTestPackageName.escapeAll()};
            |
            |use PHPUnit\\Framework\\TestCase;
            |use ${vrapType.namespaceName().escapeAll()}\\${vrapType.simpleBuilderName().escapeAll()};
            |use ${vrapType.fullClassName().escapeAll()};
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\JsonObject;
            |<<${type.imports()}>>
            |/**
            | */
            |class ${vrapType.simpleBuilderName()}Test extends TestCase
            |{
            |    ${if (properties.size > 0) """/**
            |     * @dataProvider getBuilders()
            |     */
            |    public function testBuilder(callable $!builderFunction)
            |    {
            |        $!builder = $!builderFunction(new ${vrapType.simpleBuilderName()}());
            |        $!this->assertInstanceOf(${vrapType.simpleClassName}::class, $!builder->build());
            |    }""".trimMargin() else ""}
            |
            |    public function getBuilders()
            |    {
            |        return [
            |           <<${properties
                            .filter { property -> property != discriminator }
                            .filterNot { it.isPatternProperty() }.joinToString(",\n") { p -> p.propertyBuilder(type) }}>>
            |        ];
            |    }
            |}
        """.trimMargin().keepAngleIndent().forcedLiteralEscape()

        val relativeTypeNamespace = builderTestPackageName.replace("${basePackagePrefix.toNamespaceName()}\\Test", "").replace("\\", "/")
        val relativePath = "test/unit/" + relativeTypeNamespace + "/" + vrapType.simpleBuilderName() + "Test.php"

        return TemplateFile(
                relativePath = relativePath,
                content = content
        )
    }

    fun ObjectType.imports() = this.getImports(this.allProperties).map { "use ${it.escapeAll()};" }
            .plus(this.getImports(this.allProperties.filter { !it.type.isScalar() && !(it.type is ArrayType) && !(it.type.toVrapType().simpleName() == "stdClass") }).map { "use ${it.escapeAll()}Builder;" })
            .distinct()
            .sorted()
            .joinToString(separator = "\n")

    private fun Property.propertyBuilder(type: ObjectType): String {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
        val propertyVrapType = this.type.toVrapType()
        val value = when (propertyVrapType) {
            is VrapObjectType -> "$!this->createMock(${if (propertyVrapType.fullClassName() == "\\stdClass") "JsonObject" else propertyVrapType.simpleName()}::class)"
            is VrapArrayType -> if (propertyVrapType.isScalar()) "[]" else "$!this->createMock(${propertyVrapType.simpleName()}::class)"
            is VrapScalarType -> "(${propertyVrapType.scalarType})20"
            else -> "\"foo\""
        }
        return """
            |"${this.name}" => [
            |    function(${vrapType.simpleBuilderName()} $!builder) {
            |        $!value = ${value.escapeAll()};
            |        $!builder->with${this.name.capitalize()}($!value);
            |        $!t = $!builder->build();
            |        $!this->assertSame($!value, $!t->get${this.name.capitalize()}());
            |        
            |        return $!builder;
            |    }
            |]
        """.trimMargin()
    }
}
