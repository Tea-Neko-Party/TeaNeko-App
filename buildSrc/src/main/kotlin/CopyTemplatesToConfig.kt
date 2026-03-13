import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 复制
 *
 * @author zExNocs
 * @date 2026/03/13
 * @constructor 创建[CopyTemplatesToConfig]
 */
abstract class CopyTemplatesToConfig : DefaultTask() {

    init {
        group = "scripts"
        description = "如果配置文件不存在，将模板从 `resources/templates/config` 复制到项目配置目录。"
    }

    @TaskAction
    fun copy() {
        val projectDir: Path = project.projectDir.toPath()
        val templateDir: Path = projectDir.resolve("src/main/resources/templates/config")
        val configDir: Path = projectDir.resolve("config")

        if (!Files.exists(templateDir)) {
            println("No templates found at $templateDir")
            return
        }

        Files.walk(templateDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { templateFile ->
                val relative = templateDir.relativize(templateFile)
                val target = configDir.resolve(relative)
                if (!Files.exists(target)) {
                    Files.createDirectories(target.parent)
                    Files.copy(templateFile, target, StandardCopyOption.REPLACE_EXISTING)
                    println("Copied template $templateFile -> config $target")
                } else {
                    println("Config file already exists, skip: $target")
                }
            }
        }
    }
}