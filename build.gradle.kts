import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
  `java-library`
  `publishing`
  `maven-publish`
  id("com.jfrog.bintray") version "1.8.3"
}

group = "de.classyfi.libs"
version = "0.1.1"

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
  jcenter()
}

dependencies {
  implementation(platform("org.springframework.session:spring-session-bom:Corn-SR2"))
  implementation("org.springframework.session:spring-session-core")
  implementation("org.springframework.session:spring-session-data-redis")
}

tasks.register<Jar>("sourcesJar") {
  from(sourceSets.main.get().allJava)
  archiveClassifier.set("sources")
}

publishing {
  val mavenJava by publications.creating(MavenPublication::class) {
    from(components["java"])
    artifact(tasks["sourcesJar"])
  }
}


val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

if (isSnapshot) {
  version = "${project.version.toString().removeSuffix("-SNAPSHOT")}-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}"
}

val bintray_user: String? by project
val bintray_apikey: String? by project

bintray {
  user = bintray_user
  key = bintray_apikey
  setPublications("mavenJava")
  with (pkg) {
    repo = "releases"
    name = rootProject.name
    userOrg = "markt-de"
    setLicenses("Apache-2.0")
    vcsUrl = "https://github.com/markt-de/spring-session-sticky"
    version.name = project.version.toString()

    if (isSnapshot) {
      repo = "snapshots"
      override = true
    }
  }
}