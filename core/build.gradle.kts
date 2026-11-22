plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Pure Kotlin: no Android, no third-party dependencies. All scheduling, safety and plan rules live here and are unit tested.
val coreTests = tasks.register<JavaExec>("coreTests") {
    group = "verification"
    description = "Runs the dependency-free DoseCircle core test suite."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.dosecircle.core.TestRunnerKt")
}

tasks.named("check") { dependsOn(coreTests) }
