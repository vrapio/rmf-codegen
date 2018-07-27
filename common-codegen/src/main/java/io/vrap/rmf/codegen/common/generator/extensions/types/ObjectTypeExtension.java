package io.vrap.rmf.codegen.common.generator.extensions.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vrap.rmf.codegen.common.generator.core.GeneratorConfig;
import io.vrap.rmf.codegen.common.generator.util.TypeNameSwitch;
import io.vrap.rmf.codegen.common.processor.annotations.ExtensionMethod;
import io.vrap.rmf.codegen.common.processor.annotations.ModelExtension;
import io.vrap.rmf.raml.model.types.ObjectType;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@ModelExtension(extend = ObjectType.class)
public class ObjectTypeExtension {

    TypeNameSwitch typeNameSwitch;

    @Inject
    public void setGeneratorConfig(final GeneratorConfig generatorConfig) {
        this.typeNameSwitch = TypeNameSwitch.of(generatorConfig);
    }

    @ExtensionMethod
    public String getDiscriminatorValue(ObjectType objectType) {
        objectType.getType();
        return objectType.getDiscriminatorValue();
    }

    @ExtensionMethod
    public String getDiscriminator(ObjectType objectType) {
        return objectType.getDiscriminator();
    }

    @ExtensionMethod
    public boolean hasSubtypes(ObjectType objectType) {
        return !StringUtils.isEmpty(objectType.getDiscriminator()) && objectType.getSubTypes() != null && !objectType.getSubTypes().isEmpty();
    }

    @ExtensionMethod
    public List<String> getImports(ObjectType objectType) {

        return Flowable.fromIterable(objectType.getProperties())
                .map(property -> property.getType())
                .concatWith(Flowable.fromIterable(objectType.getSubTypes()).filter(anyType -> hasSubtypes(objectType)))
                .concatWith(Maybe.fromCallable(objectType::getType) )
                .map(typeNameSwitch::doSwitch)
                .flatMapIterable(this::getAllClassNames)
                .filter(s -> !s.startsWith("java.lang"))
                .distinct()
                .toList()
                .blockingGet();
    }

    List<String> getAllClassNames(TypeName typeName) {
        if (typeName instanceof ClassName) {
            return Arrays.asList(((ClassName) typeName).reflectionName());
        } else if (typeName instanceof ParameterizedTypeName) {
            ParameterizedTypeName parameterized = ((ParameterizedTypeName) typeName);

            List<String> imports = Flowable.fromIterable(parameterized.typeArguments)
                    .concatWith(Single.just(parameterized.rawType))
                    .flatMapIterable(this::getAllClassNames)
                    .toList()
                    .blockingGet();
            return imports;
        }
        throw new IllegalStateException("shouldn't arrive here");
    }



}