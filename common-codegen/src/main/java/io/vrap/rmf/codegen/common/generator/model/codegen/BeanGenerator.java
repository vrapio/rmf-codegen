package io.vrap.rmf.codegen.common.generator.model.codegen;

import com.fasterxml.jackson.annotation.*;
import com.squareup.javapoet.*;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vrap.rmf.codegen.common.generator.core.CodeGenerator;
import io.vrap.rmf.codegen.common.generator.core.GenerationResult;
import io.vrap.rmf.codegen.common.generator.core.GeneratorConfig;
import io.vrap.rmf.codegen.common.generator.util.CodeGeneratorUtil;
import io.vrap.rmf.raml.model.modules.Api;
import io.vrap.rmf.raml.model.types.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.Generated;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vrap.rmf.codegen.common.generator.util.CodeGeneratorUtil.*;

public class BeanGenerator extends CodeGenerator {


    public BeanGenerator(GeneratorConfig generatorConfig, Api api) {
        super(generatorConfig, api);
    }



    @Override
    public Single<GenerationResult> generateStub() {

        final Single<GenerationResult> generationResult = getRamlObjects()
                .flatMapMaybe(this::transformToJavaFile)
                .concatWith(Single.just(getJavaFileForBase()))
                .doOnNext(javaFile -> javaFile.writeTo(getOutputFolder()))
                .map(JavaFile::toJavaFileObject)
                .map(javaFile -> getPath(javaFile, getOutputFolder()))
                .toList()
                .map(GenerationResult::of);

        return generationResult;
    }

    @Override
    public String getPackagePrefix() {
        return super.getPackagePrefix()+".models";
    }

    public Maybe<JavaFile> transformToJavaFile(final AnyType object) {
        return buildTypeSpec(object).map(typeSpec -> JavaFile.builder(getObjectPackage(getPackagePrefix(), object), typeSpec).build());

    }

    private JavaFile getJavaFileForBase() {
        return JavaFile.builder(getBaseClassName().packageName(), getBaseClassTypeSpec()).build();
    }

    private Maybe<TypeSpec> buildTypeSpec(final AnyType type) {

        final TypeSpec typeSpec;
        if (getCustomTypeMapping().get(type.getName()) != null) {
            return Maybe.empty();
        } else if (type instanceof ObjectType) {
            typeSpec = buildTypeSpecForObjectType(((ObjectType) type));
        } else if (type instanceof StringType) {
            typeSpec = buildTypeSpecForStringType((StringType) type);
        } else {
            return Maybe.error(new RuntimeException("unhandled type " + type));
        }

        return Maybe.just(typeSpec);
    }

    private TypeSpec buildTypeSpecForStringType(final StringType stringType) {
        if (!stringType.getEnum().isEmpty()) {
            TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(getClassName(getPackagePrefix(), stringType))
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value","$S",getClass().getCanonicalName() ).build());
            stringType.getEnum()
                    .stream()
                    .map(StringInstance.class::cast)
                    .map(StringInstance::getValue)
                    .forEach(value-> addConstantToEnumConstant(enumBuilder,value));
            enumBuilder.addJavadoc(getJavaDocProcessor().markDownToJavaDoc(stringType));
            return enumBuilder.build();
        } else {
            TypeSpec typeSpec = TypeSpec.classBuilder(getClassName(getPackagePrefix(), stringType))
                    .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value","$S",getClass().getCanonicalName() ).build())
                    .addModifiers(Modifier.PUBLIC)
                    .superclass(getBaseClassName())
                    .addJavadoc(getJavaDocProcessor().markDownToJavaDoc(stringType))
                    .build();
            return typeSpec;
        }
    }


    private void addConstantToEnumConstant(TypeSpec.Builder enumBuilder, final String enumValue) {

        TypeSpec anootationSpec = TypeSpec.anonymousClassBuilder("")
                .addAnnotation(AnnotationSpec.builder(JsonProperty.class).addMember("value", "$S",enumValue).build())
                .build();

        enumBuilder.addEnumConstant(CodeGeneratorUtil.toEnumValueName(enumValue), anootationSpec);

    }



    private TypeSpec buildTypeSpecForObjectType(final ObjectType objectType) {


        // object.getPropertyType() return the supper type
        final ClassName className = getClassName(getPackagePrefix(), objectType);
        final TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value","$S",getClass().getCanonicalName() ).build())
                .addJavadoc(getJavaDocProcessor().markDownToJavaDoc(objectType))
                .addModifiers(Modifier.PUBLIC);

        getDiscriminatorAnnotations(objectType).forEach(typeSpecBuilder::addAnnotation);


        //Add super interfaces
        Optional.of(
                Optional.ofNullable(objectType.getType())
                        .map(anyType -> getClassName(getPackagePrefix(), anyType)).orElse(getBaseClassName())
        )
                .map(typeSpecBuilder::superclass)
                .orElse(null);


        Flowable.fromIterable(objectType.getProperties())
                .doOnNext(property -> typeSpecBuilder.addField(getFieldSpec(property)))
                .doOnNext(property -> typeSpecBuilder.addMethod(getFieldGetter(property)))
                .doOnNext(property -> typeSpecBuilder.addMethod(getFieldSetter(property)))
                .blockingSubscribe();


        return typeSpecBuilder.build();
    }

    private FieldSpec getFieldSpec(final Property property) {

        if (property.getName().startsWith("/")) {
            final TypeName valueType = getTypeNameSwitch().doSwitch(property.getType());
            return FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), valueType), "values", Modifier.PRIVATE)
                    .build();
        } else {
            final TypeName valueType = getTypeNameSwitch().doSwitch(property.getType());
            return FieldSpec.builder(valueType, property.getName(), Modifier.PRIVATE).build();
        }
    }

    private MethodSpec getFieldSetter(final Property property) {
        final FieldSpec fieldSpec = getFieldSpec(property);
        final MethodSpec.Builder methodSpec;


        if (property.getName().startsWith("/")) {
            methodSpec = MethodSpec.methodBuilder("setValues");
        } else {
            methodSpec = MethodSpec.methodBuilder("set" + getPropertyMethodNameSuffix(property));
        }

        methodSpec.addParameter(fieldSpec.type, fieldSpec.name)
                .addModifiers(Modifier.PUBLIC)
                .addCode("this." + fieldSpec.name + " = " + fieldSpec.name + ";\n")
                .build();
        return methodSpec.build();
    }

    private MethodSpec getFieldGetter(final Property property) {
        FieldSpec fieldSpec = getFieldSpec(property);
        final MethodSpec.Builder methdBuilder;
        if (property.getName().startsWith("/")) {
            methdBuilder = MethodSpec.methodBuilder("values");
            methdBuilder.addAnnotation(JsonAnySetter.class);
            methdBuilder.addAnnotation(JsonAnyGetter.class);
        } else {
            final String attributePrefix = isBoolean(fieldSpec) ? "is" : "get";
            methdBuilder = MethodSpec.methodBuilder(attributePrefix + getPropertyMethodNameSuffix(property))
                    .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                            .addMember("value", "$S", property.getName()).build());
        }
        methdBuilder.addModifiers(Modifier.PUBLIC)
                .returns(fieldSpec.type)
                .addCode("return " + fieldSpec.name + ";\n");
        return methdBuilder.build();
    }


    private static String getPropertyMethodNameSuffix(final Property property) {
        final String javaPropertyName = Arrays.stream(property.getName().split("_"))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining());

        return javaPropertyName;
    }

    private static boolean isBoolean(FieldSpec fieldSpec) {
        return fieldSpec.type.equals(ClassName.BOOLEAN.box()) || fieldSpec.type.equals(ClassName.BOOLEAN);
    }

    public Path getPath(JavaFileObject javaFile, Path outputFolder) {
        return Paths.get(outputFolder.toString(), javaFile.getName());
    }

    protected Stream<AnnotationSpec> getDiscriminatorAnnotations(final ObjectType object) {
        if (StringUtils.isEmpty(object.getDiscriminator()) || object.getSubTypes().isEmpty()) {
            return Stream.empty();
        }
        final AnnotationSpec jsonTypeInfoAnnotation = AnnotationSpec.builder(JsonTypeInfo.class)
                .addMember("use", "$T.NAME", JsonTypeInfo.Id.class)
                .addMember("include", "$T.PROPERTY", JsonTypeInfo.As.class)
                .addMember("property", "$S", object.getDiscriminator())
                .addMember("visible", "true")
                .build();

        CodeBlock.Builder annotationBodyBuilder = CodeBlock.builder();
        List<ObjectType> children = getSubtypes(object).collect(Collectors.toList());
        if (!children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                annotationBodyBuilder.add(
                        (i == 0 ? "{" : ",") +
                                "\n@$T(value = $T.class, name = $S)"
                                + (i == children.size() - 1 ? "\n}" : "")
                        , JsonSubTypes.Type.class, getClassName(getPackagePrefix(), children.get(i)), children.get(i).getDiscriminatorValue());
            }
        }
        final AnnotationSpec jsonSubTypesAnnotation = AnnotationSpec.builder(JsonSubTypes.class)
                .addMember("value", annotationBodyBuilder.build())
                .build();

        return Stream.of(jsonSubTypesAnnotation, jsonTypeInfoAnnotation);
    }


    private ClassName getBaseClassName() {
        return ClassName.get(getPackagePrefix() + ".base", "Base");
    }


    private TypeSpec getBaseClassTypeSpec() {

        final MethodSpec toString = MethodSpec.methodBuilder("toString")
                .addCode("return $T.reflectionToString(this, $T.SHORT_PREFIX_STYLE);\n", ToStringBuilder.class, ToStringStyle.class)
                .addAnnotation(Override.class)
                .returns(String.class)
                .addModifiers(Modifier.PUBLIC)
                .build();

        final MethodSpec equals = MethodSpec.methodBuilder("equals")
                .addParameter(ParameterSpec.builder(TypeName.get(Object.class), "o").build())
                .addCode("return $T.reflectionEquals(this, o);\n", EqualsBuilder.class)
                .addAnnotation(Override.class)
                .returns(TypeName.BOOLEAN.unbox())
                .addModifiers(Modifier.PUBLIC)
                .build();

        final MethodSpec hashCode = MethodSpec.methodBuilder("hashCode")
                .addCode("return $T.reflectionHashCode(this);\n", HashCodeBuilder.class)
                .addAnnotation(Override.class)
                .returns(TypeName.INT.unbox())
                .addModifiers(Modifier.PUBLIC)
                .build();

        final TypeSpec typeSpec = TypeSpec.classBuilder(getBaseClassName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value","$S",getClass().getCanonicalName() ).build())
                .addMethod(toString)
                .addMethod(equals)
                .addMethod(hashCode)
                .build();

        return typeSpec;

    }

}
