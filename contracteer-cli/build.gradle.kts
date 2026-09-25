plugins {
  id("kotlin-conventions")
  alias(libs.plugins.graalvm)
  kotlin("kapt")
}

dependencies {
  implementation(project(":contracteer-verifier"))
  implementation(project(":contracteer-mockserver"))
  implementation(libs.picocli)
  implementation(libs.logback.classic)

  kapt(libs.picocli.codegen)

  testImplementation(testFixtures(project(":contracteer-core")))
}

kapt {
  arguments {
    arg("project", "${project.group}/${project.name}")
  }
}

// Tests use no annotation processor; kapt would warn that the picocli arguments above go unused.
tasks.matching { it.name in setOf("kaptGenerateStubsTestKotlin", "kaptTestKotlin") }.configureEach {
  enabled = false
}

val isQuickBuild = providers.gradleProperty("quickBuild").map { it.toBoolean() }.orElse(false)

graalvmNative {
  binaries {
    named("main") {
      mainClass = "dev.contracteer.cli.CliKt"
      imageName.set("contracteer")
      quickBuild.set(isQuickBuild)
      sharedLibrary.set(false)
      fallback.set(false)
      useFatJar.set(true)
      buildArgs.add("--enable-https")
      buildArgs.add("--enable-http")
      buildArgs.add("-H:IncludeResources=logback.xml")
      if (!isQuickBuild.get()) buildArgs.add("-Os")
    }
  }
}

tasks.register("generateVersionFile") {
  val outputDir = layout.buildDirectory.dir("generated/resources/version")
  outputs.dir(outputDir)
  doLast {
    val file = outputDir.get().file("version.txt").asFile
    file.parentFile.mkdirs()
    file.writeText(project.version.toString())
  }
}

tasks.named("processResources") {
  dependsOn("generateVersionFile")
}

tasks.named("sourcesJar") {
  dependsOn("generateVersionFile")
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/resources/version"))
