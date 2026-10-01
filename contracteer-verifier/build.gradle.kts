plugins {
  id("library-conventions")
}

dependencies {
  api(project(":contracteer-core"))

  implementation(platform(libs.http4k.bom))
  implementation(libs.http4k.core)
  implementation(libs.jackson.databind)

  testImplementation(testFixtures(project(":contracteer-core")))
  testImplementation(libs.mockk)
}