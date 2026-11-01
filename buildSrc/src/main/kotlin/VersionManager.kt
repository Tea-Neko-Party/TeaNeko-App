import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*

/**
 * 不可变的三段版本号，额外保留发布后缀，避免升级时修改已缓存的旧版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @constructor 创建[SemVer]
 * @param major 主版本号。
 * @param minor 次版本号。
 * @param patch 修订号。
 * @param suffix 发布后缀，不含连字符。
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int, val suffix: String? = null) {
    /**
     * 返回包含可选发布后缀的版本号。
     *
     * @return 版本文件及标签使用的完整版本。
     */
    override fun toString() = "$major.$minor.$patch" + (suffix?.let { "-$it" } ?: "")

    /**
     * 计算下一版本；旧入口生成普通版本，Agent 入口始终使用 l 后缀。
     *
     * @param majorInc 是否升级主版本。
     * @param minorInc 是否升级次版本。
     * @param patchInc 是否升级修订号。
     * @param agentVersion 是否使用 Agent 发布后缀。
     * @return 未写入文件的新版本。
     * @throws IllegalArgumentException 升级维度不唯一或目标数值溢出时抛出。
     */
    fun next(majorInc: Boolean, minorInc: Boolean, patchInc: Boolean, agentVersion: Boolean = false): SemVer {
        require(listOf(majorInc, minorInc, patchInc).count { it } == 1) { "必须且只能选择一个版本升级维度。" }
        val number = if (majorInc) major else if (minorInc) minor else patch
        require(number < Int.MAX_VALUE) { "版本号已达到允许的数值上限。" }
        return SemVer(
            if (majorInc) major + 1 else major,
            if (majorInc) 0 else if (minorInc) minor + 1 else minor,
            if (majorInc || minorInc) 0 else patch + 1,
            if (agentVersion) "l" else null
        )
    }
}

/**
 * 解析 X.Y.Z 及其可选发布后缀，兼容既有普通版本与 main/l 标签。
 *
 * @param [v]           版本号
 * @return [SemVer]     解析后的语义化版本对象
 * @throws IllegalArgumentException 如果格式不正确
 */
fun parseSemVer(v: String): SemVer {
    val match = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?")
        .matchEntire(v.trim())
    requireNotNull(match) { "版本格式必须为 X.Y.Z 或 X.Y.Z-后缀。" }
    val numbers = (1..3).map { match.groupValues[it].toIntOrNull() }
    require(numbers.all { it != null }) { "版本号超出允许的数值范围。" }
    return SemVer(
        requireNotNull(numbers[0]), requireNotNull(numbers[1]), requireNotNull(numbers[2]),
        match.groupValues[4].ifEmpty { null }
    )
}

/**
 * 读取和保存权威版本文件，同时更新当前 Gradle 项目的版本属性。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @constructor 创建[VersionManager]
 * @param project 所属 Gradle 项目。
 * @param versionFile 权威版本文件。
 */
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

    /**
     * 按唯一升级维度保存普通版本或 Agent 版本。
     *
     * @param majorInc 是否升级主版本。
     * @param minorInc 是否升级次版本。
     * @param patchInc 是否升级修订号。
     * @param agentVersion 是否添加 l 后缀。
     */
    fun updateVersion(
        majorInc: Boolean = false,
        minorInc: Boolean = false,
        patchInc: Boolean = false,
        agentVersion: Boolean = false
    ) {
        save(currentVersion.next(majorInc, minorInc, patchInc, agentVersion))
    }

    /**
     * 保存已校验的版本，保留其他配置和注释，不产生无意义的时间戳变更。
     *
     * @param semver 待保存的完整版本。
     * @throws IllegalArgumentException 文件含重复或无法安全替换的版本定义时抛出。
     */
    fun save(semver: SemVer) {
        parseSemVer(semver.toString())
        val old = if (versionFile.exists()) versionFile.readText() else ""
        val key = Regex("(?m)^[\\t ]*version[\\t ]*[=:][^\\r\\n]*")
        val count = key.findAll(old).count()
        require(count <= 1) { "版本文件包含重复的 version 定义。" }
        val properties = Properties().apply { old.reader().use { load(it) } }
        require(count == 1 || !properties.containsKey("version")) { "版本定义必须使用单行 version=值 格式。" }
        val updated = if (count == 1) key.replace(old, "version=$semver")
        else old + (if (old.isNotEmpty() && !old.endsWith('\n')) "\n" else "") + "version=$semver\n"
        versionFile.parentFile?.mkdirs()
        versionFile.writeText(updated)
        cachedVersion = semver
        project.version = semver.toString()
        project.logger.lifecycle("版本已更新为：$semver")
    }
}

/**
 * 普通及 Agent 版本升级共用的任务，默认保持旧入口的普通版本行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @constructor 创建[BumpVersionTask]
 */
abstract class BumpVersionTask : DefaultTask() {
    private val versionProject = project

    @get:Input
    var majorInc: Boolean = false

    @get:Input
    var minorInc: Boolean = false

    @get:Input
    var patchInc: Boolean = false

    @get:Input
    var agentVersion: Boolean = false

    /** 校验唯一升级维度并保存当前入口对应的版本。 */
    @TaskAction
    fun bump() {
        val manager = VersionManager(versionProject)
        manager.updateVersion(majorInc, minorInc, patchInc, agentVersion)
    }
}
