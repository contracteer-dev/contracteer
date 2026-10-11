plugins {
  id("library-conventions")
  `java-test-fixtures`
}

dependencies {
  implementation(libs.swagger.parser)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  implementation(libs.rgxgen)

  testImplementation(libs.logback.classic)
  testImplementation(libs.mockserver.netty)
}

