package io.vrap.rmf.codegen.common.generator.model.codegen;

import com.squareup.javapoet.TypeSpec;
import io.vrap.rmf.codegen.common.generator.core.ConfigDecoratorBase;
import io.vrap.rmf.codegen.common.generator.core.GeneratorConfig;
import io.vrap.rmf.raml.model.types.AnyType;

public abstract class TypeTransformer<T extends AnyType> extends ConfigDecoratorBase {


    protected TypeTransformer(final GeneratorConfig generatorConfig) {
       super(generatorConfig);
    }


    public abstract TypeSpec toTypeSpec(T type);




}