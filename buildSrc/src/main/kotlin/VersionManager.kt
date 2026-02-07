import java.util.Properties
import java.io.File
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input

// 语义化版本号类（接口不变）
data class SemVer(var major: Int, var minor: Int, var patch: Int) {
    override fun toString() = "$major.$minor.$patch"
}

fun parseSemVer(v: String): SemVer {
    val parts = v.trim().split(".")
    require(parts.size == 3) { "Invalid semver format: $v" }
    return SemVer(
        parts[0].toInt(),
        parts[1].toInt(),
        parts[2].toInt()
    )
}

// 版本文件操作（构造函数不变）
class VersionManager(
    val project: Project,
    val versionFile: File = project.rootProject.file("version.properties")
) {

    private var cachedVersion: SemVer? = null

    val currentVersion: SemVer
        get() {
            cachedVersion?.let { return it }

            val version =
                if (!versionFile.exists()) {
                    parseSemVer("0.0.0")
                } else {
                    val props = Properties()
                    versionFile.inputStream().use { props.load(it) }
                    parseSemVer(props.getProperty("version", "0.0.0"))
                }

            cachedVersion = version
            return version
        }

    fun updateVersion(
        majorInc: Boolean = false,
        minorInc: Boolean = false,
        patchInc: Boolean = false
    ) {
        // 防御：只能有一个为 true
        require(
            listOf(majorInc, minorInc, patchInc).count { it } == 1
        ) {
            "Exactly one of majorInc/minorInc/patchInc must be true"
        }

        val semver = currentVersion

        when {
            majorInc -> {
                semver.major++
                semver.minor = 0
                semver.patch = 0
            }
            minorInc -> {
                semver.minor++
                semver.patch = 0
            }
            patchInc -> {
                semver.patch++
            }
        }

        val props = Properties().apply {
            setProperty("version", semver.toString())
        }

        versionFile.parentFile?.mkdirs()
        versionFile.outputStream().use {
            props.store(it, null)
        }
        project.version = semver.toString()
        project.logger.lifecycle("Updated version: $semver")
    }
}

// 自定义任务基类（接口不变）
abstract class BumpVersionTask : DefaultTask() {

    @Input
    var majorInc: Boolean = false

    @Input
    var minorInc: Boolean = false

    @Input
    var patchInc: Boolean = false

    @TaskAction
    fun bump() {
        val manager = VersionManager(project)
        manager.updateVersion(majorInc, minorInc, patchInc)
    }
}
