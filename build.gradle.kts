import com.sun.management.OperatingSystemMXBean
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun
import java.lang.management.ManagementFactory

// 插件
plugins {
    java                                                    // 基础插件
    id("org.springframework.boot") version "4.0.2"          // spring boot 插件
    id("io.spring.dependency-management") version "1.1.7"   // spring 依赖管理插件
}

// 版本信息
val vm = VersionManager(project)
version = vm.currentVersion.toString()

// 组织信息
group = "org.zExNocs"
description = "TeaNeko-App 专注于机器人聊天的项目"

// java 版本
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 配置
configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

// 仓库
repositories {
    mavenCentral()
}

// 依赖
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

    // ====== development ======
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ====== test ======
    // testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// 测试配置
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

tasks {
    // ========= bootRun 配置 =========

    withType<BootRun> {
        // 自动配置内存:
        // 4G: -Xms512m -Xmx3072m
        // 8G: -Xms1024m -Xmx6144m
        // 16G: -Xms2048m -Xmx12,288m
        // max: 12,288m
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

        // 使用 prod 作为默认的 Spring profile，除非通过命令行参数覆盖
        systemProperty("spring.profiles.active", project.findProperty("springProfile") ?: "prod")
    }

    // ========= bootJar 配置 =========
    named<BootJar>("bootJar") {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Spring-Profile" to "prod"
            )
        }
    }
}