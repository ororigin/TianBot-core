plugins {
    java
    // Paper 官方插件 NMS 开发插件：解析 Folia 发布的 dev-bundle（Mojang 映射服务端类）
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    // 打包运行时依赖（sqlite-jdbc）进插件 jar
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.tianbot"
version = "26.2-beta-2"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    // Folia 26.2 NMS dev-bundle：提供 Mojang 映射的 net.minecraft.server.* 服务端类
    // （含 net.minecraft.server.level.ServerPlayer、org.bukkit.craftbukkit.entity.CraftPlayer 等）。
    // Folia 26.2 运行时即 Mojang 映射，插件编译后可直接部署，无需 reobf。
    // 解析为 dev.folia:dev-bundle:26.2.build.4-beta
    paperweight.foliaDevBundle("26.2.build.4-beta")

    // Folia 26.2 官方 API（包含 io.papermc.paper.threadedregions.scheduler.*）
    // 注：dev-bundle 的 serverCompileClasspath 已自带 folia-api，此处保留作显式声明/兜底。
    compileOnly("dev.folia:folia-api:26.2.build.4-beta")

    // SQLite JDBC 驱动（假人属性持久化，运行时被打包进 jar）
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
}

tasks.shadowJar {
    // 保持 sqlite-jdbc 原生库路径（org/sqlite/native/...）不被 relocate，避免 native 加载失败
    mergeServiceFiles()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}

// 部署到本地 Folia 26.2 测试服
val testServerPluginsDir = file(providers.gradleProperty("foliaBotTestServerPlugins").getOrElse("D:/minecraft/server/26.2/plugins"))

tasks.register<Copy>("deploy") {
    dependsOn("shadowJar")
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(testServerPluginsDir)
    doFirst {
        logger.lifecycle("Deploying ${project.name}-${project.version}-all.jar -> ${testServerPluginsDir.absolutePath}")
    }
}
