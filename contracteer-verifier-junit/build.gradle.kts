plugins {
  id("library-conventions")
}

dependencies {
  api(project(":contracteer-verifier"))
  implementation(libs.junit.engine)

  testImplementation(testFixtures(project(":contracteer-core")))
  testImplementation(platform(libs.http4k.bom))
  testImplementation(libs.http4k.core)
  testImplementation(libs.http4k.jetty)
  testImplementation(libs.logback.classic)
  testImplementation(libs.junit.platform.testkit)
}

tasks.test {
  useJUnitPlatform {
    excludeTags("engine-test-kit-target")
  }
}
