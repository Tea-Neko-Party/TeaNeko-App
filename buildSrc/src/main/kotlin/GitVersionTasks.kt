import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 以参数列表执行本地 Git，并在失败时终止发布任务，不通过命令解释器拼接文本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @param directory 必须与 Git 工作树根目录一致的项目目录。
 */
class VersionGitRepository(private val directory: File) {
    /**
     * 执行命令并合并读取输出，避免先等待进程造成管道阻塞。
     *
     * @param arguments 原样传递的 Git 参数。
     * @param accepted 允许的退出码，查询是否有差异时可包含 1。
     * @return 退出码与去除末尾空白的输出。
     * @throws GradleException Git 无法启动或返回非预期退出码时抛出。
     */
    fun run(vararg arguments: String, accepted: Set<Int> = setOf(0)): Pair<Int, String> {
        val process = try {
            ProcessBuilder(listOf("git") + arguments).directory(directory).redirectErrorStream(true).start()
        } catch (failure: Exception) {
            throw GradleException("无法启动 Git，请检查本地 Git 环境。", failure)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trimEnd()
        val code = process.waitFor()
        if (code !in accepted) throw GradleException("Git 操作失败（退出码 $code）：\n$output")
        return code to output
    }

    /** 校验工作树根目录及已有 HEAD，避免在父仓库或空仓库错误提交。 */
    fun requireRepository() {
        val root = File(run("rev-parse", "--show-toplevel").second).canonicalFile
        require(root == directory.canonicalFile) { "版本任务必须在 Git 工作树根项目中执行。" }
        run("rev-parse", "--verify", "HEAD")
    }

    /**
     * 查询精确标签是否存在。
     *
     * @param tag 完整标签名。
     * @return 是否存在同名引用。
     */
    fun hasTag(tag: String): Boolean =
        run("show-ref", "--verify", "--quiet", "refs/tags/$tag", accepted = setOf(0, 1)).first == 0

    /**
     * 查找可达的上一标签，排除当前版本，避免先打当前标签后生成空日志。
     *
     * @param version 当前完整版本。
     * @return 上一标签，无标签时为空字符串。
     */
    fun previousTag(version: String): String {
        requireRepository()
        return run("describe", "--tags", "--abbrev=0", "--exclude=v$version", accepted = setOf(0, 128))
            .let { if (it.first == 0) it.second else "" }
    }

    /**
     * 为已提交的版本创建注解标签；同名标签只允许幂等指向当前提交。
     *
     * @param version 已保存在 HEAD 中的完整版本。
     * @return 本次是否创建了标签。
     * @throws IllegalArgumentException 版本尚未提交或已有标签指向其他提交时抛出。
     * @throws GradleException Git 校验或标签创建失败时抛出。
     */
    fun createTag(version: SemVer): Boolean {
        requireRepository()
        require(run("diff", "HEAD", "--quiet", "--", "version.properties", accepted = setOf(0, 1)).first == 0) {
            "version.properties 尚未提交，请先提交版本变更再创建标签。"
        }
        val committed = java.util.Properties().apply {
            run("show", "HEAD:version.properties").second.reader().use { load(it) }
        }
        require(committed.getProperty("version") == version.toString()) { "HEAD 中的版本与待打标签版本不一致。" }
        val tag = "v$version"
        run("check-ref-format", "refs/tags/$tag")
        if (hasTag(tag)) {
            require(run("rev-parse", "refs/tags/$tag^{}").second == run("rev-parse", "HEAD").second) {
                "标签 $tag 已指向其他提交，禁止覆盖。"
            }
            return false
        }
        run("tag", "-a", tag, "-m", "发布版本 $tag")
        return true
    }
}

/**
 * 普通及 Agent 标签任务，读取执行时的权威版本而非配置阶段的旧快照。
 *
 * @author zExNocs
 * @date 2026/11/01
 */
abstract class CreateVersionGitTagTask : DefaultTask() {
    private val versionProject = project
    @get:Input
    var agentOnly: Boolean = false

    /**
     * 校验已提交版本，并创建或确认当前提交的注解标签。
     *
     * @throws IllegalArgumentException Agent 后缀或已提交版本不满足要求时抛出。
     * @throws GradleException Git 校验或标签创建失败时抛出。
     */
    @TaskAction
    fun createTag() {
        val version = VersionManager(versionProject).currentVersion
        require(!agentOnly || version.suffix == "l") { "Agent 标签要求当前版本以 -l 结尾，请先使用 Agent 升级入口。" }
        val created = VersionGitRepository(versionProject.rootDir).createTag(version)
        logger.lifecycle(if (created) "已创建标签 v$version。" else "标签 v$version 已指向当前提交，无需重复创建。")
    }
}

/**
 * Agent 本地提交入口及其只读预览，固定只自动暂存版本文件。
 *
 * 已暂存改动由调用者明确选择；Git 提交与标签不是原子事务，失败时保留实际状态供核对。
 *
 * @author zExNocs
 * @date 2026/11/01
 */
abstract class AgentGitUpdateTask : DefaultTask() {
    private val versionProject = project
    @get:Input
    var bump: String = "none"

    @get:Input
    var commitMessage: String = ""

    @get:Input
    var preview: Boolean = false

    /**
     * 预览或执行版本保存、已暂存内容提交及精确标签创建，不执行 push。
     *
     * 所有可提前完成的校验都在写文件前执行；不自动纳入其他未暂存文件。
     *
     * @throws IllegalArgumentException 参数、仓库状态或目标标签不满足提交条件时抛出。
     * @throws GradleException Git 执行失败或更新仅部分完成时抛出。
     */
    @TaskAction
    fun update() {
        require(bump in setOf("none", "major", "minor", "patch")) { "agentBump 只允许 none、major、minor 或 patch。" }
        val manager = VersionManager(versionProject)
        val old = manager.currentVersion
        val next = if (bump == "none") old.copy(suffix = "l")
        else old.next(bump == "major", bump == "minor", bump == "patch", agentVersion = true)
        val git = VersionGitRepository(versionProject.rootDir)
        git.requireRepository()
        val staged = git.run("diff", "--cached", "--name-only").second
        if (preview) {
            logger.lifecycle("Agent 更新预览：$old → $next；目标标签 v$next；仅自动暂存 version.properties。")
            logger.lifecycle("当前已暂存文件：\n${staged.ifEmpty { "（无）" }}")
            logger.lifecycle("此入口不会提交、打标签或推送；执行前请核对暂存区。")
            return
        }
        require(commitMessage.isNotBlank()) { "请用 -PagentCommitMessage=提交说明 明确填写本次提交内容。" }
        require(git.run("symbolic-ref", "--quiet", "HEAD", accepted = setOf(0, 1)).first == 0) {
            "当前处于分离 HEAD，不能执行 Agent 更新提交。"
        }
        require(git.run("ls-files", "--unmerged").second.isEmpty()) { "存在未解决的 Git 合并冲突。" }
        require(!git.hasTag("v$next")) { "目标标签 v$next 已存在，请选择下一版本，不重复发布。" }
        git.run("check-ref-format", "refs/tags/v$next")
        git.run("var", "GIT_AUTHOR_IDENT")
        git.run("var", "GIT_COMMITTER_IDENT")
        require(staged.isNotEmpty() || next != old) { "版本未变化且暂存区为空，没有可提交的更新。" }
        try {
            manager.save(next)
            git.run("add", "--", "version.properties")
            git.run("commit", "-m", commitMessage)
            git.createTag(next)
        } catch (failure: Exception) {
            throw GradleException("Agent 更新未全部完成；请核对版本文件、暂存区、HEAD 和标签。已完成操作保留，不自动回退或重复提交。", failure)
        }
        logger.lifecycle("Agent 更新已提交并创建标签 v$next；未执行远程推送。")
    }
}
