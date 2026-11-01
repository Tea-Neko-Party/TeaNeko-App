import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 使用隔离的真实 Git 仓库验证版本通道、提交范围和失败保留，不修改项目仓库。
 *
 * @author zExNocs
 * @date 2026/11/01
 */
class VersionWorkflowTest {
    @TempDir
    lateinit var directory: File

    /** 校验普通/Agent 升级的数值及后缀互不继承，并保留版本文件其他内容。 */
    @Test
    fun versionChannelsRemainIndependent() {
        val current = parseSemVer("4.4.1-l")
        assertEquals("4.5.0", current.next(false, true, false).toString())
        assertEquals("4.5.0-l", current.next(false, true, false, true).toString())
        assertEquals("5.0.0-l", current.next(true, false, false, true).toString())
        assertEquals("4.4.2-l", current.next(false, false, true, true).toString())
        assertEquals("4.4.0-main", parseSemVer("4.4.0-main").toString())
        assertThrows(IllegalArgumentException::class.java) { parseSemVer("-1.2.3") }
        assertThrows(IllegalArgumentException::class.java) { current.next(true, true, false) }
        assertThrows(IllegalArgumentException::class.java) { parseSemVer("2147483647.0.0").next(true, false, false) }
        val file = File(directory, "version.properties").apply { writeText("# 保留说明\nother=keep\nversion=4.4.1\n") }
        val manager = VersionManager(ProjectBuilder.builder().withProjectDir(directory).build())
        manager.updateVersion(minorInc = true, agentVersion = true)
        assertEquals("# 保留说明\nother=keep\nversion=4.5.0-l\n", file.readText())
        assertEquals("4.5.0-l", manager.currentVersion.toString())
    }

    /** 完整提交只纳入已暂存文件及版本，并创建精确的注解标签。 */
    @Test
    fun updateCommitsOnlyStagedChangesAndVersion() {
        val git = repository()
        git.run("tag", "v4.4.1")
        File(directory, "selected.txt").writeText("已暂存更新")
        git.run("add", "selected.txt")
        File(directory, "unrelated.txt").writeText("用户未暂存内容")
        val task = task().apply { bump = "minor"; commitMessage = "feat: Agent 更新\n\n保留多行说明" }
        task.update()
        assertEquals("version=4.5.0-l", git.run("show", "HEAD:version.properties").second)
        assertEquals("已暂存更新", git.run("show", "HEAD:selected.txt").second)
        assertEquals("?? unrelated.txt", git.run("status", "--porcelain", "--", "unrelated.txt").second)
        assertEquals(git.run("rev-parse", "HEAD").second, git.run("rev-parse", "v4.5.0-l^{}").second)
        assertEquals("tag", git.run("cat-file", "-t", "v4.5.0-l").second)
        assertEquals("v4.4.1", git.previousTag("4.5.0-l"))
        assertFalse(git.createTag(parseSemVer("4.5.0-l")))
    }

    /** 只读预览无副作用，标签冲突在任何版本或暂存区变动前被拒绝。 */
    @Test
    fun previewAndTagConflictDoNotWrite() {
        val git = repository()
        git.run("tag", "v4.5.0-l")
        val before = git.run("rev-parse", "HEAD").second
        val task = task().apply { bump = "minor"; preview = true }
        task.update()
        task.preview = false
        task.commitMessage = "feat: 不应提交"
        assertThrows(IllegalArgumentException::class.java) { task.update() }
        assertEquals("version=4.4.1\n", File(directory, "version.properties").readText())
        assertEquals(before, git.run("rev-parse", "HEAD").second)
        assertEquals("", git.run("status", "--porcelain").second)
    }

    /** Git 钩子失败时报告失败并保留实际暂存状态，不打标签或伪造成功。 */
    @Test
    fun failedCommitPreservesReviewableState() {
        val git = repository()
        val hooks = File(directory, ".git/hooks").apply { mkdirs() }
        File(hooks, "pre-commit").apply { writeText("#!/bin/sh\nexit 1\n"); setExecutable(true) }
        val before = git.run("rev-parse", "HEAD").second
        val task = task().apply { bump = "patch"; commitMessage = "fix: 钩子拒绝" }
        assertThrows(GradleException::class.java) { task.update() }
        assertEquals(before, git.run("rev-parse", "HEAD").second)
        assertFalse(git.hasTag("v4.4.2-l"))
        assertEquals("version=4.4.2-l", git.run("show", ":version.properties").second)
    }

    /**
     * 初始化仅供本用例使用的仓库和身份，不读取开发者全局提交身份。
     *
     * @return 已有基础提交的仓库。
     */
    private fun repository(): VersionGitRepository {
        val git = VersionGitRepository(directory)
        git.run("init")
        git.run("config", "user.name", "版本流程测试")
        git.run("config", "user.email", "version-test@example.invalid")
        git.run("config", "commit.gpgsign", "false")
        git.run("config", "tag.gpgsign", "false")
        git.run("config", "core.autocrlf", "false")
        git.run("config", "core.hooksPath", ".git/hooks")
        File(directory, ".git/info/exclude").appendText("\n.gradle/\nbuild/\nuserHome/\n")
        File(directory, "version.properties").writeText("version=4.4.1\n")
        File(directory, "selected.txt").writeText("初始内容")
        git.run("add", "version.properties", "selected.txt")
        git.run("commit", "-m", "初始版本")
        return git
    }

    /**
     * 构造真实任务实例，不启动外部 Gradle 构建或触碰当前项目仓库。
     *
     * @return 隔离项目中的更新任务。
     */
    private fun task(): AgentGitUpdateTask = ProjectBuilder.builder().withProjectDir(directory).build()
        .tasks.register("agentGitUpdate", AgentGitUpdateTask::class.java).get()
}
