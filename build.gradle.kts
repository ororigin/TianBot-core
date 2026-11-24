plugins {
    java
    // Lophine 自研的 paperweight 分支（fun.bm.comfreyweight），用于消费 Lophine 的 dev-bundle
    // 以获取 Mojang 映射的 net.minecraft.* / ca.spottedleaf.moonrise.* 服务端内部类。
    // 注意：该插件未发布到公共仓库，需先自行 publishToMavenLocal（见 README/迁移笔记）。
    id("fun.bm.comfreyweight.userdev") version "1.0.1"
    // 打包运行时依赖（sqlite-jdbc）进插件 jar
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.tianbot"
version = "26.3-release-1.1"

repositories {
    // Lophine 本地发布的 lophine-api / dev-bundle（26.3-R0.1-SNAPSHOT）
    mavenLocal()
    // mache 相关的 userdev 依赖（io.papermc:mache、io.papermc.unpick-definitions、
    // io.papermc.codebook 等）只存在于 PaperMC 仓库。dev-bundle 的 mache.json 虽
    // 声明了这些仓库，但 userdev 插件的注入发生在 afterEvaluate，晚于本块；若不显式
    // 声明，Gradle 会先在 bacteriawa 上 404（该仓库只有 unpick 的空 POM）并中止。
    // 注意：unpick-definitions 的 groupId 是 `io.papermc.unpick-definitions`，
    // 与 `io.papermc` 不同，故用 includeGroupByRegex 一并覆盖。
    maven("https://repo.papermc.io/repository/maven-public/") {
        content {
            includeGroupByRegex("io\\.papermc.*")
            includeGroup("net.fabricmc")
        }
    }
    // Lophine 官方仓库（lophine-api 等）
    maven("https://repo.bacteriawa.com/repository/maven-public/")
    mavenCentral()
}

dependencies {
    // Lophine 26.3 NMS dev-bundle：提供 Mojang 映射的服务端内部类
    // （net.minecraft.server.level.ServerPlayer、org.bukkit.craftbukkit.*、
    //  ca.spottedleaf.moonrise.* 等）。Lophine 运行时即 Mojang 映射，无需 reobf。
    //
    // 为什么不用 paperweight.foliaDevBundle(...)：Lophine 是 fork，其 api-version、
    // packet 注册表与 RegionizedWorldData 等均可能与上游 Folia 不同步；且上游
    // dev.folia 目前根本没有 26.3（最新仅 26.2.build.7-beta）。
    paperweight.devBundle("fun.bm.lophine", "26.3-R0.1-SNAPSHOT")

    // Lophine 26.3 API（含 io.papermc.paper.threadedregions.scheduler.*、
    // io.papermc.paper.command.brigadier.*、org.leavesmc.leaves.* 等）。
    // lophine-api 通过 capability 别名同时充当 org.bukkit:bukkit /
    // io.papermc.paper:paper-mojangapi / org.spigotmc:spigot-api，故无需再声明 folia-api。
    compileOnly("fun.bm.lophine:lophine-api:26.3-R0.1-SNAPSHOT")

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

// 部署到本地 Lophine 26.3 测试服
val testServerPluginsDir = file(providers.gradleProperty("foliaBotTestServerPlugins").getOrElse("D:/minecraft/server/26.3/plugins"))

tasks.register<Copy>("deploy") {
    dependsOn("shadowJar")
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(testServerPluginsDir)
    doFirst {
        logger.lifecycle("Deploying ${project.name}-${project.version}-all.jar -> ${testServerPluginsDir.absolutePath}")
    }
}
