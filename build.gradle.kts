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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// ========= 主类配置 =========
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
        // 注入版本号
        doFirst {
            systemProperty("version", versionProject.version.toString())
        }

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
                "Spring-Profile" to "prod"
            )
        }
        doFirst {
            manifest.attributes("Implementation-Version" to versionProject.version.toString())
        }
    }
}
