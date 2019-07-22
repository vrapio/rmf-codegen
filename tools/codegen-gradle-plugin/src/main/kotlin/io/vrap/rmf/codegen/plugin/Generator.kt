package io.vrap.rmf.codegen.plugin

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import java.io.File


open class RamlGenerator constructor(val name:String){

    var uri: File? = null
    var targets : NamedDomainObjectContainer<Target>? = null



    fun targets (closure: Closure<Target>){
        targets?.configure(closure)
    }

    override fun toString(): String {
        return "RamlGenerator(name='$name', uri=$uri, targets=${targets?.map { target -> target.toString() }})"
    }
}

open class Target constructor(val name: String){

    var path : File? = null
    var models_package: String? = null
    var base_package: String? = null
    var client_package: String? = null
    var target: TargetType? = null
    var customTypeMapping: Map<String, String> = mapOf()
    var typeNamePrefix: String? = null

    override fun toString(): String {
        return "Target(name='$name', path=$path, models_package=$models_package, base_package=$base_package, client_package=$client_package, target=$target)"
    }
}

enum class TargetType {
    JAVA_MODEL,
    JAVA_MODEL_WITH_INTERFACES,
    GROOVY_DSL,
    JAVA_SPRING_CLIENT,
    TYPESCRIPT_MODEL,
    JOI_VALIDATOR
}
