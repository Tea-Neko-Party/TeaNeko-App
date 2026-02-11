import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

// 插件
plugins {
    java    // 基础插件
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

// spring shell 版本
extra["springShellVersion"] = "4.0.1"

// 依赖
dependencies {
    // framework
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.shell:spring-shell-starter")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("com.mysql:mysql-connector-j")

    // ====== development ======
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ====== test ======
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.shell:spring-shell-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 依赖管理
dependencyManagement {
    imports {
        mavenBom("org.springframework.shell:spring-shell-dependencies:${property("springShellVersion")}")
    }
}

// 测试配置
tasks.withType<Test> {
    useJUnitPlatform()
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
    // 使用 prod 作为默认的 Spring profile，除非通过命令行参数覆盖
    withType<BootRun> {
        systemProperty("spring.profiles.active", project.findProperty("springProfile") ?: "prod")
    }

    // ========= bootJar 配置 =========
    named<BootJar>("bootJar") {
        doFirst {
            println("Building JAR with Spring profile: prod")
        }

        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Spring-Profile" to "prod"
            )
        }
    }
}