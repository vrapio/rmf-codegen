@file:JvmName("RamlCodegenPlugin")

package io.vrap.rmf.codegen.plugin

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

open class RamlCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        setupProject(project)
        createTasks(project)
    }

    fun setupProject(project: Project){
        val generators : NamedDomainObjectContainer<RamlGenerator>  =  project.container(RamlGenerator::class.java)
        generators.all { generator: RamlGenerator?->   generator?.targets = project.container(Target::class.java)}
        project.extensions.add("RamlGenerator", generators)
    }

    fun createTasks(project: Project){
        project.tasks.create("generateRamlStub",RamlCodeGeneratorTask::class.java)
    }
}
