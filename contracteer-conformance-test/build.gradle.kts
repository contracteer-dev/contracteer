plugins {
  id("kotlin-conventions")
}

dependencies {
  testImplementation(project(":contracteer-verifier"))
  testImplementation(project(":contracteer-mockserver"))
  testImplementation(testFixtures(project(":contracteer-core")))
  testImplementation(platform(libs.http4k.bom))
  testImplementation(libs.http4k.core)
  testImplementation(libs.logback.classic)
}
