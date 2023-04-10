dependencies {
    implementation(projects.shared)
    implementation("org.bstats:bstats-velocity:3.0.1")
    compileOnly("com.velocitypowered:velocity-api:3.1.2-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.2-SNAPSHOT")
}

tasks.compileJava {
    options.release.set(11)
}