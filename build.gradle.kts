import com.sun.management.OperatingSystemMXBean
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun
import se.bjurr.gitchangelog.plugin.gradle.GitChangelogTask
import java.lang.management.ManagementFactory
import java.time.LocalDate

// ========= 插件 =========
plugins {
    java                                                    // 基础插件
    id("org.springframework.boot") version "4.0.3"          // spring boot 插件
    id("io.spring.dependency-management") version "1.1.7"   // spring 依赖管理插件
    id("com.github.ben-manes.versions") version "0.53.0"    // gradle version 插件
    id("se.bjurr.gitchangelog.git-changelog-gradle-plugin") version "3.1.2" // 根据 git 自动写入 changelog
}
val springAiVersion by extra("2.0.0-M3")

// ========= 版本信息 =========
val vm = VersionManager(project)
version = vm.currentVersion.toString()

// ========= 组织信息 =========
group = "org.zExNocs"
description = "TeaNeko-App 专注于机器人聊天的项目"

// ========= java 版本 =========
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ========= 编译配置 =========
configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
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
// -------- versioning and changelog --------
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

tasks.register("createGitTag") {
    group = "versioning"
    description = "自动根据当前 version 属性打 Git Tag"

    doLast {
        val currentVersion = project.version.toString()
        val tagName = "v$currentVersion"

        try {
            // 使用原生 ProcessBuilder 检查 tag 是否存在
            val checkProcess = ProcessBuilder("git", "rev-parse", "-q", "--verify", "refs/tags/$tagName")
                .directory(project.projectDir)
                .start()
            checkProcess.waitFor()

            if (checkProcess.exitValue() == 0) {
                println("⚠️ Tag $tagName 已经存在，跳过创建。")
            } else {
                println("📦 正在为当前版本创建 Git Tag: $tagName")
                // 使用原生 ProcessBuilder 创建 tag
                val tagProcess = ProcessBuilder("git", "tag", "-a", tagName, "-m", "Release version $tagName")
                    .directory(project.projectDir)
                    .start()
                tagProcess.waitFor()

                if (tagProcess.exitValue() == 0) {
                    println("✅ Tag $tagName 创建成功！记得 push 代码时带上 tag。")
                } else {
                    val errorMsg = tagProcess.errorStream.bufferedReader().readText()
                    println("❌ 创建 Tag 失败: $errorMsg")
                }
            }
        } catch (e: Exception) {
            println("❌ 执行 Git 命令失败，请确认环境: ${e.message}")
        }
    }
}

tasks.register<GitChangelogTask>("generateGitChangelog") {
    group = "versioning"
    description = "Generate changelog for the current version only"

    file.set(File(project.projectDir, "docs/changelog/${project.version}.md"))

    // 动态获取上一个 Git Tag 作为起点
    fromRevision.set(project.provider {
        try {
            // 使用原生 ProcessBuilder 获取上一个 tag
            val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                .directory(project.projectDir)
                .start()

            // 读取标准输出
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() == 0) output else ""
        } catch (_: Exception) {
            "" // 如果报错（比如没有任何 tag），就返回空，让插件从头开始抓取
        }
    })

    toRevision.set("HEAD")

    // 过滤掉没意义的提交（基于约定式提交规范）
    ignoreCommitsIfMessageMatches.set("^chore:.*|^test:.*|^style:.*|^build:.*|^Merge.*")

    templateContent.set("""
        # [${project.version}] - ${LocalDate.now()}
        
        ### 更新内容
        {{#commits}}
        * [{{authorName}}] - {{messageTitle}} - `{{hash}}`
        {{/commits}}
    """.trimIndent())
}

// --------- file config 模板任务 ---------
tasks.register<CopyConfigToTemplates>("copyConfigToTemplates")

tasks.register<CopyTemplatesToConfig>("copyTemplatesToConfig")
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
        systemProperty("version", project.version.toString())

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
                "Implementation-Version" to project.version,
                "Spring-Profile" to "prod"
            )
        }
    }
}