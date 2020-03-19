plugins {
  `java-library`
  `publishing`
  `maven-publish`
}

group = "de.classyfi.libs"
version = "0.0.2"

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
  jcenter()
}

dependencies {
  implementation(platform("org.springframework.session:spring-session-bom:Corn-SR1"))
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
