import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 复制当前 config 中文件到 resources/templates/config 文件夹中
 *
 * @author zExNocs
 * @date 2026/03/13
 * @constructor 创建[CopyConfigToTemplates]
 */
abstract class CopyConfigToTemplates : DefaultTask() {

    init {
        group = "scripts"
        description = "如果模板不存在，将配置文件复制到 resources/templates/config。"
    }

    @TaskAction
    fun copy() {
        val projectDir: Path = project.projectDir.toPath()
        val configDir: Path = projectDir.resolve("config")
        val templateDir: Path = projectDir.resolve("src/main/resources/templates/config")

        if (!Files.exists(configDir)) {
            println("No config directory found at $configDir")
            return
        }

        Files.walk(configDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val relative = configDir.relativize(file)
                val target = templateDir.resolve(relative)
                // 如果不存在，则复制
                if (!Files.exists(target)) {
                    Files.createDirectories(target.parent)
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
                    println("Copied $file -> $target")
                } else {
                    println("Template already exists, skip: $target")
                }
            }
        }
    }
}