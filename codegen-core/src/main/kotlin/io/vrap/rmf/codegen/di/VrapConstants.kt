package io.vrap.rmf.codegen.di

import com.google.inject.BindingAnnotation


@BindingAnnotation
@Target(AnnotationTarget.FIELD,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultPackage

@BindingAnnotation
@Target(AnnotationTarget.FIELD,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OutputFolder

@BindingAnnotation
@Target(AnnotationTarget.FIELD,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ClientPackageName


@BindingAnnotation
@Target(AnnotationTarget.FIELD,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModelPackageName

@BindingAnnotation
@Target(AnnotationTarget.FIELD,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BasePackageName