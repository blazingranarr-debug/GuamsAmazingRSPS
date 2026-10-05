plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.simmetrics.core)
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.gameframe)
    implementation(projects.api.parsers.json)
    implementation(projects.api.registry)
    implementation(projects.api.realm)
    implementation(projects.api.realmConfig)
    implementation(projects.api.type.typeSymbols)
    implementation(projects.api.utils.utilsSystem)
    implementation(projects.engine.utilsBits)
    testImplementation(libs.netty.buffer)
    testImplementation(projects.api.cache)
    testImplementation(projects.engine.game)
}
