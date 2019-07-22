package io.vrap.codegen.languages.php.model;

import com.google.inject.Inject
import com.google.inject.name.Named
import io.vrap.codegen.languages.php.PhpBaseTypes
import io.vrap.codegen.languages.php.PhpSubTemplates
import io.vrap.codegen.languages.php.extensions.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.types.util.TypesSwitch
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.eclipse.emf.ecore.EObject
import java.util.*

class PhpObjectTypeRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : ObjectTypeExtensions, EObjectTypeExtensions, ObjectTypeRenderer {

    @Inject
    @BasePackageName
    lateinit var packagePrefix:String

    override fun render(type: ObjectType): TemplateFile {

        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        val content = """
            |<?php
            |${PhpSubTemplates.generatorInfo}
            |namespace ${vrapType.namespaceName().escapeAll()};
            |
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Base\\JsonObject;
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Base\\MapperFactory;
            |<<${type.imports()}>>
            |
            |final class ${vrapType.simpleClassName}Model extends JsonObject implements ${vrapType.simpleClassName}
            |{
            |    ${if (type.discriminator != null || type.discriminatorValue != null) {"const DISCRIMINATOR_VALUE = '${type.discriminatorValue ?: ""}';"} else ""}
            |    <<${type.constructor()}>>
            |
            |    <<${type.toBeanFields()}>>
            |
            |    <<${type.getters()}>>
            |
            |    <<${type.serializer()}>>
            |    
            |    <<${type.discriminatorClasses()}>>
            |    <<${type.discriminatorResolver()}>>
            |}
        """.trimMargin().keepIndentation("<<", ">>").forcedLiteralEscape()


        return TemplateFile(
                relativePath = "src/" + vrapType.fullClassName().replace(packagePrefix.toNamespaceName(), "").replace("\\", "/") + "Model.php",
                content = content
        )
    }


    fun ObjectType.constructor(): String {
        return if (this.discriminator != null)
            """
            |/**
            | * @param ?object $!data
            | */
            |public function __construct(object $!data = null)
            |{
            |    parent::__construct($!data);
            |    /** @var string $!this->${this.discriminator} */
            |    $!this->${this.discriminator} = static::DISCRIMINATOR_VALUE;
            |}
            """.trimMargin()
        else
            ""
    }

    fun ObjectType.imports() = this.getImports(this.allProperties).map { "use ${it.escapeAll()};" }
            .plus(this.getPropertyImports(this.allProperties).map { "use ${it.escapeAll()};" })
            .plus(this.getObjectImports(this.allProperties).map { "use ${it.escapeAll()}Model;" })
            .distinct()
            .sorted()
            .joinToString(separator = "\n")

    fun Property.toPhpField(): String {

        return """
            |/**
            | * @var ?${if (this.type.toVrapType().simpleName() != "stdClass") this.type.toVrapType().simpleName() else "JsonObject" }
            | */
            |protected $${if (this.isPatternProperty()) "values" else this.name};
        """.trimMargin();
    }

    fun Property.toPhpConstantName(): String {
        return "FIELD_${if (this.isPatternProperty()) "VALUES" else StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this.name)}"
    }

    fun Property.toPhpConstant(): String {

        return """
            |const ${this.toPhpConstantName()} = '${if (this.isPatternProperty()) "values" else this.name}';
        """.trimMargin()
    }

    fun ObjectType.toBeanConstant() = this.properties
//            .filter { it.name != this.discriminator }
            .map { it.toPhpConstant() }.joinToString(separator = "\n")

    fun ObjectType.toBeanFields() = this.allProperties
//            .filter { it.name != this.discriminator }
            .map { it.toPhpField() }.joinToString(separator = "\n\n")

    fun ObjectType.setters() = this.properties
            //Filter the discriminators because they don't make much sense the generated bean
            .filter { it.name != this.discriminator }
            .map { it.setter() }
            .joinToString(separator = "\n\n")


    fun ObjectType.getters() = this.allProperties
            //Filter the discriminators because they don't make much sense the generated bean
//            .filter { it.name != this.discriminator }
            .map { it.getter() }
            .joinToString(separator = "\n\n")

    fun ObjectType.serializer(): String {
        val dtProperties = this.allProperties
            .filter { it.type is DateTimeOnlyType || it.type is DateTimeType || it.type is DateOnlyType || it.type is TimeOnlyType }

        if (dtProperties.isNotEmpty()) {
            return """
            |public function jsonSerialize(): array {
            |    $!data = parent::jsonSerialize();
            |    <<${dtProperties.map { it.serializer() }.joinToString(separator = "\n\n")}>>
            |    return $!data;
            |}
        """.trimMargin()
        } else {
            return ""
        }
    }

    fun Property.isPatternProperty() = this.name.startsWith("/") && this.name.endsWith("/")

    fun Property.setter(): String {
        return if (this.isPatternProperty()) {
            """
            |@JsonAnySetter
            |public void setValue(String key, ${this.type.toVrapType().simpleName()} value)
            |{
            |    if (values == null) {
            |        values = new HashMap<>();
            |    }
            |    values.put(key, value);
            |}
            """.trimMargin()
        } else {
            """
            |public void set${this.name.capitalize()}(final ${this.type.toVrapType().simpleName()} ${this.name})
            |{
            |   this.${this.name} = ${this.name};
            |}
            """.trimMargin()
        }
    }

    fun Property.getter(): String {
        return if (this.isPatternProperty()) {

            """
                |/**
                | ${this.type.toPhpComment()}
                | * @return ?${if (this.type.toVrapType().simpleName() != "stdClass") this.type.toVrapType().simpleName() else "JsonObject" }
                | */
                |public function values()
                |{
                |    return $!this->values;
                |}
            """.trimMargin()
        } else {
            """
                |/**
                |
                | ${this.type.toPhpComment()}
                | * @return ?${if (this.type.toVrapType().simpleName() != "stdClass") this.type.toVrapType().simpleName() else "JsonObject" }
                | */
                |public function get${this.name.capitalize()}()
                |{
                |   if (is_null($!this->${this.name})) {
                |       <<${this.mapper()}>>
                |   }
                |   return $!this->${this.name};
                |}
        """.trimMargin()
        }
    }


    fun Property.validationAnnotations(): String {
        val validationAnnotations = ArrayList<String>()
        if (this.required != null && this.required!!) {
            validationAnnotations.add("@NotNull")
        }
        if (CascadeValidationCheck.doSwitch(this.type)) {
            validationAnnotations.add("@Valid")
        }
        return validationAnnotations.joinToString(separator = "\n")
    }

    fun Property.serializer(): String {
        val type = this.type
        val defineObject = this.eContainer().toVrapType();
        val indexName = "${defineObject.simpleName()}::${this.toPhpConstantName()}"
        return when(type) {
            is DateTimeType -> """
                |if (isset($!data[$indexName]) && $!data[$indexName] instanceof \DateTimeImmutable) {
                |   $!data[$indexName] = $!data[$indexName]->setTimeZone(new \DateTimeZone('UTC'))->format('c');
                |}
            """.trimMargin()
            is DateOnlyType -> """
                |if (isset($!data[$indexName]) && $!data[$indexName] instanceof \DateTimeImmutable) {
                |   $!data[$indexName] = $!data[$indexName]->format('Y-m-d');
                |}
            """.trimMargin()
            is TimeOnlyType -> """
                |if (isset($!data[$indexName]) && $!data[$indexName] instanceof \DateTimeImmutable) {
                |   $!data[$indexName] = $!data[$indexName]->format('H:i:s.u');
                |}
            """.trimMargin()
            is DateTimeOnlyType -> """
                |if (isset($!data[$indexName]) && $!data[$indexName] instanceof \DateTimeImmutable) {
                |   $!data[$indexName] = $!data[$indexName]->setTimeZone(new \DateTimeZone('UTC'))->format('c');
                |}
            """.trimMargin()
            else -> return ""
        }.escapeAll()
    }


    fun Property.mapper():String {
        val type = this.type
        val defineObject = this.eContainer().toVrapType();
        return when(type) {
            is ObjectType ->
                if (type.toVrapType().isScalar()) {
                    """
                        |/** @psalm-var ?\\stdClass $!data */
                        |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                        |if (is_null($!data)) {
                        |    return null;
                        |}
                        |$!this->${this.name} = new JsonObject($!data);
                    """.trimMargin()
                } else {
                    """
                        |/** @psalm-var ?\\stdClass $!data */
                        |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                        |if (is_null($!data)) {
                        |    return null;
                        |}
                        |${if (type.discriminator.isNullOrBlank()) "" else "$!className = ${type.toVrapType().simpleName()}Model::resolveDiscriminatorClass($!data);"}
                        |$!this->${this.name} = new ${if (type.discriminator.isNullOrBlank()) "${this.type.toVrapType().simpleName()}Model" else "$!className"}($!data);
                    """.trimMargin()
                }
            is ArrayType ->
                if (type.toVrapType().isScalar()) {
                    """
                        |/** @psalm-var ?array<int, mixed> $!data */
                        |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                        |if (is_null($!data)) {
                        |    return null;
                        |}
                        |$!this->${this.name} = $!data;
                    """.trimMargin()
                } else {
                    """
                    |/** @psalm-var ?array<int, object> $!data */
                    |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                    |if (is_null($!data)) {
                    |    return null;
                    |}
                    |$!this->${this.name} = ${if (type.items?.isScalar() != true) "new ${this.type.toVrapType().simpleName()}($!data)" else "$!data"};
                    """.trimMargin()
                }
            else ->
                when (type.toVrapType()) {
                    is VrapEnumType -> """
                       |/** @psalm-var ?string $!data */
                       |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                       |if (is_null($!data)) {
                       |    return null;
                       |}
                       |$!this->${this.name} = (string)$!data;
                    """.trimMargin()
                    PhpBaseTypes.integerType ->  """
                       |/** @psalm-var ?int $!data */
                       |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                       |if (is_null($!data)) {
                       |    return null;
                       |}
                       |$!this->${this.name} = (int)$!data;
                    """.trimMargin()
                    PhpBaseTypes.stringType ->  """
                       |/** @psalm-var ?string $!data */
                       |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                       |if (is_null($!data)) {
                       |    return null;
                       |}
                       |$!this->${this.name} = (string)$!data;
                    """.trimMargin()
                    PhpBaseTypes.doubleType ->  """
                       |/** @psalm-var ?float $!data */
                       |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                       |if (is_null($!data)) {
                       |    return null;
                       |}
                       |$!this->${this.name} = (float)$!data;
                    """.trimMargin()
                    PhpBaseTypes.booleanType ->  """
                       |/** @psalm-var ?bool $!data */
                       |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                       |if (is_null($!data)) {
                       |    return null;
                       |}
                       |$!this->${this.name} = (bool)$!data;
                    """.trimMargin()
                    PhpBaseTypes.dateTimeType ->
                        """
                           |/** @psalm-var ?string $!data */
                           |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                           |if (is_null($!data)) {
                           |    return null;
                           |}
                           |$!data = DateTimeImmutable::createFromFormat(MapperFactory::DATETIME_FORMAT, $!data);
                           |if ($!data === false) {
                           |    return null;
                           |}
                           |$!this->${this.name} = $!data;
                        """.trimMargin()
                    else ->
                        """
                            |/** @psalm-var ?\\stdClass $!data */
                            |$!data = $!this->get(${defineObject.simpleName()}::${this.toPhpConstantName()});
                            |if (is_null($!data)) {
                            |    return null;
                            |}
                            |$!this->${this.name} = new JsonObject($!data);
                        """.trimMargin()
                }
        }
    }

    private object CascadeValidationCheck : TypesSwitch<Boolean>() {
        override fun defaultCase(`object`: EObject?): Boolean? {
            return false
        }

        override fun caseObjectType(objectType: ObjectType?): Boolean? {
            return true
        }

        override fun caseArrayType(arrayType: ArrayType): Boolean? {
            return if (arrayType.items != null) {
                doSwitch(arrayType.items)
            } else {
                false
            }
        }
    }


    private fun ObjectType.discriminatorClasses(): String {
        if (this.discriminator.isNullOrBlank()) {
            return ""
        }
        return """
                |/** @var array<string, class-string<${this.toVrapType().simpleName()}> > */
                |private static $!discriminatorClasses = [
                |   <<${this.namedSubTypes().filterIsInstance<ObjectType>().map { "'${it.discriminatorValue}' => ${it.toVrapType().simpleName()}Model::class," }.joinToString(separator = "\n")}>>
                |];
            """.trimMargin()
    }

    private fun ObjectType.discriminatorResolver(): String {
        if (this.discriminator.isNullOrBlank()) {
            return ""
        }
        return """
            |/**
            | * @psalm-return class-string<${this.toVrapType().simpleName()}>
            | */
            |public static function resolveDiscriminatorClass(object $!value): string
            |{
            |   $!fieldName = ${this.toVrapType().simpleName()}::DISCRIMINATOR_FIELD;
            |   if (isset($!value->$!fieldName)) {
            |       /** @var string $!discriminatorValue */
            |       $!discriminatorValue = $!value->$!fieldName;
            |       if (isset(static::$!discriminatorClasses[$!discriminatorValue])) {
            |            return static::$!discriminatorClasses[$!discriminatorValue];
            |       }
            |   }
            |   /** @psalm-var class-string<${this.toVrapType().simpleName()}> */
            |   $!type = ${this.toVrapType().simpleName()}Model::class;
            |   return $!type;
            |}
        """.trimMargin()
    }
}

