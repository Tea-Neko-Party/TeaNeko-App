import com.sun.management.OperatingSystemMXBean
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun
import se.bjurr.gitchangelog.plugin.gradle.GitChangelogTask
import java.lang.management.ManagementFactory
import java.time.LocalDate

// ========= 插件 =========
plugins {
    java                                                    // 基础插件
    id("org.springframework.boot") version "4.1.2"          // spring boot 插件
    id("io.spring.dependency-management") version "1.1.7"   // spring 依赖管理插件
    id("com.github.ben-manes.versions") version "0.54.0"    // gradle version 插件
    id("se.bjurr.gitchangelog.git-changelog-gradle-plugin") version "3.1.2" // 根据 git 自动写入 changelog
}
extra.set("springAiVersion", "2.0.0-M3")
val springAiVersion = extra["springAiVersion"] as String

// ========= 版本信息 =========
val versionProject = project
val vm = VersionManager(versionProject)
version = vm.currentVersion.toString()

// ========= 组织信息 =========
group = "org.zExNocs"
description = "TeaNeko-App 专注于机器人聊天的项目"

// ========= java 版本 =========
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ========= 编译配置 =========
configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

// ========= 仓库与依赖 =========
repositories {
    mavenCentral()
}

dependencies {
    // framework
    // implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 2026/11/03：Web 控制台复用框架认证、会话与 CSRF，不维护平行鉴权框架。
    implementation("org.springframework.boot:spring-boot-starter-security")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("com.mysql:mysql-connector-j")

    // ====== jackson ======
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")

    // ====== AI ======
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")

    // ====== development ======
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ====== test ======
    // testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// ========= 主类配置 =========
// 2026/11/03：前端锁文件确保可复现安装，静态产物与 Java 一起进入同源发布包。
val webInstall = tasks.register<Exec>("webInstall") {
    group = "web"
    description = "根据锁文件安装控制台依赖"
    workingDir(layout.projectDirectory.dir("frontend"))
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm",
        "ci", "--no-audit", "--no-fund")
    inputs.files("frontend/package.json", "frontend/package-lock.json")
    outputs.dir("frontend/node_modules")
}

val webBuild = tasks.register<Exec>("webBuild") {
    group = "web"
    description = "检查 TypeScript 并构建控制台静态资源"
    dependsOn(webInstall)
    workingDir(layout.projectDirectory.dir("frontend"))
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm", "run", "build")
    inputs.dir("frontend/src")
    // 2026/11/04：统一端口配置及读取器变更后重新校验前端构建。
    inputs.files("frontend/index.html", "frontend/tsconfig.json", "frontend/vite.config.ts", "frontend/dev-ports.ts",
        "frontend/package-lock.json", "src/main/resources/application.properties")
    outputs.dir(layout.buildDirectory.dir("generated/web/console"))
}

// 2026/11/04：类路径启动与 JAR 共用实际构建版本，唯一输入仍为原版本管理器。
val generateVersionInfo = tasks.register<WriteProperties>("generateVersionInfo") {
    group = "build"
    description = "生成当前构建的运行版本资源"
    destinationFile.set(layout.buildDirectory.file("generated/version/teaneko-version.properties"))
    encoding = "UTF-8"
    property("version", provider { versionProject.version.toString() })
}

// 2026/11/04：同时登记资源目录，Gradle 类路径和 IDE 导入使用同一份生成结果。
sourceSets.named("main") {
    resources.srcDir(generateVersionInfo.map { it.destinationFile.get().asFile.parentFile })
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(webBuild, generateVersionInfo)
    from(layout.buildDirectory.dir("generated/web")) {
        into("static")
    }
}

springBoot {
    mainClass.set("org.zexnocs.teanekoapp.TeaNekoAppApplication")
}

// ========= 测试配置 =========
tasks.withType<Test> {
    useJUnitPlatform()

    // 添加 Mockito agent 以支持 inline mock
    doFirst {
        val byteBuddyAgent = configurations.testRuntimeClasspath.get()
            .files
            .find { it.name.contains("byte-buddy-agent") }
            ?.absolutePath

        if (byteBuddyAgent != null) {
            jvmArgs("-javaagent:$byteBuddyAgent")
        }
    }
}

// ========== 注册任务 ==========
// -------- 版本、Git 提交与更新日志 --------
tasks.register<BumpVersionTask>("bumpMajor") {
    description = "增加主版本号 (major)"
    group = "versioning"
    majorInc = true
    minorInc = false
    patchInc = false
}

tasks.register<BumpVersionTask>("bumpMinor") {
    description = "增加次版本号 (minor)"
    group = "versioning"
    majorInc = false
    minorInc = true
    patchInc = false
}

tasks.register<BumpVersionTask>("bumpPatch") {
    description = "增加修订版本号 (patch)"
    group = "versioning"
    majorInc = false
    minorInc = false
    patchInc = true
}

listOf("Major", "Minor", "Patch").forEach { level ->
    tasks.register<BumpVersionTask>("bumpAgent$level") {
        group = "versioning"
        description = "升级 Agent ${level.lowercase()} 版本并添加 -l 后缀"
        majorInc = level == "Major"
        minorInc = level == "Minor"
        patchInc = level == "Patch"
        agentVersion = true
    }
}

tasks.register<CreateVersionGitTagTask>("createGitTag") {
    group = "versioning"
    description = "为已提交的当前完整版本创建 Git 标签"
}

tasks.register<CreateVersionGitTagTask>("createAgentGitTag") {
    group = "versioning"
    description = "为已提交的 -l 版本创建 Agent Git 标签"
    agentOnly = true
}

listOf("agentGitPreview", "agentGitUpdate").forEach { entry ->
    tasks.register<AgentGitUpdateTask>(entry) {
        group = "versioning"
        description = if (entry == "agentGitPreview") "只读预览 Agent 版本及暂存区" else "更新 Agent 版本、提交暂存区并创建标签"
        preview = entry == "agentGitPreview"
        bump = providers.gradleProperty("agentBump").getOrElse("none")
        commitMessage = providers.gradleProperty("agentCommitMessage").getOrElse("")
    }
}

// 在任何任务写入前拒绝互相冲突的升级入口，避免连续递增或普通/Agent 后缀互相覆盖。
gradle.taskGraph.whenReady {
    val bumps = allTasks.filterIsInstance<BumpVersionTask>()
    val agentUpdate = allTasks.any { it.path == ":agentGitUpdate" }
    require(bumps.size <= 1) { "一次构建只能选择一个普通或 Agent 版本升级入口。" }
    require(!agentUpdate || bumps.isEmpty()) { "agentGitUpdate 已负责版本更新，不能同时调用 bump 任务。" }
    require(!agentUpdate || allTasks.none { it.path == ":generateGitChangelog" }) {
        "请先生成并审阅更新日志，再暂存并调用 agentGitUpdate，避免提交遗漏本次日志。"
    }
    val tags = allTasks.filterIsInstance<CreateVersionGitTagTask>()
    require(tags.size <= 1 && (!agentUpdate || tags.isEmpty())) { "一次构建只能选择一个 Git 标签或 Agent 更新入口。" }
}

tasks.withType<CreateVersionGitTagTask>().configureEach {
    mustRunAfter(tasks.withType<BumpVersionTask>())
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveVersion.set(provider { versionProject.version.toString() })
    mustRunAfter(tasks.withType<BumpVersionTask>(), tasks.named("agentGitUpdate"))
}

tasks.register<GitChangelogTask>("generateGitChangelog") {
    group = "versioning"
    description = "根据执行时的当前版本生成更新日志"

    mustRunAfter(tasks.withType<BumpVersionTask>())
    file.set(provider { File(versionProject.projectDir, "docs/changelog/${versionProject.version}.md") })

    // 当前版本与文件名均延迟读取；当前标签已经存在时仍从上一版本开始。
    fromRevision.set(provider { VersionGitRepository(versionProject.projectDir).previousTag(versionProject.version.toString()) })

    toRevision.set("HEAD")

    // 过滤掉没意义的提交（基于约定式提交规范）
    ignoreCommitsIfMessageMatches.set("^chore:.*|^test:.*|^style:.*|^build:.*|^Merge.*")

    templateContent.set(provider { """
        # [${versionProject.version}] - ${LocalDate.now()}
        
        ### 更新内容
        {{#commits}}
        * [{{authorName}}] - {{messageTitle}} - `{{hash}}`
        {{/commits}}
    """.trimIndent() })
}

// --------- file config 模板任务 ---------
tasks.register<CopyConfigToTemplates>("copyConfigToTemplates") {
    description = "复制 config 文件夹下的配置文件到 templates/config 目录下"
}

tasks.register<CopyTemplatesToConfig>("copyTemplatesToConfig") {
    description = "复制 templates/config 目录下的配置文件到 config 文件夹下"
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

// --------- 其他配置 ---------

tasks {
    // ========= bootRun 配置 =========
    withType<BootRun> {
        // 2026/11/02：允许类路径中的 Netty 等依赖加载本地库，匹配 JDK 25 的原生访问要求。
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        // 2026/11/04：运行版本由 processResources 生成，不再由启动参数声明。

        // 使用 prod 作为默认的 Spring profile，除非通过命令行参数覆盖
        systemProperty("spring.profiles.active", project.findProperty("springProfile") ?: "prod")

        /* 配置内存
        // 4G: -Xms512m -Xmx3072m
        // 8G: -Xms1024m -Xmx6144m
        // 16G: -Xms2048m -Xmx12,288m
        // max: 12,288m
        */
        doFirst {
            val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
            val totalMemMb: Long = if (osBean != null) {
                osBean.totalMemorySize / 1024 / 1024
            } else {
                println("Warning: cannot detect total memory via MXBean, defaulting to 8G")
                8192L
            }

            // 分配策略
            val (xms, xmx) = when {
                totalMemMb <= 2048 -> 256 to 1536
                totalMemMb <= 4096 -> 512 to 3072
                totalMemMb <= 8192 -> 1024 to 6144
                totalMemMb <= 16384 -> 2048 to 12288
                else -> 2048 to 8192
            }

            println("Detected total memory: ${totalMemMb}MB, setting JVM -Xms=${xms}m -Xmx=${xmx}m")

            jvmArgs("-Xms${xms}m", "-Xmx${xmx}m")
        }
    }

    // ========= bootJar 配置 =========
    named<BootJar>("bootJar") {
        mainClass.set("org.zexnocs.teanekoapp.TeaNekoAppApplication")
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                // 2026/11/02：java -jar 启动也显式声明类路径依赖所需的原生访问。
                "Enable-Native-Access" to "ALL-UNNAMED",
                "Spring-Profile" to "prod"
            )
        }
        doFirst {
            manifest.attributes("Implementation-Version" to versionProject.version.toString())
        }
    }
}
