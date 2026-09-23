pluginManagement {
    repositories {
        // fun.bm.comfreyweight.* （Lophine 的 paperweight 分支）未发布到任何公共仓库，
        // 需先自行从 https://github.com/LophineLabs/ComfreyWeight 源码 publishToMavenLocal。
        // 因此 pluginManagement 必须包含 mavenLocal()。
        mavenLocal()
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// 自动下载缺失的 JDK 25 toolchain
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "TianBot-core"
